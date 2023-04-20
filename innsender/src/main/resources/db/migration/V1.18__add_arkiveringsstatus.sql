DROP index IF EXISTS soknad_arkiveringsstatus_idx;
ALTER TABLE soknad
	DROP COLUMN IF EXISTS arkiveringsstatus;

ALTER TABLE soknad
	ADD COLUMN arkiveringsstatus varchar NOT NULL default 'IkkeSatt';

UPDATE soknad
SET arkiveringsstatus='Arkivert'
WHERE status = 'Innsendt';

CREATE INDEX soknad_arkiveringsstatus_idx ON soknad (arkiveringsstatus);
