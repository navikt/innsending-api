insert into hendelse
(id, innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending, tema)
select null,
			 innsendingsid,
			 'Opprettet'                 as hendelsetype,
			 opprettetdato               as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendingsid,
			 tema
from soknad;

insert into hendelse
(id, innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending, tema)
select null,
			 innsendingsid,
			 'SlettetAvBruker'           as hendelsetype,
			 endretdato                  as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendingsid,
			 tema
from soknad
where status = 'SlettetAvBruker';

insert into hendelse
(id, innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending, tema)
select null,
			 innsendingsid,
			 'SlettetAvSystem'           as hendelsetype,
			 endretdato                  as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendingsid,
			 tema
from soknad
where status = 'AutomatiskSlettet';

insert into hendelse
(id, innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending, tema)
select null,
			 innsendingsid,
			 'Innsendt'                  as hendelsetype,
			 innsendtdato                as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendingsid,
			 tema
from soknad
where status = 'Innsendt';

insert into hendelse
(id, innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending, tema)
select null,
			 innsendingsid,
			 'Arkivert'                  as hendelsetype,
			 endretdato                  as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendingsid,
			 tema
from soknad
where status = 'Innsendt'
	and arkiveringsstatus = 'Arkivert';
