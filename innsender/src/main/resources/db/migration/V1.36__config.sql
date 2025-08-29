CREATE TABLE config
(
	id          BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
	key         VARCHAR(64) UNIQUE       NOT NULL,
	value       VARCHAR(32),
	created_at  TIMESTAMP WITH TIME ZONE NOT NULL default (now() at time zone 'UTC'),
	modified_at TIMESTAMP WITH TIME ZONE,
	modified_by VARCHAR(128)
);

INSERT INTO config (key, value)
VALUES ('nologin_main_switch', 'off');
INSERT INTO config (key, value)
VALUES ('nologin_max_file_uploads_count', '1000');
INSERT INTO config (key, value)
VALUES ('nologin_max_file_uploads_window_minutes', '10');
INSERT INTO config (key, value)
VALUES ('nologin_max_submissions_count', '200');
INSERT INTO config (key, value)
VALUES ('nologin_max_submissions_window_minutes', '10');
