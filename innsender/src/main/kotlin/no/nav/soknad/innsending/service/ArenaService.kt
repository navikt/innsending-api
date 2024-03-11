package no.nav.soknad.innsending.service

import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.consumerapis.arena.ArenaConsumerInterface
import no.nav.soknad.innsending.model.Aktivitet
import no.nav.soknad.innsending.model.AktivitetType
import no.nav.soknad.innsending.util.prefill.MaalgruppeUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class ArenaService(private val arenaConsumer: ArenaConsumerInterface) {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun getAktiviteterWithMaalgrupper(type: AktivitetType): List<Aktivitet> = runBlocking {
		logger.info("Henter aktiviteter og målgrupper fra Arena med type $type")

		val maalgrupper = arenaConsumer.getMaalgrupper()
		val aktiviteter = arenaConsumer.getAktiviteter(type)

		logger.info("Hentet aktiviteter og målgrupper fra Arena. Målgrupper: ${maalgrupper.map { it.maalgruppetype }}, aktiviteter: ${aktiviteter.map { it.aktivitetstype }}")

		aktiviteter.map { aktivitet ->
			aktivitet.copy(maalgruppe = MaalgruppeUtils.getPrioritzedMaalgruppeFromAktivitet(maalgrupper, aktivitet))
		}
	}
}
