CREATE INDEX soknad_innsendingsid_idx ON soknad(innsendingsid);
CREATE INDEX soknad_brukerid_idx ON soknad(brukerid);
CREATE INDEX soknad_status_idx ON soknad(brukerid);
CREATE INDEX soknad_opprettetdato_idx ON soknad(opprettetdato);
CREATE INDEX vedlegg_soknadsid_idx ON vedlegg(soknadsid);
CREATE INDEX fil_vedleggsid_idx ON fil(vedleggsid);
