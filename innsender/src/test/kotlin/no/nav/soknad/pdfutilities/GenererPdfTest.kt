package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.utils.Hjelpemetoder.Companion.writeBytesToFile
import org.junit.Test
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.assertEquals

class GenererPdfTest {

	private val skjemanr = "NAV 10-07.03"
	private val tittel = "Søknad om hjelpemidler"

	private val formFeed = "\u000C"
	private val tab = "\t"
	private val lineFeed = "\u000a"
	private val backspace = "\u0008"

	@Test
	fun verifiserlagForsideEttersending() {
		val soknad = lagEttersendingsSoknadForTesting(tittel, spraak = "en-UK")

		val sammensattnavn = "Fornavn Mellomnavn Etternavn"
		val forside = PdfGenerator().lagForsideEttersending(soknad, sammensattnavn)

		assertEquals(1, AntallSider().finnAntallSider(forside))
		isPdfaTest(forside)

	}

	@Test
	fun verifiserGenereringAvKvitteringsPdf_medSpesialtegn() {
		val soknad = lagSoknadForTesting(tittel)

		val sammensattnavn = "śander Ełmer"
		val kvittering = PdfGenerator().lagKvitteringsSide(
			soknad,
			sammensattnavn,
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.Innsendt },
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.SendSenere })

		assertEquals(2, AntallSider().finnAntallSider(kvittering))
		isPdfaTest(kvittering)

	}

	@Test
	fun verifiserGenereringAvKvitteringsPdf_spesialtegnPaVedlegg() {
		val soknad = lagSoknadForTesting("Jan har en hund🐶 med tre ben og to haler. $formFeed$tab$lineFeed")

		val sammensattnavn = "asdfasdf"
		val kvittering = PdfGenerator().lagKvitteringsSide(
			soknad,
			sammensattnavn,
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.Innsendt },
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.SendSenere })

		assertEquals(2, AntallSider().finnAntallSider(kvittering))
		isPdfaTest(kvittering)

	}

	@Test
	fun verifiserGenereringAvKvitteringsPdf() {

		val soknad = lagSoknadForTesting(tittel, "nb_NO")

		val sammensattnavn = "Fornavn Elmer"
		val kvittering = PdfGenerator().lagKvitteringsSide(
			soknad,
			sammensattnavn,
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.Innsendt },
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.SendSenere })

		assertEquals(2, AntallSider().finnAntallSider(kvittering))
		isPdfaTest(kvittering)

	}

	@Test
	fun verifiserGenereringAvKvitteringsPdf_nynorsk() {

		val soknad = lagSoknadForTesting(tittel, "nn-NO")

		val sammensattnavn = "Fornavn Mellomnavn Etternavn"
		val kvittering = PdfGenerator().lagKvitteringsSide(
			soknad,
			sammensattnavn,
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.Innsendt },
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.SendSenere })

		assertEquals(2, AntallSider().finnAntallSider(kvittering))
		isPdfaTest(kvittering)

	}

	@Test
	fun verifiserGenereringAvEttersendingsKvitteringsPdf() {

		val soknad = lagEttersendingsSoknadForTesting(tittel)

		val sammensattnavn = "Fornavn Elmer"
		val kvittering = PdfGenerator().lagKvitteringsSide(
			soknad,
			sammensattnavn,
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.Innsendt && it.opprettetdato > OffsetDateTime.MIN },
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.SendSenere })

		assertEquals(1, AntallSider().finnAntallSider(kvittering))
		isPdfaTest(kvittering)
	}

	private fun isPdfaTest(document: ByteArray) {
		// PDFBox mangler funksjonalitet for å validere versjon PDF/A-2A.
		// Skriv generert PDF til disk og last opp til en online verifiseringssite, f.eks. https://www.pdf-online.com/osa/validate.aspx

		writeBytesToFile(document, "./pdf-til-validering.pdf")
		//assertTrue(Validerer().isPDFa(document)) PDFBox mangler funksjonalitet for å validere versjon PDF/A-2A.
	}

	@Test
	fun verifiserGenereringAvEttersendingsKvitteringsPdf_altInnsendt() {

		val soknad = lagEttersendingsSoknadAltInnsendt(tittel)

		val sammensattnavn = "Fornavn Elmer"
		val kvittering = PdfGenerator().lagKvitteringsSide(
			soknad,
			sammensattnavn,
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.Innsendt && it.opprettetdato > OffsetDateTime.MIN },
			soknad.vedleggsListe.filter { it.opplastingsStatus == OpplastingsStatusDto.SendSenere })

		assertEquals(1, AntallSider().finnAntallSider(kvittering))
		isPdfaTest(kvittering)

	}

	private fun lagSoknadForTesting(tittel: String, spraak: String? = "nb_NO"): DokumentSoknadDto {
		val brukerid = "20128012345"
		val opprettetDato = OffsetDateTime.now()
		val vedleggDtos = listOf(
			VedleggDto(
				tittel = tittel, label = tittel + ", " + skjemanr,
				erHoveddokument = true, erVariant = false, erPdfa = true, erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.Innsendt, opprettetdato = OffsetDateTime.MIN
			),
			VedleggDto(
				tittel = tittel,
				label = tittel + ", " + skjemanr,
				erHoveddokument = true,
				erVariant = true,
				erPdfa = true,
				erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.Innsendt,
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
				opplastingsStatus = OpplastingsStatusDto.Innsendt,
				opprettetdato = OffsetDateTime.MIN,
				innsendtdato = OffsetDateTime.now()
			),
			VedleggDto(
				tittel = "Vedlegg2", label = "Vedlegg2, NAV 08-36.03",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.SendSenere, opprettetdato = OffsetDateTime.MIN
			),
			VedleggDto(
				tittel = "Vedlegg3", label = "Vedlegg3, NAV 08-36.04",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.SendSenere, opprettetdato = OffsetDateTime.MIN
			),
			VedleggDto(
				tittel = "Vedlegg4", label = "Vedlegg4",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = false,
				opplastingsStatus = OpplastingsStatusDto.SendesAvAndre, opprettetdato = OffsetDateTime.MIN,
				opplastingsValgKommentarLedetekst = "Hvem skal sende inn denne dokumentasjonen",
				opplastingsValgKommentar = "Jeg har bedt min arbeidsgiver om å sende inn denne dokumentasjonen"
			),
			VedleggDto(
				tittel = "Vedlegg8", label = "Vedlegg8",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = false,
				opplastingsStatus = OpplastingsStatusDto.SendesAvAndre, opprettetdato = OffsetDateTime.MIN,
				opplastingsValgKommentarLedetekst = "Hvem skal sende inn denne dokumentasjonen",
				opplastingsValgKommentar = "Jeg har bedt min lege om å sende inn denne dokumentasjonen"
			),
			VedleggDto(
				tittel = "Vedlegg5", label = "Vedlegg5",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.HarIkkeDokumentasjonen, opprettetdato = OffsetDateTime.MIN,
				opplastingsValgKommentarLedetekst = "Forklar hvorfor du ikke har denne dokumentasjonen",
				opplastingsValgKommentar = "Jeg har ikke mottatt denne dokumentasjonen fra min sønns skole"
			),
			VedleggDto(
				tittel = "Vedlegg6", label = "Vedlegg6",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = false,
				opplastingsStatus = OpplastingsStatusDto.LevertDokumentasjonTidligere, opprettetdato = OffsetDateTime.MIN,
				opplastingsValgKommentarLedetekst = "Forklar i hvilken sammenheng du leverte denne dokumentasjonen til NAV",
				opplastingsValgKommentar = "Jeg leverte denne for ett år siden i forbindelse med en søknad om støtte til barnepass$formFeed"
			),
			VedleggDto(
				tittel = "Vedlegg7", label = "Vedlegg7",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = false,
				opplastingsStatus = OpplastingsStatusDto.NavKanHenteDokumentasjon, opprettetdato = OffsetDateTime.MIN,
				opplastingsValgKommentarLedetekst = "Bekreft at du gir NAV tillatelse til å hente inn denne dokumentasjonen",
				opplastingsValgKommentar = "Jeg bekrefter at NAV kan innhente denne dokumentasjonen på vegne av meg"
			),
			VedleggDto(
				tittel = "Vedlegg9", label = "Vedlegg9",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = false,
				opplastingsStatus = OpplastingsStatusDto.LevertDokumentasjonTidligere, opprettetdato = OffsetDateTime.MIN,
				opplastingsValgKommentarLedetekst = "Forklar i hvilken sammenheng du leverte denne dokumentasjonen til NAV",
				opplastingsValgKommentar = "Jeg leverte denne $formFeed for ett år siden i forbindelse nmed en ${lineFeed}søknad om støtte til barnepass"
			),
			VedleggDto(
				tittel = "Vedlegg10", label = "Vedlegg10",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = false,
				opplastingsStatus = OpplastingsStatusDto.LevertDokumentasjonTidligere, opprettetdato = OffsetDateTime.MIN,
				opplastingsValgKommentarLedetekst = "Forklar i hvilken sammenheng du leverte denne dokumentasjonen til NAV",
				opplastingsValgKommentar = "Jeg leverte denne for ett år siden$tab$backspace i forbindelse med en søknad om støtte til barnepass.$lineFeed Det er ingen endring i forholdene siden dette ble levert. Den dokumentasjonen er derfor fortsatt relevant."
			),

			)
		return DokumentSoknadDto(
			brukerId = brukerid, skjemanr = skjemanr, tittel = tittel, tema = "TMA",
			status = SoknadsStatusDto.Innsendt, innsendtDato = OffsetDateTime.now(),
			spraak = spraak,
			innsendingsId = UUID.randomUUID().toString(), opprettetDato = opprettetDato, vedleggsListe = vedleggDtos
		)
	}

	private fun lagEttersendingsSoknadForTesting(tittel: String, spraak: String? = "nb_NO"): DokumentSoknadDto {
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
				opplastingsStatus = OpplastingsStatusDto.Innsendt,
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
				opplastingsStatus = OpplastingsStatusDto.Innsendt,
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
				opplastingsStatus = OpplastingsStatusDto.Innsendt,
				opprettetdato = OffsetDateTime.now(),
				innsendtdato = OffsetDateTime.now()
			),
			VedleggDto(
				tittel = "Vedlegg3", label = "Vedlegg3, NAV 08-36.04",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.SendSenere, opprettetdato = OffsetDateTime.MIN
			),
			VedleggDto(
				tittel = "Vedlegg4", label = "Vedlegg4",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = false,
				opplastingsStatus = OpplastingsStatusDto.SendesAvAndre, opprettetdato = OffsetDateTime.MIN
			)
		)
		return DokumentSoknadDto(
			brukerId = brukerid, skjemanr = skjemanr, tittel = tittel, tema = "TMA",
			spraak = spraak,
			status = SoknadsStatusDto.Innsendt, innsendtDato = OffsetDateTime.now(),
			innsendingsId = UUID.randomUUID().toString(), ettersendingsId = UUID.randomUUID().toString(),
			opprettetDato = opprettetDato, vedleggsListe = vedleggDtos,
			forsteInnsendingsDato = OffsetDateTime.MIN
		)

	}

	private fun lagEttersendingsSoknadAltInnsendt(tittel: String, spraak: String? = "nb_NO"): DokumentSoknadDto {
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
				opplastingsStatus = OpplastingsStatusDto.Innsendt,
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
				opplastingsStatus = OpplastingsStatusDto.Innsendt,
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
				opplastingsStatus = OpplastingsStatusDto.Innsendt,
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
				opplastingsStatus = OpplastingsStatusDto.Innsendt,
				opprettetdato = OffsetDateTime.MIN,
				innsendtdato = OffsetDateTime.now()
			),
			VedleggDto(
				tittel = "Vedlegg4", label = "Vedlegg4",
				erHoveddokument = false, erVariant = false, erPdfa = true, erPakrevd = false,
				opplastingsStatus = OpplastingsStatusDto.SendesAvAndre, opprettetdato = OffsetDateTime.MIN
			)
		)
		return DokumentSoknadDto(
			brukerId = brukerid, skjemanr = skjemanr, tittel = tittel, tema = "TMA",
			spraak = spraak,
			status = SoknadsStatusDto.Innsendt, innsendtDato = OffsetDateTime.now(),
			innsendingsId = UUID.randomUUID().toString(), ettersendingsId = UUID.randomUUID().toString(),
			opprettetDato = opprettetDato, vedleggsListe = vedleggDtos,
			forsteInnsendingsDato = OffsetDateTime.MIN
		)

	}


}
