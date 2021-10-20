ALTER TABLE vedlegg drop column vedleggsurl;
ALTER TABLE soknad add column skjemaUrl varchar(255);
ALTER TABLE soknad RENAME COLUMN behandlingsid TO innsendingsid
