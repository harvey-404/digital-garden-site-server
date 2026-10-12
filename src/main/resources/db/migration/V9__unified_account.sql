CREATE TABLE site_account (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 email VARCHAR(254) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 display_name VARCHAR(64) NOT NULL,
 timezone VARCHAR(64) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'active',
 created_at BIGINT NOT NULL
);
CREATE TABLE site_session (
 token_hash VARCHAR(64) NOT NULL PRIMARY KEY,
 account_id BIGINT NOT NULL,
 expires_at BIGINT NOT NULL,
 created_at BIGINT NOT NULL,
 FOREIGN KEY(account_id) REFERENCES site_account(id)
);
CREATE INDEX idx_site_session_account ON site_session(account_id);
CREATE INDEX idx_site_session_expiry ON site_session(expires_at);
CREATE TABLE site_email_challenge (
 email VARCHAR(254) NOT NULL,
 purpose VARCHAR(16) NOT NULL,
 code_hash VARCHAR(64) NOT NULL,
 expires_at BIGINT NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 consumed BOOLEAN NOT NULL DEFAULT FALSE,
 PRIMARY KEY(email,purpose)
);
CREATE TABLE site_account_link (
 account_id BIGINT NOT NULL,
 module VARCHAR(16) NOT NULL,
 local_user_id BIGINT NOT NULL,
 PRIMARY KEY(account_id,module),
 UNIQUE(module,local_user_id),
 FOREIGN KEY(account_id) REFERENCES site_account(id)
);
CREATE TABLE site_account_rate (
 bucket VARCHAR(64) NOT NULL PRIMARY KEY,
 window_start BIGINT NOT NULL,
 attempts INT NOT NULL
);
