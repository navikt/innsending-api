DROP INDEX soknad_status_idx;
CREATE INDEX soknad_status_idx ON soknad (status);
