package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.util.mapping.tilleggsstonad.kjoreliste
import no.nav.soknad.innsending.util.mapping.tilleggsstonad.reiseDaglig
import no.nav.soknad.innsending.util.mapping.tilleggsstonad.stotteTilBolig
import no.nav.soknad.innsending.util.mapping.tilleggsstonad.stotteTilFlytting
import no.nav.soknad.innsending.util.testpersonid
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.SoknadAssertions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class TilleggsstonadServiceTest: InnsendingServiceTest() {


	@Test
	fun sendInnTilleggssoknad_dagligreise() {
		val innsendingService = lagInnsendingService(soknadService)
		val hoveddokDto = Hjelpemetoder.lagVedlegg(
			vedleggsnr = reiseDaglig,
			tittel = "Tilleggssoknad",
			erHoveddokument = true,
			erVariant = false,
			opplastingsStatus = OpplastingsStatusDto.lastetOpp,
			vedleggsNavn = "/litenPdf.pdf"
		)
		val hoveddokVariantDto = Hjelpemetoder.lagVedlegg(
			vedleggsnr = reiseDaglig,
			tittel = "Tilleggssoknad",
			erHoveddokument = true,
			erVariant = true,
			opplastingsStatus = OpplastingsStatusDto.lastetOpp,
			vedleggsNavn = "/__files/dagligreise-NAV-11-12.21B-08032024.json"
		)
		val inputDokumentSoknadDto = Hjelpemetoder.lagDokumentSoknad(
			skjemanr = reiseDaglig,
			tittel = "Tilleggssoknad",
			brukerId = testpersonid,
			vedleggsListe = listOf(hoveddokDto, hoveddokVariantDto),
			spraak = "no_NB",
			tema = "TSO"
		)
		val skjemaDto =
			SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService = soknadService, inputDokumentSoknadDto)

		val opprettetSoknad = soknadService.hentSoknad(skjemaDto.innsendingsId!!)
		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				opprettetSoknad,
				innsendingService
			)
		Assertions.assertTrue(kvitteringsDto.hoveddokumentRef != null)
		Assertions.assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		Assertions.assertTrue(kvitteringsDto.skalEttersendes!!.isEmpty())
	}


	@Test
	fun sendInnTilleggssoknad_bostotte() {
		val innsendingService = lagInnsendingService(soknadService)
		val skjemaNr = stotteTilBolig
		val hoveddokDto = Hjelpemetoder.lagVedlegg(
			vedleggsnr = skjemaNr,
			tittel = "Tilleggssoknad",
			erHoveddokument = true,
			erVariant = false,
			opplastingsStatus = OpplastingsStatusDto.lastetOpp,
			vedleggsNavn = "/litenPdf.pdf"
		)
		val hoveddokVariantDto = Hjelpemetoder.lagVedlegg(
			vedleggsnr = skjemaNr,
			tittel = "Tilleggssoknad",
			erHoveddokument = true,
			erVariant = true,
			opplastingsStatus = OpplastingsStatusDto.lastetOpp,
			vedleggsNavn = "/__files/tilleggsstonad-NAV-11-12.19B-28022024.json"
		)
		val inputDokumentSoknadDto = Hjelpemetoder.lagDokumentSoknad(
			skjemanr = skjemaNr,
			tittel = "Tilleggssoknad",
			brukerId = testpersonid,
			vedleggsListe = listOf(hoveddokDto, hoveddokVariantDto),
			spraak = "no_NB",
			tema = "TSO"
		)
		val skjemaDto =
			SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService = soknadService, inputDokumentSoknadDto)

		val opprettetSoknad = soknadService.hentSoknad(skjemaDto.innsendingsId!!)
		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				opprettetSoknad,
				innsendingService
			)
		Assertions.assertTrue(kvitteringsDto.hoveddokumentRef != null)
		Assertions.assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		Assertions.assertTrue(kvitteringsDto.skalEttersendes!!.isEmpty())

		val innsendtSoknad = soknadService.hentSoknadMedHoveddokumentVariant(opprettetSoknad.innsendingsId!!)
		Assertions.assertTrue(innsendtSoknad.status == SoknadsStatusDto.innsendt)
		Assertions.assertEquals("TSO", innsendtSoknad.tema)
		Assertions.assertEquals(
			Mimetype.applicationSlashXml,
			innsendtSoknad.vedleggsListe.filter { it.erHoveddokument && it.erVariant && it.opplastingsStatus == OpplastingsStatusDto.innsendt }
				.first().mimetype
		)
		Assertions.assertEquals(
			Mimetype.applicationSlashJson,
			innsendtSoknad.vedleggsListe.filter { it.erHoveddokument && it.erVariant && it.opplastingsStatus == OpplastingsStatusDto.sendesIkke }
				.first().mimetype
		)

	}

	@Test
	fun sendInnTilleggssoknad_bostotte_samling() {
		val innsendingService = lagInnsendingService(soknadService)
		val skjemaNr = stotteTilBolig
		val hoveddokDto = Hjelpemetoder.lagVedlegg(
			vedleggsnr = skjemaNr,
			tittel = "Tilleggssoknad",
			erHoveddokument = true,
			erVariant = false,
			opplastingsStatus = OpplastingsStatusDto.lastetOpp,
			vedleggsNavn = "/litenPdf.pdf"
		)
		val hoveddokVariantDto = Hjelpemetoder.lagVedlegg(
			vedleggsnr = skjemaNr,
			tittel = "Tilleggssoknad",
			erHoveddokument = true,
			erVariant = true,
			opplastingsStatus = OpplastingsStatusDto.lastetOpp,
			vedleggsNavn = "/__files/tilleggsstonad-NAV-11-12.19B-samling.json"
		)
		val inputDokumentSoknadDto = Hjelpemetoder.lagDokumentSoknad(
			skjemanr = skjemaNr,
			tittel = "Tilleggssoknad",
			brukerId = testpersonid,
			vedleggsListe = listOf(hoveddokDto, hoveddokVariantDto),
			spraak = "no_NB",
			tema = "TSO"
		)
		val skjemaDto =
			SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService = soknadService, inputDokumentSoknadDto)

		val opprettetSoknad = soknadService.hentSoknad(skjemaDto.innsendingsId!!)
		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				opprettetSoknad,
				innsendingService
			)
		Assertions.assertTrue(kvitteringsDto.hoveddokumentRef != null)
		Assertions.assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		Assertions.assertTrue(kvitteringsDto.skalEttersendes!!.isEmpty())

		val innsendtSoknad = soknadService.hentSoknadMedHoveddokumentVariant(opprettetSoknad.innsendingsId!!)
		Assertions.assertTrue(innsendtSoknad.status == SoknadsStatusDto.innsendt)
		Assertions.assertEquals("TSO", innsendtSoknad.tema)
		Assertions.assertEquals(
			Mimetype.applicationSlashXml,
			innsendtSoknad.vedleggsListe.filter { it.erHoveddokument && it.erVariant && it.opplastingsStatus == OpplastingsStatusDto.innsendt }
				.first().mimetype
		)
		Assertions.assertEquals(
			Mimetype.applicationSlashJson,
			innsendtSoknad.vedleggsListe.filter { it.erHoveddokument && it.erVariant && it.opplastingsStatus == OpplastingsStatusDto.sendesIkke }
				.first().mimetype
		)

	}


	@Test
	fun sendInnTilleggssoknad_flytting() {
		val innsendingService = lagInnsendingService(soknadService)
		val skjemaNr = stotteTilFlytting
		val hoveddokDto = Hjelpemetoder.lagVedlegg(
			vedleggsnr = skjemaNr,
			tittel = "Tilleggssoknad",
			erHoveddokument = true,
			erVariant = false,
			opplastingsStatus = OpplastingsStatusDto.lastetOpp,
			vedleggsNavn = "/litenPdf.pdf"
		)
		val hoveddokVariantDto = Hjelpemetoder.lagVedlegg(
			vedleggsnr = skjemaNr,
			tittel = "Tilleggssoknad",
			erHoveddokument = true,
			erVariant = true,
			opplastingsStatus = OpplastingsStatusDto.lastetOpp,
			vedleggsNavn = "/__files/flytteutgifter-NAV-11-12.23B-10042024.json"
		)
		val inputDokumentSoknadDto = Hjelpemetoder.lagDokumentSoknad(
			skjemanr = skjemaNr,
			tittel = "Tilleggssoknad",
			brukerId = testpersonid,
			vedleggsListe = listOf(hoveddokDto, hoveddokVariantDto),
			spraak = "no_NB",
			tema = "TSO"
		)
		val skjemaDto =
			SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService = soknadService, inputDokumentSoknadDto)

		val opprettetSoknad = soknadService.hentSoknad(skjemaDto.innsendingsId!!)
		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				opprettetSoknad,
				innsendingService
			)
		Assertions.assertTrue(kvitteringsDto.hoveddokumentRef != null)
		Assertions.assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		Assertions.assertTrue(kvitteringsDto.skalEttersendes!!.isEmpty())

		val innsendtSoknad = soknadService.hentSoknadMedHoveddokumentVariant(opprettetSoknad.innsendingsId!!)
		Assertions.assertTrue(innsendtSoknad.status == SoknadsStatusDto.innsendt)
		Assertions.assertEquals("TSR", innsendtSoknad.tema)
		Assertions.assertEquals(
			Mimetype.applicationSlashXml,
			innsendtSoknad.vedleggsListe.filter { it.erHoveddokument && it.erVariant && it.opplastingsStatus == OpplastingsStatusDto.innsendt }
				.first().mimetype
		)
		Assertions.assertEquals(
			Mimetype.applicationSlashJson,
			innsendtSoknad.vedleggsListe.filter { it.erHoveddokument && it.erVariant && it.opplastingsStatus == OpplastingsStatusDto.sendesIkke }
				.first().mimetype
		)

	}

	@Test
	fun sendInnKjoreliste() {
		val innsendingService = lagInnsendingService(soknadService)
		val hoveddokDto = Hjelpemetoder.lagVedlegg(
			vedleggsnr = kjoreliste,
			tittel = "Kjøreliste",
			erHoveddokument = true,
			erVariant = false,
			opplastingsStatus = OpplastingsStatusDto.lastetOpp,
			vedleggsNavn = "/litenPdf.pdf"
		)
		val hoveddokVariantDto = Hjelpemetoder.lagVedlegg(
			vedleggsnr = kjoreliste,
			tittel = "Kjøreliste",
			erHoveddokument = true,
			erVariant = true,
			opplastingsStatus = OpplastingsStatusDto.lastetOpp,
			vedleggsNavn = "/__files/kjøreliste-NAV-11-12.24B-05032024.json"
		)
		val inputDokumentSoknadDto = Hjelpemetoder.lagDokumentSoknad(
			skjemanr = kjoreliste,
			tittel = "Kjøreliste",
			brukerId = testpersonid,
			vedleggsListe = listOf(hoveddokDto, hoveddokVariantDto),
			spraak = "no_NB",
			tema = "TSO"
		)
		val skjemaDto =
			SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService = soknadService, inputDokumentSoknadDto)

		val opprettetSoknad = soknadService.hentSoknad(skjemaDto.innsendingsId!!)
		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				opprettetSoknad,
				innsendingService
			)
		Assertions.assertTrue(kvitteringsDto.hoveddokumentRef != null)
		Assertions.assertTrue(kvitteringsDto.innsendteVedlegg!!.isEmpty())
		Assertions.assertTrue(kvitteringsDto.skalEttersendes!!.isEmpty())

		val innsendtSoknad = soknadService.hentSoknadMedHoveddokumentVariant(opprettetSoknad.innsendingsId!!)
		Assertions.assertTrue(innsendtSoknad.status == SoknadsStatusDto.innsendt)
		Assertions.assertEquals("TSR", innsendtSoknad.tema)
		Assertions.assertEquals(
			Mimetype.applicationSlashXml,
			innsendtSoknad.vedleggsListe.filter { it.erHoveddokument && it.erVariant && it.opplastingsStatus == OpplastingsStatusDto.innsendt }
				.first().mimetype
		)
		Assertions.assertEquals(
			Mimetype.applicationSlashJson,
			innsendtSoknad.vedleggsListe.filter { it.erHoveddokument && it.erVariant && it.opplastingsStatus == OpplastingsStatusDto.sendesIkke }
				.first().mimetype
		)

	}

}
