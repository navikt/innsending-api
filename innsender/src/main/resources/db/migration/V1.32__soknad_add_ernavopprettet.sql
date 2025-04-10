ALTER TABLE soknad ADD COLUMN ernavopprettet boolean;

update soknad set ernavopprettet = applikasjon not like '%dcp.%';
