CREATE TABLE IF NOT EXISTS game_semantic_word (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    word         VARCHAR(64)     NOT NULL DEFAULT '',
    queue_order  INT             NOT NULL DEFAULT 0,
    status       VARCHAR(20)     NOT NULL DEFAULT 'pending' COMMENT 'pending/active/used/skipped',
    is_deleted   TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_gsw_queue (is_deleted, status, queue_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS game_semantic_round (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    target_word      VARCHAR(64)     NOT NULL DEFAULT '',
    word_id          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    status           VARCHAR(20)     NOT NULL DEFAULT 'finished' COMMENT 'active/finished/waiting_words',
    winner_username  VARCHAR(32)     NOT NULL DEFAULT '',
    winner_fp        VARCHAR(64)     NOT NULL DEFAULT '',
    started_at       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    finished_at      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    is_deleted       TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_gsr_status (is_deleted, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS game_semantic_guess (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    round_id         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    word             VARCHAR(64)     NOT NULL DEFAULT '',
    score            DOUBLE          NOT NULL DEFAULT 0,
    first_username   VARCHAR(32)     NOT NULL DEFAULT '',
    first_fp         VARCHAR(64)     NOT NULL DEFAULT '',
    is_deleted       TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gsg_round_word (round_id, word),
    KEY idx_gsg_round_score (round_id, is_deleted, score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
