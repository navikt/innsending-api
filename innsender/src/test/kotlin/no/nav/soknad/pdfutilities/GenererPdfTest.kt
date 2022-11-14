package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.utils.writeBytesToFile
import org.junit.Test
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.assertEquals

class GenererPdfTest {

	@Test
	fun verifiserGenereringAvKvitteringsPdf_medSpesialtegn() {
		val tittel = "Hovedskjema fra Nuńes 	til NAV 06-08.01"
		val soknad = lagSoknadForTesting(tittel)

		val sammensattnavn = "śander Ełmer"
		val kvittering = PdfGenerator().lagKvitteringsSide(soknad, sammensattnavn, soknad.vedleggsListe, emptyList())

		//writeBytesToFile(kvittering, "./delme.pdf")

		assertEquals(1, AntallSider().finnAntallSider(kvittering))

	}


	@Test
	fun verifiserGenereringAvKvitteringsPdf() {
		val tittel = "Hovedskjema fra Nunes til NAV 06-08.01"

		val soknad = lagSoknadForTesting(tittel)

		val sammensattnavn = "Fornavn Elmer"
		val kvittering = PdfGenerator().lagKvitteringsSide(soknad, sammensattnavn, soknad.vedleggsListe, emptyList())

		//writeBytesToFile(kvittering, "./delme2.pdf")

		assertEquals(1, AntallSider().finnAntallSider(kvittering))

	}

	private fun lagSoknadForTesting(tittel: String): DokumentSoknadDto {
		val brukerid = "20128012345"
		val skjemanr = "NAV 06-08.01"
		val vedleggDtos = listOf(
			VedleggDto(tittel=tittel, label=tittel,
				erHoveddokument = true, erVariant=false, erPdfa=true, erPakrevd = true,
				opplastingsStatus = OpplastingsStatusDto.innsendt, opprettetdato= OffsetDateTime.MIN))
		return DokumentSoknadDto(brukerId = brukerid, skjemanr=skjemanr, tittel=tittel, tema="TMA",
			status=SoknadsStatusDto.innsendt, innsendtDato = OffsetDateTime.now(),
			innsendingsId = UUID.randomUUID().toString(), opprettetDato = OffsetDateTime.now(), vedleggsListe = vedleggDtos
		)

	}

}
