package de.txmmy.novaeconomy.order;

public record CreateOrderResult(Status status, long orderId, long totalMinor, long balanceMinor) {
    public enum Status {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        ACTIVE_LIMIT_REACHED
    }
}
