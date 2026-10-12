CREATE TABLE ledger_guest_session (
    token_hash CHAR(64) NOT NULL PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    completed INT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    expires_at BIGINT NOT NULL,
    migrated_to_account BIGINT NULL,
    UNIQUE KEY uq_ledger_guest_user (user_id),
    KEY idx_ledger_guest_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
