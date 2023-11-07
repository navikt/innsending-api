package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.consumerapis.arena.dto.Gyldighetsperiode
import no.nav.soknad.innsending.consumerapis.arena.dto.Maalgruppe
import no.nav.soknad.innsending.consumerapis.arena.dto.MaalgruppeType
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("local | docker")
class ArenaConsumerTest : ArenaConsumerInterface {
	override suspend fun getMaalgruppe(): List<Maalgruppe> {
		val maalgruppe =
			Maalgruppe(
				gyldighetsperiode = Gyldighetsperiode("2020-01-01", tom = "2024-01-01"),
				maalgruppetype = MaalgruppeType.ARBSOKERE,
				maalgruppenavn = "Arbeidssøkere",
			)
		return listOf(maalgruppe)
	}
}
