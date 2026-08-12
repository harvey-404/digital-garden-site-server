CREATE TABLE IF NOT EXISTS game_device_identity (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    fp            VARCHAR(64)     NOT NULL DEFAULT '' COMMENT 'browser device fingerprint',
    username      VARCHAR(16)     NOT NULL DEFAULT '' COMMENT 'last bound nickname',
    bound_at      BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'unix seconds of last nick bind',
    is_deleted    TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm        BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_gdi_fp (fp),
    KEY idx_gdi_fp_del (is_deleted, fp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
