-- Fresh schema per docs/specs/2026-07-12-database-conventions.md
-- No FOREIGN KEY constraints

SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS post_tag;
DROP TABLE IF EXISTS post_like;
DROP TABLE IF EXISTS comment;
DROP TABLE IF EXISTS post;
DROP TABLE IF EXISTS project;
DROP TABLE IF EXISTS tag;
DROP TABLE IF EXISTS admin_user;
DROP TABLE IF EXISTS site_profile;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE post (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title        VARCHAR(200)    NOT NULL DEFAULT '',
    slug         VARCHAR(200)    NOT NULL DEFAULT '',
    content_md   LONGTEXT        NOT NULL,
    summary      VARCHAR(500)    NOT NULL DEFAULT '',
    cover_image  VARCHAR(255)    NOT NULL DEFAULT '',
    status       VARCHAR(16)     NOT NULL DEFAULT 'DRAFT',
    view_count   INT             NOT NULL DEFAULT 0,
    like_count   INT             NOT NULL DEFAULT 0,
    is_deleted   TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_post_slug (slug),
    KEY idx_post_status_in_dtm (is_deleted, status, in_dtm)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE tag (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)     NOT NULL DEFAULT '',
    is_deleted  TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_tag_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_tag (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    tag_id      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    is_deleted  TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_post_tag_pair (post_id, tag_id),
    KEY idx_post_tag_post (post_id),
    KEY idx_post_tag_tag (tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE comment (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    nickname    VARCHAR(64)     NOT NULL DEFAULT '',
    content     VARCHAR(1000)   NOT NULL DEFAULT '',
    status      VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    is_deleted  TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_comment_post (is_deleted, post_id, status, in_dtm)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE project (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title        VARCHAR(200)    NOT NULL DEFAULT '',
    description  TEXT            NOT NULL,
    cover_image  VARCHAR(255)    NOT NULL DEFAULT '',
    project_url  VARCHAR(255)    NOT NULL DEFAULT '',
    repo_url     VARCHAR(255)    NOT NULL DEFAULT '',
    tech_stack   VARCHAR(255)    NOT NULL DEFAULT '',
    sort_order   INT             NOT NULL DEFAULT 0,
    is_deleted   TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm   BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_project_list (is_deleted, sort_order, in_dtm)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE post_like (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id     BIGINT UNSIGNED NOT NULL DEFAULT 0,
    visitor_id  VARCHAR(128)    NOT NULL DEFAULT '',
    ip_hash     VARCHAR(128)    NOT NULL DEFAULT '',
    is_deleted  TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm      BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm  BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_visitor (post_id, visitor_id),
    KEY idx_post_like_post (post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE admin_user (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)     NOT NULL DEFAULT '',
    password_hash VARCHAR(255)    NOT NULL DEFAULT '',
    is_deleted    TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm        BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_admin_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE site_profile (
    id            BIGINT UNSIGNED NOT NULL,
    display_name  VARCHAR(255)    NOT NULL DEFAULT '',
    avatar_url    VARCHAR(255)    NOT NULL DEFAULT '',
    bio           TEXT            NOT NULL,
    social_links  VARCHAR(1000)   NOT NULL DEFAULT '[]',
    is_deleted    TINYINT(1)      NOT NULL DEFAULT 0,
    in_dtm        BIGINT UNSIGNED NOT NULL DEFAULT 0,
    update_dtm    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
