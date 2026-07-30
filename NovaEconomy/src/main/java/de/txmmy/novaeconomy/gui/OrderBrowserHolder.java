package de.txmmy.novaeconomy.gui;

import de.txmmy.novaeconomy.order.OrderRecord;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public final class OrderBrowserHolder implements InventoryHolder {
    private final Material filter;
    private final int page;
    private final int totalPages;
    private final Map<Integer, OrderRecord> ordersBySlot = new HashMap<>();
    private Inventory inventory;

    public OrderBrowserHolder(Material filter, int page, int totalPages) {
        this.filter = filter;
        this.page = page;
        this.totalPages = totalPages;
    }

    public Material filter() {
        return filter;
    }

    public int page() {
        return page;
    }

    public int totalPages() {
        return totalPages;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public void putOrder(int slot, OrderRecord order) {
        ordersBySlot.put(slot, order);
    }

    public OrderRecord orderAt(int slot) {
        return ordersBySlot.get(slot);
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (inventory == null) {
            throw new IllegalStateException("GUI-Inventar wurde noch nicht erstellt");
        }
        return inventory;
    }
}
