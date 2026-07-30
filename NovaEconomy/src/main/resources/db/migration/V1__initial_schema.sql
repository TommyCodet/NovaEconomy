CREATE TABLE IF NOT EXISTS accounts (
    uuid TEXT PRIMARY KEY,
    last_name TEXT NOT NULL,
    balance_minor INTEGER NOT NULL CHECK (balance_minor >= 0),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    owner_uuid TEXT NOT NULL,
    owner_name TEXT NOT NULL,
    material TEXT NOT NULL,
    requested_amount INTEGER NOT NULL CHECK (requested_amount > 0),
    delivered_amount INTEGER NOT NULL DEFAULT 0 CHECK (delivered_amount >= 0),
    collected_amount INTEGER NOT NULL DEFAULT 0 CHECK (collected_amount >= 0),
    unit_price_minor INTEGER NOT NULL CHECK (unit_price_minor > 0),
    reserved_remaining_minor INTEGER NOT NULL CHECK (reserved_remaining_minor >= 0),
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at TEXT,
    FOREIGN KEY (owner_uuid) REFERENCES accounts(uuid),
    CHECK (delivered_amount <= requested_amount),
    CHECK (collected_amount <= delivered_amount)
);

CREATE TABLE IF NOT EXISTS deliveries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_id INTEGER NOT NULL,
    supplier_uuid TEXT NOT NULL,
    supplier_name TEXT NOT NULL,
    amount INTEGER NOT NULL CHECK (amount > 0),
    payout_minor INTEGER NOT NULL CHECK (payout_minor > 0),
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (order_id) REFERENCES orders(id),
    FOREIGN KEY (supplier_uuid) REFERENCES accounts(uuid)
);

CREATE TABLE IF NOT EXISTS account_transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_uuid TEXT NOT NULL,
    transaction_type TEXT NOT NULL,
    amount_minor INTEGER NOT NULL,
    balance_after_minor INTEGER NOT NULL CHECK (balance_after_minor >= 0),
    reference_type TEXT,
    reference_id TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (account_uuid) REFERENCES accounts(uuid)
);

CREATE INDEX IF NOT EXISTS idx_orders_status_material ON orders(status, material);
CREATE INDEX IF NOT EXISTS idx_orders_owner_status ON orders(owner_uuid, status);
CREATE INDEX IF NOT EXISTS idx_deliveries_order ON deliveries(order_id);
CREATE INDEX IF NOT EXISTS idx_transactions_account_created ON account_transactions(account_uuid, created_at);
