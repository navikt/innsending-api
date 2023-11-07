package no.nav.soknad.innsending.consumerapis.arena

import no.nav.soknad.innsending.model.Gyldighetsperiode
import no.nav.soknad.innsending.model.Maalgruppe
import no.nav.soknad.innsending.model.MaalgruppeType
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
@Profile("local | docker")
class ArenaConsumerTest : ArenaConsumerInterface {
	override suspend fun getMaalgrupper(): List<Maalgruppe> {
		val maalgruppe =
			Maalgruppe(
				gyldighetsperiode = Gyldighetsperiode(
					fom = LocalDate.now().minusMonths(2),
					tom = LocalDate.now().plusMonths(2)
				),
				maalgruppetype = MaalgruppeType.ARBSOKERE,
				maalgruppenavn = "Arbeidssøker",
			)
		return listOf(maalgruppe)
	}
}
