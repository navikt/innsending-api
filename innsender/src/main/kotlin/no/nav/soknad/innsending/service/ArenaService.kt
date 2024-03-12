package no.nav.soknad.innsending.service

import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.consumerapis.arena.ArenaConsumerInterface
import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.AktivitetEndepunkt
import no.nav.soknad.innsending.util.prefill.MaalgruppeUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ArenaService(private val arenaConsumer: ArenaConsumerInterface) {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun getAktiviteterWithMaalgrupper(aktivitetEndepunkt: AktivitetEndepunkt): List<Aktivitet> = runBlocking {
		logger.info("Henter aktiviteter og målgrupper fra Arena med endepunkt $aktivitetEndepunkt")

		val maalgrupper = arenaConsumer.getMaalgrupper()
		val aktiviteter = arenaConsumer.getAktiviteter(aktivitetEndepunkt)

		logger.info("Hentet aktiviteter og målgrupper fra Arena. Målgrupper: ${maalgrupper.map { it.maalgruppetype }}, aktiviteter: ${aktiviteter.map { it.aktivitetstype }}")

		aktiviteter
			.filter { it.erStoenadsberettigetAktivitet == true }
			.map { aktivitet ->
				aktivitet.copy(maalgruppe = MaalgruppeUtils.getPrioritzedMaalgruppeFromAktivitet(maalgrupper, aktivitet))
			}
	}
}
