CREATE TABLE IF NOT EXISTS todo (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title        VARCHAR(200)    NOT NULL DEFAULT '',
    slug         VARCHAR(200)    NOT NULL DEFAULT '',
    description  TEXT            NOT NULL,
    plan_md      LONGTEXT        NOT NULL,
    priority     VARCHAR(16)     NOT NULL DEFAULT 'MEDIUM' COMMENT 'HIGH/MEDIUM/LOW',
    progress     TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0-100',
    status       VARCHAR(16)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
    sort_order   INT             NOT NULL DEFAULT 0,
    is_deleted   TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_todo_slug (slug),
    KEY idx_todo_list (is_deleted, status, priority, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
