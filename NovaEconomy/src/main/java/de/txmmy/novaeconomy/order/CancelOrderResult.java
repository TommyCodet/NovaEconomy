package de.txmmy.novaeconomy.order;

public record CancelOrderResult(Status status, long refundMinor) {
    public enum Status {
        SUCCESS,
        NOT_FOUND,
        NOT_OWNER,
        NOT_ACTIVE
    }
}
