ALTER TABLE soknad ADD COLUMN arkiveringsstatus varchar NOT NULL default 'IkkeSatt';

UPDATE soknad SET arkiveringsstatus='arkivert' WHERE status = 'Innsendt';

CREATE INDEX soknad_arkiveringsstatus_idx ON soknad(arkiveringsstatus);
