CREATE SEQUENCE fil_id_seq;

CREATE TABLE fil
(
	id              BIGINT NOT NULL PRIMARY KEY DEFAULT nextval('fil_id_seq'::regclass),
	vedleggsid      BIGINT NOT NULL,
	filnavn		      VARCHAR(255),
	mimetype        VARCHAR(255),
	dokument        BYTEA,
	opprettetdato   TIMESTAMP WITH TIME ZONE NOT NULL default (now() at time zone 'UTC')
);

ALTER TABLE fil
	ADD CONSTRAINT fil_vedlegg_fk
		FOREIGN KEY (vedleggsid) REFERENCES vedlegg;
