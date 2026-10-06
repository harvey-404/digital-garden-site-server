ALTER TABLE ledger_expense ADD COLUMN record_version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE ledger_chat_session (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    active_draft_id BIGINT UNSIGNED NULL,
    processing_request_id VARCHAR(64) NULL,
    processing_attempt VARCHAR(36) NULL,
    processing_until BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_chat_session_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ledger_chat_message (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    role VARCHAR(16) NOT NULL,
    request_id VARCHAR(64) NULL,
    text TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    draft_id BIGINT UNSIGNED NULL,
    attachment_ids_json TEXT NOT NULL,
    response_json MEDIUMTEXT NULL,
    payload_hash CHAR(64) NULL,
    received_at BIGINT NOT NULL,
    UNIQUE KEY uq_chat_message_request (user_id, request_id, role),
    KEY idx_chat_message_history (session_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ledger_chat_attachment (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    hash CHAR(64) NOT NULL,
    storage_key VARCHAR(128) NOT NULL,
    content_type VARCHAR(32) NOT NULL,
    width INT NOT NULL,
    height INT NOT NULL,
    in_dtm BIGINT NOT NULL,
    KEY idx_chat_attachment_hash (user_id, hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ledger_chat_draft (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    session_id BIGINT UNSIGNED NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    status VARCHAR(16) NOT NULL,
    KEY idx_chat_draft_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ledger_chat_draft_item (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    draft_id BIGINT UNSIGNED NOT NULL,
    ordinal INT NOT NULL,
    data_json MEDIUMTEXT NOT NULL,
    is_removed TINYINT NOT NULL DEFAULT 0,
    KEY idx_chat_item_draft (draft_id, ordinal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ledger_chat_expense_link (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    draft_id BIGINT UNSIGNED NOT NULL,
    draft_item_id BIGINT UNSIGNED NOT NULL,
    expense_id BIGINT UNSIGNED NOT NULL,
    expense_version BIGINT NOT NULL,
    snapshot_json TEXT NOT NULL,
    source_attachment_ids_json TEXT NOT NULL,
    UNIQUE KEY uq_chat_link_item (draft_item_id),
    UNIQUE KEY uq_chat_link_expense (expense_id),
    KEY idx_chat_link_draft (user_id, draft_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
