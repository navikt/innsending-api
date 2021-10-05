CREATE SEQUENCE soknad_id_seq;
CREATE SEQUENCE vedlegg_id_seq;


CREATE TABLE IF NOT EXISTS soknad
(
    id              BIGINT NOT NULL PRIMARY KEY DEFAULT nextval('soknad_id_seq'::regclass),
    behandlingsid   VARCHAR(255) NOT NULL,
    tittel          VARCHAR(255),
    skjemanr        VARCHAR(20),
    tema            VARCHAR(10),
    spraak          VARCHAR(10),
    status          VARCHAR(255),
    brukerid        VARCHAR(11),
    ettersendingsid VARCHAR(255),
    opprettetdato   TIMESTAMP WITH TIME ZONE NOT NULL default (now() at time zone 'UTC'),
    endretdato      TIMESTAMP WITH TIME ZONE NOT NULL default (now() at time zone 'UTC'),
    innsendtdato    TIMESTAMP WITH TIME ZONE
);

CREATE TABLE vedlegg
(
    id              BIGINT NOT NULL PRIMARY KEY DEFAULT nextval('vedlegg_id_seq'::regclass),
    soknadsid       BIGINT NOT NULL,
    status          VARCHAR(10),
    erhoveddokument BOOLEAN,
    ervariant       BOOLEAN,
    erpdfa          BOOLEAN,
    vedleggsnr      VARCHAR(255),
    vedleggsurl     VARCHAR(255),
    tittel          VARCHAR(255),
    mimetype        VARCHAR(255),
    uuid            VARCHAR(255),
    dokument        BYTEA,
    opprettetdato   TIMESTAMP WITH TIME ZONE NOT NULL default (now() at time zone 'UTC'),
    endretdato      TIMESTAMP WITH TIME ZONE NOT NULL default (now() at time zone 'UTC')
);

ALTER TABLE vedlegg
ADD CONSTRAINT vedlegg_soknad_fk
FOREIGN KEY (soknadsid) REFERENCES soknad;