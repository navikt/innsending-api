package no.nav.soknad.innsending.consumerapis.kontoregister

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("local | docker")
class KontoregisterServiceLocal : KontoregisterInterface {
	override fun getKontonummer(): String {
		return "8361347234732292"
	}
}
