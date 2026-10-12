CREATE TABLE tracker_user (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 username VARCHAR(32) NOT NULL UNIQUE,
 password_hash VARCHAR(100) NOT NULL,
 timezone VARCHAR(64) NOT NULL
);
CREATE TABLE tracker_activity (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 name VARCHAR(80) NOT NULL,
 active_name VARCHAR(80) NULL,
 unit VARCHAR(20) NOT NULL,
 template VARCHAR(40) NOT NULL,
 track_duration BOOLEAN NOT NULL,
 archived BOOLEAN NOT NULL DEFAULT FALSE,
 UNIQUE (user_id, active_name),
 FOREIGN KEY (user_id) REFERENCES tracker_user(id)
);
CREATE TABLE tracker_rule (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 activity_id BIGINT NOT NULL,
 effective_from DATE NOT NULL,
 body TEXT NOT NULL,
 FOREIGN KEY (activity_id) REFERENCES tracker_activity(id)
);
CREATE INDEX idx_tracker_rule_activity ON tracker_rule(activity_id,effective_from);
CREATE TABLE tracker_record (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 activity_id BIGINT NOT NULL,
 quantity INT NOT NULL,
 started_at VARCHAR(40) NOT NULL,
 duration_seconds BIGINT NOT NULL,
 note VARCHAR(1000) NOT NULL,
 deleted BOOLEAN NOT NULL DEFAULT FALSE,
 segments TEXT NOT NULL,
 FOREIGN KEY (user_id) REFERENCES tracker_user(id),
 FOREIGN KEY (activity_id) REFERENCES tracker_activity(id)
);
CREATE INDEX idx_tracker_record_activity ON tracker_record(user_id,activity_id);
CREATE TABLE tracker_timer (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,
 activity_id BIGINT NOT NULL,
 started_at VARCHAR(40) NOT NULL,
 running_since VARCHAR(40) NULL,
 segments TEXT NOT NULL,
 state VARCHAR(16) NOT NULL,
 record_id BIGINT NULL,
 active_user BIGINT NULL UNIQUE,
 FOREIGN KEY (user_id) REFERENCES tracker_user(id),
 FOREIGN KEY (activity_id) REFERENCES tracker_activity(id),
 FOREIGN KEY (record_id) REFERENCES tracker_record(id)
);
CREATE TABLE tracker_request (
 user_id BIGINT NOT NULL,
 request_id VARCHAR(36) NOT NULL,
 operation VARCHAR(40) NOT NULL,
 resource_id BIGINT NOT NULL,
 PRIMARY KEY(user_id,request_id),
 FOREIGN KEY (user_id) REFERENCES tracker_user(id)
);
CREATE TABLE tracker_ai_quota (
 user_id BIGINT NOT NULL,
 quota_day DATE NOT NULL,
 used INT NOT NULL,
 PRIMARY KEY(user_id,quota_day),
 FOREIGN KEY (user_id) REFERENCES tracker_user(id)
);
