ALTER TABLE soknad
	ADD COLUMN erarkivert boolean;
CREATE INDEX soknad_erarkivert_idx ON soknad (erarkivert);
