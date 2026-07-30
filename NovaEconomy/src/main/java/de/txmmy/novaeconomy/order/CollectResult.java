package de.txmmy.novaeconomy.order;

import java.util.List;

public record CollectResult(List<CollectedItems> items, int orderCount) {
    public int totalAmount() {
        return items.stream().mapToInt(CollectedItems::amount).sum();
    }
}
