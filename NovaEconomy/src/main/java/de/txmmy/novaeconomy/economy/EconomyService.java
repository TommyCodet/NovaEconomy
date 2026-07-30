package de.txmmy.novaeconomy.economy;

import de.txmmy.novaeconomy.database.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class EconomyService {
    private final Database database;
    private final long startingBalanceMinor;

    public EconomyService(Database database, long startingBalanceMinor) {
        this.database = database;
        this.startingBalanceMinor = startingBalanceMinor;
    }

    public void ensureAccount(UUID uuid, String lastName) {
        database.transaction(connection -> {
            ensureAccount(connection, uuid, lastName);
            return null;
        });
    }

    public long getBalance(UUID uuid, String lastName) {
        return database.transaction(connection -> {
            ensureAccount(connection, uuid, lastName);
            return getBalance(connection, uuid);
        });
    }

    public TransferResult transfer(UUID senderUuid, String senderName,
                                   UUID receiverUuid, String receiverName,
                                   long amountMinor) {
        if (amountMinor <= 0) {
            return new TransferResult(TransferResult.Status.INVALID_AMOUNT, 0, 0);
        }
        if (senderUuid.equals(receiverUuid)) {
            return new TransferResult(TransferResult.Status.SAME_ACCOUNT, 0, 0);
        }

        return database.transaction(connection -> {
            ensureAccount(connection, senderUuid, senderName);
            ensureAccount(connection, receiverUuid, receiverName);
            long senderBalance = getBalance(connection, senderUuid);
            if (senderBalance < amountMinor) {
                return new TransferResult(TransferResult.Status.INSUFFICIENT_FUNDS,
                        senderBalance, getBalance(connection, receiverUuid));
            }

            long senderAfter = senderBalance - amountMinor;
            long receiverAfter = Math.addExact(getBalance(connection, receiverUuid), amountMinor);
            updateBalance(connection, senderUuid, senderAfter, senderName);
            updateBalance(connection, receiverUuid, receiverAfter, receiverName);
            insertTransaction(connection, senderUuid, "PLAYER_PAYMENT_SENT", -amountMinor,
                    senderAfter, "PLAYER", receiverUuid.toString());
            insertTransaction(connection, receiverUuid, "PLAYER_PAYMENT_RECEIVED", amountMinor,
                    receiverAfter, "PLAYER", senderUuid.toString());
            return new TransferResult(TransferResult.Status.SUCCESS, senderAfter, receiverAfter);
        });
    }

    public long setBalance(UUID uuid, String name, long newBalanceMinor, String transactionType) {
        if (newBalanceMinor < 0) {
            throw new IllegalArgumentException("Kontostand darf nicht negativ sein");
        }
        return database.transaction(connection -> {
            ensureAccount(connection, uuid, name);
            long oldBalance = getBalance(connection, uuid);
            updateBalance(connection, uuid, newBalanceMinor, name);
            insertTransaction(connection, uuid, transactionType, newBalanceMinor - oldBalance,
                    newBalanceMinor, "ADMIN", null);
            return newBalanceMinor;
        });
    }

    public long addBalance(UUID uuid, String name, long deltaMinor, String transactionType) {
        return database.transaction(connection -> {
            ensureAccount(connection, uuid, name);
            long oldBalance = getBalance(connection, uuid);
            long newBalance = Math.addExact(oldBalance, deltaMinor);
            if (newBalance < 0) {
                throw new IllegalArgumentException("Kontostand würde negativ");
            }
            updateBalance(connection, uuid, newBalance, name);
            insertTransaction(connection, uuid, transactionType, deltaMinor,
                    newBalance, "ADMIN", null);
            return newBalance;
        });
    }

    public void ensureAccount(Connection connection, UUID uuid, String lastName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO accounts(uuid, last_name, balance_minor)
                VALUES (?, ?, ?)
                ON CONFLICT(uuid) DO UPDATE SET
                    last_name = excluded.last_name,
                    updated_at = CURRENT_TIMESTAMP
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, lastName);
            statement.setLong(3, startingBalanceMinor);
            statement.executeUpdate();
        }
    }

    public long getBalance(Connection connection, UUID uuid) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT balance_minor FROM accounts WHERE uuid = ?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new SQLException("Konto fehlt für UUID " + uuid);
                }
                return resultSet.getLong("balance_minor");
            }
        }
    }

    public void updateBalance(Connection connection, UUID uuid, long balanceMinor, String lastName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE accounts
                SET balance_minor = ?, last_name = ?, updated_at = CURRENT_TIMESTAMP
                WHERE uuid = ?
                """)) {
            statement.setLong(1, balanceMinor);
            statement.setString(2, lastName);
            statement.setString(3, uuid.toString());
            if (statement.executeUpdate() != 1) {
                throw new SQLException("Konto konnte nicht aktualisiert werden: " + uuid);
            }
        }
    }

    public void insertTransaction(Connection connection, UUID uuid, String type, long amountMinor,
                                  long balanceAfterMinor, String referenceType, String referenceId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO account_transactions(
                    account_uuid, transaction_type, amount_minor, balance_after_minor,
                    reference_type, reference_id
                ) VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, type);
            statement.setLong(3, amountMinor);
            statement.setLong(4, balanceAfterMinor);
            statement.setString(5, referenceType);
            statement.setString(6, referenceId);
            statement.executeUpdate();
        }
    }
}
