CREATE SEQUENCE hendelse_id_seq;

CREATE TABLE hendelse
(
	id            BIGINT                   NOT NULL PRIMARY KEY DEFAULT nextval('hendelse_id_seq'::regclass),
	innsendingsid VARCHAR(255)             NOT NULL,
	hendelsetype  VARCHAR(255)             NOT NULL,
	tidspunkt     TIMESTAMP WITH TIME ZONE NOT NULL             default (now() at time zone 'UTC')
);

CREATE INDEX hendelse_status_idx ON hendelse (innsendingsid);
CREATE INDEX hendelse_hendelsetype_idx ON hendelse (hendelsetype);
