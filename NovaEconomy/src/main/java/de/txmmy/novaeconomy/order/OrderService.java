package de.txmmy.novaeconomy.order;

import de.txmmy.novaeconomy.database.Database;
import de.txmmy.novaeconomy.economy.EconomyService;
import de.txmmy.novaeconomy.util.Money;
import org.bukkit.Material;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class OrderService {
    private final Database database;
    private final EconomyService economyService;
    private final int maxActiveOrdersPerPlayer;

    public OrderService(Database database, EconomyService economyService, int maxActiveOrdersPerPlayer) {
        this.database = database;
        this.economyService = economyService;
        this.maxActiveOrdersPerPlayer = maxActiveOrdersPerPlayer;
    }

    public CreateOrderResult createOrder(UUID ownerUuid, String ownerName, Material material,
                                         int amount, long unitPriceMinor) {
        long totalMinor = Money.multiplyExact(unitPriceMinor, amount);
        return database.transaction(connection -> {
            economyService.ensureAccount(connection, ownerUuid, ownerName);
            int activeCount = countActiveOrders(connection, ownerUuid);
            if (activeCount >= maxActiveOrdersPerPlayer) {
                return new CreateOrderResult(CreateOrderResult.Status.ACTIVE_LIMIT_REACHED,
                        0, totalMinor, economyService.getBalance(connection, ownerUuid));
            }

            long balance = economyService.getBalance(connection, ownerUuid);
            if (balance < totalMinor) {
                return new CreateOrderResult(CreateOrderResult.Status.INSUFFICIENT_FUNDS,
                        0, totalMinor, balance);
            }

            long balanceAfter = balance - totalMinor;
            economyService.updateBalance(connection, ownerUuid, balanceAfter, ownerName);
            long orderId;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO orders(
                        owner_uuid, owner_name, material, requested_amount,
                        unit_price_minor, reserved_remaining_minor, status
                    ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setString(1, ownerUuid.toString());
                statement.setString(2, ownerName);
                statement.setString(3, material.name());
                statement.setInt(4, amount);
                statement.setLong(5, unitPriceMinor);
                statement.setLong(6, totalMinor);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Keine Auftrags-ID erzeugt");
                    }
                    orderId = keys.getLong(1);
                }
            }
            economyService.insertTransaction(connection, ownerUuid, "ORDER_RESERVE", -totalMinor,
                    balanceAfter, "ORDER", Long.toString(orderId));
            return new CreateOrderResult(CreateOrderResult.Status.SUCCESS, orderId, totalMinor, balanceAfter);
        });
    }

    public List<OrderRecord> findActiveOrders(Material materialFilter) {
        return database.read(connection -> {
            String sql = """
                    SELECT id, owner_uuid, owner_name, material, requested_amount,
                           delivered_amount, collected_amount, unit_price_minor,
                           reserved_remaining_minor, status
                    FROM orders
                    WHERE status = 'ACTIVE'
                    """ + (materialFilter == null ? "" : " AND material = ?") + " ORDER BY created_at ASC, id ASC";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                if (materialFilter != null) {
                    statement.setString(1, materialFilter.name());
                }
                try (ResultSet resultSet = statement.executeQuery()) {
                    List<OrderRecord> orders = new ArrayList<>();
                    while (resultSet.next()) {
                        orders.add(mapOrder(resultSet));
                    }
                    return orders;
                }
            }
        });
    }

    public FulfillmentResult fulfill(long orderId, UUID supplierUuid, String supplierName, int offeredAmount) {
        if (offeredAmount <= 0) {
            return new FulfillmentResult(FulfillmentResult.Status.NOTHING_NEEDED,
                    orderId, Material.AIR, 0, 0, false);
        }
        return database.transaction(connection -> {
            economyService.ensureAccount(connection, supplierUuid, supplierName);
            OrderRecord order = findOrderForUpdate(connection, orderId);
            if (order == null) {
                return new FulfillmentResult(FulfillmentResult.Status.NOT_FOUND,
                        orderId, Material.AIR, 0, 0, false);
            }
            if (order.ownerUuid().equals(supplierUuid)) {
                return new FulfillmentResult(FulfillmentResult.Status.OWN_ORDER,
                        orderId, order.material(), 0, 0, false);
            }
            if (order.status() != OrderStatus.ACTIVE) {
                return new FulfillmentResult(FulfillmentResult.Status.NOT_ACTIVE,
                        orderId, order.material(), 0, 0, false);
            }

            int accepted = Math.min(offeredAmount, order.remainingAmount());
            if (accepted <= 0) {
                return new FulfillmentResult(FulfillmentResult.Status.NOTHING_NEEDED,
                        orderId, order.material(), 0, 0, false);
            }
            long payout = Money.multiplyExact(order.unitPriceMinor(), accepted);
            if (payout > order.reservedRemainingMinor()) {
                throw new SQLException("Reserviertes Guthaben ist inkonsistent bei Auftrag " + orderId);
            }

            int deliveredAfter = order.deliveredAmount() + accepted;
            long reservedAfter = order.reservedRemainingMinor() - payout;
            boolean completed = deliveredAfter >= order.requestedAmount();
            String statusAfter = completed ? "COMPLETED" : "ACTIVE";
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE orders
                    SET delivered_amount = ?, reserved_remaining_minor = ?, status = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'ACTIVE'
                    """)) {
                statement.setInt(1, deliveredAfter);
                statement.setLong(2, reservedAfter);
                statement.setString(3, statusAfter);
                statement.setLong(4, orderId);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Auftrag wurde parallel verändert: " + orderId);
                }
            }

            long supplierBalance = economyService.getBalance(connection, supplierUuid);
            long supplierAfter = Math.addExact(supplierBalance, payout);
            economyService.updateBalance(connection, supplierUuid, supplierAfter, supplierName);

            long deliveryId;
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO deliveries(order_id, supplier_uuid, supplier_name, amount, payout_minor)
                    VALUES (?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, orderId);
                statement.setString(2, supplierUuid.toString());
                statement.setString(3, supplierName);
                statement.setInt(4, accepted);
                statement.setLong(5, payout);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) {
                        throw new SQLException("Keine Lieferungs-ID erzeugt");
                    }
                    deliveryId = keys.getLong(1);
                }
            }
            economyService.insertTransaction(connection, supplierUuid, "ORDER_DELIVERY_PAYOUT", payout,
                    supplierAfter, "DELIVERY", Long.toString(deliveryId));
            return new FulfillmentResult(FulfillmentResult.Status.SUCCESS,
                    orderId, order.material(), accepted, payout, completed);
        });
    }

    public CancelOrderResult cancel(long orderId, UUID ownerUuid, String ownerName) {
        return database.transaction(connection -> {
            economyService.ensureAccount(connection, ownerUuid, ownerName);
            OrderRecord order = findOrderForUpdate(connection, orderId);
            if (order == null) {
                return new CancelOrderResult(CancelOrderResult.Status.NOT_FOUND, 0);
            }
            if (!order.ownerUuid().equals(ownerUuid)) {
                return new CancelOrderResult(CancelOrderResult.Status.NOT_OWNER, 0);
            }
            if (order.status() != OrderStatus.ACTIVE) {
                return new CancelOrderResult(CancelOrderResult.Status.NOT_ACTIVE, 0);
            }

            long refund = order.reservedRemainingMinor();
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE orders
                    SET status = 'CANCELLED', reserved_remaining_minor = 0,
                        updated_at = CURRENT_TIMESTAMP, cancelled_at = CURRENT_TIMESTAMP
                    WHERE id = ? AND status = 'ACTIVE'
                    """)) {
                statement.setLong(1, orderId);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Auftrag wurde parallel verändert: " + orderId);
                }
            }

            if (refund > 0) {
                long balance = economyService.getBalance(connection, ownerUuid);
                long balanceAfter = Math.addExact(balance, refund);
                economyService.updateBalance(connection, ownerUuid, balanceAfter, ownerName);
                economyService.insertTransaction(connection, ownerUuid, "ORDER_REFUND", refund,
                        balanceAfter, "ORDER", Long.toString(orderId));
            }
            return new CancelOrderResult(CancelOrderResult.Status.SUCCESS, refund);
        });
    }

    public CollectResult collect(UUID ownerUuid) {
        return database.transaction(connection -> {
            Map<Material, Integer> totals = new LinkedHashMap<>();
            List<Long> orderIds = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT id, material, delivered_amount, collected_amount
                    FROM orders
                    WHERE owner_uuid = ? AND delivered_amount > collected_amount
                    ORDER BY id ASC
                    """)) {
                statement.setString(1, ownerUuid.toString());
                try (ResultSet resultSet = statement.executeQuery()) {
                    while (resultSet.next()) {
                        long id = resultSet.getLong("id");
                        Material material = Material.valueOf(resultSet.getString("material"));
                        int collectible = resultSet.getInt("delivered_amount") - resultSet.getInt("collected_amount");
                        totals.merge(material, collectible, Math::addExact);
                        orderIds.add(id);
                    }
                }
            }

            if (orderIds.isEmpty()) {
                return new CollectResult(List.of(), 0);
            }
            try (PreparedStatement statement = connection.prepareStatement("""
                    UPDATE orders
                    SET collected_amount = delivered_amount, updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """)) {
                for (long orderId : orderIds) {
                    statement.setLong(1, orderId);
                    statement.addBatch();
                }
                statement.executeBatch();
            }

            List<CollectedItems> items = totals.entrySet().stream()
                    .map(entry -> new CollectedItems(entry.getKey(), entry.getValue()))
                    .toList();
            return new CollectResult(items, orderIds.size());
        });
    }

    private int countActiveOrders(Connection connection, UUID ownerUuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM orders WHERE owner_uuid = ? AND status = 'ACTIVE'")) {
            statement.setString(1, ownerUuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private OrderRecord findOrderForUpdate(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id, owner_uuid, owner_name, material, requested_amount,
                       delivered_amount, collected_amount, unit_price_minor,
                       reserved_remaining_minor, status
                FROM orders WHERE id = ?
                """)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapOrder(resultSet) : null;
            }
        }
    }

    private OrderRecord mapOrder(ResultSet resultSet) throws SQLException {
        return new OrderRecord(
                resultSet.getLong("id"),
                UUID.fromString(resultSet.getString("owner_uuid")),
                resultSet.getString("owner_name"),
                Material.valueOf(resultSet.getString("material")),
                resultSet.getInt("requested_amount"),
                resultSet.getInt("delivered_amount"),
                resultSet.getInt("collected_amount"),
                resultSet.getLong("unit_price_minor"),
                resultSet.getLong("reserved_remaining_minor"),
                OrderStatus.valueOf(resultSet.getString("status"))
        );
    }
}
