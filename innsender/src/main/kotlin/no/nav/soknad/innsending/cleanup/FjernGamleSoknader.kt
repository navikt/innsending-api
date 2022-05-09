package no.nav.soknad.innsending.cleanup

import no.nav.soknad.innsending.service.SoknadService
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class FjernGamleSoknader(private val soknadService: SoknadService) {

	@Value("\${cron.slettEldreEnn}")
	private lateinit var dagerGamleString: String

	@Scheduled(cron = "\${cron.slettGamleIkkeInnsendteSoknader}")
	fun fjernGamleIkkeInnsendteSoknader() {
		soknadService.slettGamleIkkeInnsendteSoknader(dagerGamleString.toLong())
	}
}
