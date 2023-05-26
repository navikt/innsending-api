alter table hendelse
	add column skjemanr varchar(20);
alter table hendelse
	add column erettersending boolean;

insert into hendelse
	(id, innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending)
select null,
			 innsendingsid,
			 'Opprettet'                 as hendelsetype,
			 opprettetdato               as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendingsid
from soknad;

insert into hendelse
	(id, innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending)
select null,
			 innsendingsid,
			 'SlettetAvBruker'           as hendelsetype,
			 endretdato                  as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendings
from soknad
where status = 'SlettetAvBruker';

insert into hendelse
	(id, innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending)
select null,
			 innsendingsid,
			 'SlettetAvSystem'           as hendelsetype,
			 endretdato                  as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendings
from soknad
where status = 'AutomatiskSlettet';

insert into hendelse
	(id, innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending)
select null,
			 innsendingsid,
			 'Innsendt'                  as hendelsetype,
			 innsendtdato                as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendings
from soknad
where status = 'Innsendt';

insert into hendelse
	(id, innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending)
select null,
			 innsendingsid,
			 'Arkivert'                  as hendelsetype,
			 endretdato                  as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendings
from soknad
where status = 'Innsendt'
	and arkiveringsstatus = 'Arkivert';

