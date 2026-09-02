CREATE TABLE IF NOT EXISTS ledger_user (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_sn               VARCHAR(64)     NOT NULL DEFAULT '',
    invite_code           VARCHAR(14)     NOT NULL DEFAULT '',
    display_name          VARCHAR(64)     NOT NULL DEFAULT '',
    default_budget_amount DECIMAL(12,2)   NOT NULL DEFAULT 0,
    status                VARCHAR(16)     NOT NULL DEFAULT 'active' COMMENT 'active/disabled',
    is_deleted            TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm                BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm            BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ledger_user_sn (user_sn),
    UNIQUE KEY uq_ledger_user_invite (invite_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ledger_category (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    name         VARCHAR(64)     NOT NULL DEFAULT '',
    icon         VARCHAR(32)     NOT NULL DEFAULT '',
    sort_order   INT             NOT NULL DEFAULT 0,
    is_deleted   TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ledger_category_user (user_id, is_deleted, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ledger_budget (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    budget_dtm   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    amount       DECIMAL(12,2)   NOT NULL DEFAULT 0,
    is_deleted   TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_ledger_budget_user_month (user_id, budget_dtm, is_deleted),
    KEY idx_ledger_budget_user (user_id, is_deleted, budget_dtm)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ledger_expense (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    budget_id    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    category_id  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    description  VARCHAR(500)    NOT NULL DEFAULT '',
    amount       DECIMAL(12,2)   NOT NULL DEFAULT 0,
    recorded_at  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    is_deleted   TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ledger_expense_user_budget (user_id, budget_id, is_deleted),
    KEY idx_ledger_expense_recorded (user_id, recorded_at, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
