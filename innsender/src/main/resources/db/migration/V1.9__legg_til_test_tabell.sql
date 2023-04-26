CREATE SEQUENCE alive_id_seq;

CREATE TABLE alive
(
	id        BIGINT NOT NULL PRIMARY KEY DEFAULT nextval('alive_id_seq'::regclass),
	test      VARCHAR(2)
);

INSERT INTO alive(test) values ('ok');
