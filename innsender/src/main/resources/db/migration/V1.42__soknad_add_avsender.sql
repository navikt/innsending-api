ALTER TABLE soknad ADD COLUMN avsender JSONB;
ALTER TABLE soknad DROP COLUMN avsenderid;
ALTER TABLE soknad DROP COLUMN avsendertype;
ALTER TABLE soknad DROP COLUMN avsendernavn;
