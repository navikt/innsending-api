insert into hendelse
(innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending, tema)
select
  		 innsendingsid,
			 'Opprettet'                 as hendelsetype,
			 opprettetdato               as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendingsid,
			 tema
from soknad;

insert into hendelse
(innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending, tema)
select
			 innsendingsid,
			 'SlettetAvBruker'           as hendelsetype,
			 endretdato                  as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendingsid,
			 tema
from soknad
where status = 'SlettetAvBruker';

insert into hendelse
(innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending, tema)
select
			 innsendingsid,
			 'SlettetAvSystem'           as hendelsetype,
			 endretdato                  as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendingsid,
			 tema
from soknad
where status = 'AutomatiskSlettet';

insert into hendelse
(innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending, tema)
select
			 innsendingsid,
			 'Innsendt'                  as hendelsetype,
			 innsendtdato                as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendingsid,
			 tema
from soknad
where status = 'Innsendt';

insert into hendelse
(innsendingsid, hendelsetype, tidspunkt, skjemanr, erettersending, tema)
select
			 innsendingsid,
			 'Arkivert'                  as hendelsetype,
			 endretdato                  as tidspunkt,
			 skjemanr,
			 ettersendingsid IS NOT NULL as erettersendingsid,
			 tema
from soknad
where status = 'Innsendt'
	and arkiveringsstatus = 'Arkivert';
