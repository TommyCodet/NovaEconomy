package de.txmmy.novaeconomy.order;

import org.bukkit.Material;

import java.util.UUID;

public record OrderRecord(
        long id,
        UUID ownerUuid,
        String ownerName,
        Material material,
        int requestedAmount,
        int deliveredAmount,
        int collectedAmount,
        long unitPriceMinor,
        long reservedRemainingMinor,
        OrderStatus status
) {
    public int remainingAmount() {
        return requestedAmount - deliveredAmount;
    }

    public int collectibleAmount() {
        return deliveredAmount - collectedAmount;
    }
}
