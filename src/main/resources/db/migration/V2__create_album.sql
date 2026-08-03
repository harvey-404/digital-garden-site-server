CREATE TABLE IF NOT EXISTS album_place (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name         VARCHAR(200)    NOT NULL DEFAULT '',
    address      VARCHAR(500)    NOT NULL DEFAULT '',
    city         VARCHAR(100)    NOT NULL DEFAULT '',
    lat          DOUBLE          NOT NULL DEFAULT 0,
    lng          DOUBLE          NOT NULL DEFAULT 0,
    note         VARCHAR(1000)   NOT NULL DEFAULT '',
    sort_order   INT             NOT NULL DEFAULT 0,
    status       VARCHAR(20)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED',
    is_deleted   TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_album_place_pub (status, is_deleted),
    KEY idx_album_place_sort (is_deleted, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS album_photo (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    place_id     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    file_url     VARCHAR(500)    NOT NULL DEFAULT '',
    caption      VARCHAR(500)    NOT NULL DEFAULT '',
    sort_order   INT             NOT NULL DEFAULT 0,
    is_deleted   TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_album_photo_place (place_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS album_video (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    place_id     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    url          VARCHAR(1000)   NOT NULL DEFAULT '',
    platform     VARCHAR(50)     NOT NULL DEFAULT 'other',
    title        VARCHAR(200)    NOT NULL DEFAULT '',
    sort_order   INT             NOT NULL DEFAULT 0,
    is_deleted   TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_album_video_place (place_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
