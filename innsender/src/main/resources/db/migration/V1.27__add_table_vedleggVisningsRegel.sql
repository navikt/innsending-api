CREATE SEQUENCE visningsregel_id_seq;

CREATE TABLE vedleggvisningsregel
(
	id            			BIGINT                   NOT NULL PRIMARY KEY DEFAULT nextval('visningsregel_id_seq'::regclass),
	vedleggsid					BIGINT			             NOT NULL,
	radiovalg  					VARCHAR(255)             NOT NULL,
	kommentarledetekst	VARCHAR(255),
	kommentarbeskivelsestekst	VARCHAR(255),
	notifikasjonstekst	VARCHAR(255)
);

CREATE INDEX visningsregel_vedleggsid_idx ON vedleggvisningsregel (vedleggsid);
ALTER TABLE vedleggvisningsregel
	ADD CONSTRAINT vedleggvisningsregel_vedlegg_fk
		FOREIGN KEY (vedleggsid) REFERENCES vedlegg;
