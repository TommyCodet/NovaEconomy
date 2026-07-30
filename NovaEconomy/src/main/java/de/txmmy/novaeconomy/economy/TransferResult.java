package de.txmmy.novaeconomy.economy;

public record TransferResult(Status status, long senderBalanceMinor, long receiverBalanceMinor) {
    public enum Status {
        SUCCESS,
        INSUFFICIENT_FUNDS,
        INVALID_AMOUNT,
        SAME_ACCOUNT
    }
}
