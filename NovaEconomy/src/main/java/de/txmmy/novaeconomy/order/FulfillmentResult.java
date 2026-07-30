package de.txmmy.novaeconomy.order;

import org.bukkit.Material;

public record FulfillmentResult(
        Status status,
        long orderId,
        Material material,
        int acceptedAmount,
        long payoutMinor,
        boolean completed
) {
    public enum Status {
        SUCCESS,
        NOT_FOUND,
        NOT_ACTIVE,
        OWN_ORDER,
        NOTHING_NEEDED
    }
}
