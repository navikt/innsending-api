package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.util.mapping.mapTilSoknadDb
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

class RepositoryUtilsTest : ApplicationTest() {

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var soknadService: SoknadService

	@Test
	fun testFailureWhenStatusUpdatedFromInnsendtToUtfylt() {
		val dokumentSoknadDto = Hjelpemetoder.lagDokumentSoknad()
		val soknad = soknadService.opprettNySoknad(dokumentSoknadDto)
		val innsendingsId = soknad.innsendingsId!!
		val dbData = repo.hentSoknadDb(innsendingsId)
		repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.Innsendt, dbData.id))

		val exception = assertThrows<Exception> {
			repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.Utfylt, dbData.id))
		}
		assertTrue { exception.message!!.contains("Feil i lagring av søknad") }
	}

	@Test
	fun testFailureWhenStatusUpdatedFromInnsendtToSlettetAvBruker() {
		val dokumentSoknadDto = Hjelpemetoder.lagDokumentSoknad()
		val soknad = soknadService.opprettNySoknad(dokumentSoknadDto)
		val innsendingsId = soknad.innsendingsId!!
		val dbData = repo.hentSoknadDb(innsendingsId)
		repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.Innsendt, dbData.id))

		val exception = assertThrows<Exception> {
			repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.SlettetAvBruker, dbData.id))
		}
		assertTrue { exception.message!!.contains("Feil i lagring av søknad") }
	}

	@Test
	fun testFailureWhenStatusUpdatedFromSlettetAvBrukerToInnsendt() {
		val dokumentSoknadDto = Hjelpemetoder.lagDokumentSoknad()
		val soknad = soknadService.opprettNySoknad(dokumentSoknadDto)
		val innsendingsId = soknad.innsendingsId!!
		val dbData = repo.hentSoknadDb(innsendingsId)
		repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.SlettetAvBruker, dbData.id))

		val exception = assertThrows<Exception> {
			repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.Innsendt, dbData.id))
		}
		assertTrue { exception.message!!.contains("Feil i lagring av søknad") }
	}

	@Test
	fun testFailureWhenStatusUpdatedFromSlettetAvBrukerToUtfylt() {
		val dokumentSoknadDto = Hjelpemetoder.lagDokumentSoknad()
		val soknad = soknadService.opprettNySoknad(dokumentSoknadDto)
		val innsendingsId = soknad.innsendingsId!!
		val dbData = repo.hentSoknadDb(innsendingsId)
		repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.SlettetAvBruker, dbData.id))

		val exception = assertThrows<Exception> {
			repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.Utfylt, dbData.id))
		}
		assertTrue { exception.message!!.contains("Feil i lagring av søknad") }
	}

}
