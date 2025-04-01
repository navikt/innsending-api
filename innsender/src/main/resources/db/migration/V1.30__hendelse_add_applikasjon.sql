ALTER TABLE hendelse ADD COLUMN applikasjon VARCHAR(255);

UPDATE hendelse h
SET applikasjon = s.applikasjon
FROM soknad s
WHERE h.innsendingsid = s.innsendingsid;
