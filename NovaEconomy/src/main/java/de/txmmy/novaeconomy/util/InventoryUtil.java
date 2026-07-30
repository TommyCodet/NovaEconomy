package de.txmmy.novaeconomy.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Map;

public final class InventoryUtil {
    private InventoryUtil() {
    }

    public static int count(PlayerInventory inventory, Material material) {
        int total = 0;
        for (ItemStack stack : inventory.getStorageContents()) {
            if (stack != null && stack.getType() == material) {
                total = Math.addExact(total, stack.getAmount());
            }
        }
        return total;
    }

    public static int remove(PlayerInventory inventory, Material material, int requested) {
        int remaining = requested;
        ItemStack[] contents = inventory.getStorageContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack stack = contents[slot];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int taken = Math.min(remaining, stack.getAmount());
            remaining -= taken;
            if (taken == stack.getAmount()) {
                contents[slot] = null;
            } else {
                stack.setAmount(stack.getAmount() - taken);
                contents[slot] = stack;
            }
        }
        inventory.setStorageContents(contents);
        return requested - remaining;
    }

    public static void giveOrDrop(Player player, Material material, int amount) {
        int remaining = amount;
        int maxStack = material.getMaxStackSize();
        while (remaining > 0) {
            int stackAmount = Math.min(maxStack, remaining);
            remaining -= stackAmount;
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(new ItemStack(material, stackAmount));
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }
}
