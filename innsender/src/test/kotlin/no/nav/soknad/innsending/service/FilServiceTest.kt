package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.SoknadAssertions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

class FilServiceTest : ApplicationTest() {

	@Autowired
	private lateinit var soknadService: SoknadService

	@Autowired
	private lateinit var filService: FilService

	@Test
	fun lastOppFilTilVedlegg() {
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDto = dokumentSoknadDto.vedleggsListe.first { "W1" == it.vedleggsnr }
		Assertions.assertTrue(vedleggDto != null)

		val filDtoSaved = filService.lagreFil(dokumentSoknadDto, Hjelpemetoder.lagFilDtoMedFil(vedleggDto))

		Assertions.assertTrue(filDtoSaved != null)
		Assertions.assertTrue(filDtoSaved.id != null)

		val hentetFilDto = filService.hentFil(dokumentSoknadDto, vedleggDto.id!!, filDtoSaved.id!!)
		Assertions.assertTrue(filDtoSaved.id == hentetFilDto.id)
	}

	@Test
	fun slettFilTilVedlegg() {
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val vedleggDto = dokumentSoknadDto.vedleggsListe.first { "W1" == it.vedleggsnr }
		Assertions.assertTrue(vedleggDto != null)

		val filDtoSaved = filService.lagreFil(dokumentSoknadDto, Hjelpemetoder.lagFilDtoMedFil(vedleggDto))

		Assertions.assertTrue(filDtoSaved != null)
		Assertions.assertTrue(filDtoSaved.id != null)

		val oppdatertSoknadDto = soknadService.hentSoknad(dokumentSoknadDto.id!!)
		Assertions.assertEquals(
			OpplastingsStatusDto.lastetOpp,
			oppdatertSoknadDto.vedleggsListe.first { it.id == vedleggDto.id!! }.opplastingsStatus
		)

		val oppdatertVedleggDto = filService.slettFil(oppdatertSoknadDto, filDtoSaved.vedleggsid, filDtoSaved.id!!)

		Assertions.assertEquals(OpplastingsStatusDto.ikkeValgt, oppdatertVedleggDto.opplastingsStatus)
	}

	@Test
	fun hentingAvDokumentFeilerNarIngenDokumentOpplastet() {
		// Opprett original soknad
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		// Sender inn original soknad
		assertThrows<ResourceNotFoundException> {
			filService.hentFil(dokumentSoknadDto, dokumentSoknadDto.vedleggsListe[0].id!!, 1L)
		}
	}
}
