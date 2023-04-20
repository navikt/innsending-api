package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.assertEquals

class GenererPdfTest {

	private val skjemanr = "NAV 10-07.03"
	private val tittel = "Søknad om hjelpemidler"

	@Test
	fun verifiserlagForsideEttersending() {
		val soknad = lagSoknadForTesting(tittel)

		val forside = PdfGenerator().lagForsideEttersending(soknad)

		assertEquals(1, AntallSider().finnAntallSider(forside))
		val erPdfa = Validerer().isPDFa(forside)
		assertTrue(erPdfa)

	}

	@Test
	fun verifiserGenereringAvKvitteringsPdf_medSpesialtegn() {
		val soknad = lagSoknadForTesting(tittel)

		val sammensattnavn = "śander Ełmer"
		val kvittering = PdfGenerator().lagKvitteringsSide(
			soknad,
			sammensattnavn,
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.innsendt },
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.sendSenere })

		//writeBytesToFile(kvittering, "./delme.pdf")

		assertEquals(1, AntallSider().finnAntallSider(kvittering))
		val erPdfa = Validerer().isPDFa(kvittering)
		assertTrue(erPdfa)

	}


	@Test
	fun verifiserGenereringAvKvitteringsPdf() {

		val soknad = lagSoknadForTesting(tittel)

		val sammensattnavn = "Fornavn Elmer"
		val kvittering = PdfGenerator().lagKvitteringsSide(
			soknad,
			sammensattnavn,
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.innsendt },
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.sendSenere })

		//writeBytesToFile(kvittering, "./soknadskvittering.pdf")

		assertEquals(1, AntallSider().finnAntallSider(kvittering))
		val erPdfa = Validerer().isPDFa(kvittering)
		assertTrue(erPdfa)

	}

	@Test
	fun verifiserGenereringAvEttersendingsKvitteringsPdf() {

		val soknad = lagEttersendingsSoknadForTesting(tittel)

		val sammensattnavn = "Fornavn Elmer"
		val kvittering = PdfGenerator().lagKvitteringsSide(
			soknad,
			sammensattnavn,
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.innsendt && it.opprettetdato > OffsetDateTime.MIN },
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.sendSenere })

		//writeBytesToFile(kvittering, "./ettersendingskvittering.pdf")

		assertEquals(1, AntallSider().finnAntallSider(kvittering))
		val erPdfa = Validerer().isPDFa(kvittering)
		assertTrue(erPdfa)

	}

	@Test
	fun verifiserGenereringAvEttersendingsKvitteringsPdf_altInnsendt() {

		val soknad = lagEttersendingsSoknadAltInnsendt(tittel)

		val sammensattnavn = "Fornavn Elmer"
		val kvittering = PdfGenerator().lagKvitteringsSide(
			soknad,
			sammensattnavn,
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.innsendt && it.opprettetdato > OffsetDateTime.MIN },
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.sendSenere })

		//writeBytesToFile(kvittering, "./ettersendingskvittering2.pdf")

		assertEquals(1, AntallSider().finnAntallSider(kvittering))
		val erPdfa = Validerer().isPDFa(kvittering)
		assertTrue(erPdfa)

	}

	private fun lagSoknadForTesting(tittel: String): DokumentSoknadDto {
		val brukerid = "20128012345"
		val opprettetDato = OffsetDateTime.now()
		val vedleggDtos = listOf(
			VedleggDto(
				tittel = tittel, label = tittel + ", " + skjemanr,
				erHoveddokument = true, erVariant = false, erPdfa = true, erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.innsendt, opprettetdato = OffsetDateTime.MIN
			),
			VedleggDto(
				tittel = tittel,
				label = tittel + ", " + skjemanr,
				erHoveddokument = true,
				erVariant = true,
				erPdfa = true,
				erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.innsendt,
				opprettetdato = OffsetDateTime.MIN,
				innsendtdato = OffsetDateTime.now()
			),
			VedleggDto(
				tittel = "Vedlegg1",
				label = "Vedlegg1, NAV 08-36.02",
				erHoveddokument = false,
				erVariant = false,
				erPdfa = true,
				erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.innsendt,
				opprettetdato = OffsetDateTime.MIN,
				innsendtdato = OffsetDateTime.now()
			),
			VedleggDto(
				tittel = "Vedlegg2", label = "Vedlegg2, NAV 08-36.03",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.sendSenere, opprettetdato = OffsetDateTime.MIN
			),
			VedleggDto(
				tittel = "Vedlegg3", label = "Vedlegg3, NAV 08-36.04",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.sendSenere, opprettetdato = OffsetDateTime.MIN
			),
			VedleggDto(
				tittel = "Vedlegg4", label = "Vedlegg4",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = false,
				opplastingsStatus = OpplastingsStatusDto.sendesAvAndre, opprettetdato = OffsetDateTime.MIN
			),
			VedleggDto(
				tittel = "Vedlegg5", label = "Vedlegg5",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.sendesIkke, opprettetdato = OffsetDateTime.MIN
			),
			VedleggDto(
				tittel = "Vedlegg6", label = "Vedlegg6",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = false,
				opplastingsStatus = OpplastingsStatusDto.sendesIkke, opprettetdato = OffsetDateTime.MIN
			)

		)
		return DokumentSoknadDto(
			brukerId = brukerid, skjemanr = skjemanr, tittel = tittel, tema = "TMA",
			status = SoknadsStatusDto.innsendt, innsendtDato = OffsetDateTime.now(),
			innsendingsId = UUID.randomUUID().toString(), opprettetDato = opprettetDato, vedleggsListe = vedleggDtos
		)
	}

	private fun lagEttersendingsSoknadForTesting(tittel: String): DokumentSoknadDto {
		val brukerid = "20128012345"
		val opprettetDato = OffsetDateTime.now()
		val vedleggDtos = listOf(
			VedleggDto(
				tittel = tittel,
				label = tittel + ", " + skjemanr,
				erHoveddokument = true,
				erVariant = false,
				erPdfa = true,
				erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.innsendt,
				opprettetdato = OffsetDateTime.now(),
				innsendtdato = OffsetDateTime.now()
			),
			VedleggDto(
				tittel = "Vedlegg1",
				label = "Vedlegg1, NAV 08-36.02",
				erHoveddokument = false,
				erVariant = false,
				erPdfa = true,
				erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.innsendt,
				opprettetdato = OffsetDateTime.MIN,
				innsendtdato = OffsetDateTime.MIN
			),
			VedleggDto(
				tittel = "Vedlegg2",
				label = "Vedlegg2, NAV 08-36.03",
				erHoveddokument = false,
				erVariant = false,
				erPdfa = true,
				erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.innsendt,
				opprettetdato = OffsetDateTime.now(),
				innsendtdato = OffsetDateTime.now()
			),
			VedleggDto(
				tittel = "Vedlegg3", label = "Vedlegg3, NAV 08-36.04",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.sendSenere, opprettetdato = OffsetDateTime.MIN
			),
			VedleggDto(
				tittel = "Vedlegg4", label = "Vedlegg4",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = false,
				opplastingsStatus = OpplastingsStatusDto.sendesAvAndre, opprettetdato = OffsetDateTime.MIN
			)
		)
		return DokumentSoknadDto(
			brukerId = brukerid, skjemanr = skjemanr, tittel = tittel, tema = "TMA",
			status = SoknadsStatusDto.innsendt, innsendtDato = OffsetDateTime.now(),
			innsendingsId = UUID.randomUUID().toString(), ettersendingsId = UUID.randomUUID().toString(),
			opprettetDato = opprettetDato, vedleggsListe = vedleggDtos,
			forsteInnsendingsDato = OffsetDateTime.MIN
		)

	}

	private fun lagEttersendingsSoknadAltInnsendt(tittel: String): DokumentSoknadDto {
		val brukerid = "20128012345"
		val opprettetDato = OffsetDateTime.now()
		val vedleggDtos = listOf(
			VedleggDto(
				tittel = tittel,
				label = tittel + ", " + skjemanr,
				erHoveddokument = true,
				erVariant = false,
				erPdfa = true,
				erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.innsendt,
				opprettetdato = OffsetDateTime.now(),
				innsendtdato = OffsetDateTime.now()
			),
			VedleggDto(
				tittel = "Vedlegg1",
				label = "Vedlegg1, NAV 08-36.02",
				erHoveddokument = false,
				erVariant = false,
				erPdfa = true,
				erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.innsendt,
				opprettetdato = OffsetDateTime.MIN,
				innsendtdato = OffsetDateTime.MIN
			),
			VedleggDto(
				tittel = "Vedlegg2",
				label = "Vedlegg2, NAV 08-36.03",
				erHoveddokument = false,
				erVariant = false,
				erPdfa = true,
				erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.innsendt,
				opprettetdato = OffsetDateTime.now(),
				innsendtdato = OffsetDateTime.now()
			),
			VedleggDto(
				tittel = "Vedlegg3",
				label = "Vedlegg3, NAV 08-36.04",
				erHoveddokument = false,
				erVariant = false,
				erPdfa = true,
				erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.innsendt,
				opprettetdato = OffsetDateTime.MIN,
				innsendtdato = OffsetDateTime.now()
			),
			VedleggDto(
				tittel = "Vedlegg4", label = "Vedlegg4",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = false,
				opplastingsStatus = OpplastingsStatusDto.sendesAvAndre, opprettetdato = OffsetDateTime.MIN
			)
		)
		return DokumentSoknadDto(
			brukerId = brukerid, skjemanr = skjemanr, tittel = tittel, tema = "TMA",
			status = SoknadsStatusDto.innsendt, innsendtDato = OffsetDateTime.now(),
			innsendingsId = UUID.randomUUID().toString(), ettersendingsId = UUID.randomUUID().toString(),
			opprettetDato = opprettetDato, vedleggsListe = vedleggDtos,
			forsteInnsendingsDato = OffsetDateTime.MIN
		)

	}

}
