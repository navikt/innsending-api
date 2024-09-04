package no.nav.soknad.innsending.consumerapis.antivirus

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("local | docker | endtoend | loadtests")
class AntivirusServiceTest : AntivirusInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	override fun scan(file: ByteArray): Boolean {
		logger.info("Skipper scanning av dokument for virus ved lokal kjøring")
		return true
	}
}
