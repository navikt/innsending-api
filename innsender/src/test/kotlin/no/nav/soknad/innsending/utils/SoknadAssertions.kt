package no.nav.soknad.innsending.utils

import io.mockk.every
import io.mockk.slot
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.KvitteringsDto
import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.model.SkjemaDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.util.Constants
import no.nav.soknad.innsending.util.testpersonid
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.VedleggDtoTestBuilder
import org.junit.jupiter.api.Assertions
import kotlin.test.assertTrue


class SoknadAssertions {
	companion object {

		fun testOgSjekkOpprettingAvSoknad(
			soknadService: SoknadService,
			dokumentSoknadDto: DokumentSoknadDto
		): SkjemaDto {
			val skjemaDto = soknadService.opprettNySoknad(dokumentSoknadDto)

			Assertions.assertEquals(dokumentSoknadDto.brukerId, skjemaDto.brukerId)
			Assertions.assertEquals(dokumentSoknadDto.skjemanr, skjemaDto.skjemanr)
			Assertions.assertEquals(dokumentSoknadDto.spraak, skjemaDto.spraak)
			Assertions.assertTrue(skjemaDto.innsendingsId != null)
			Assertions.assertTrue(dokumentSoknadDto.vedleggsListe.filter { !it.erHoveddokument }.size == skjemaDto.vedleggsListe?.size ?: 0)
			return skjemaDto
		}

		fun testOgSjekkOpprettingAvSoknad(
			soknadService: SoknadService,
			vedleggsListe: List<String> = listOf(),
			brukerid: String = testpersonid,
			spraak: String = "nb_NO"
		): DokumentSoknadDto {
			val skjemanr = "NAV 55-00.60"
			val hoveddokument = VedleggDtoTestBuilder(
				vedleggsnr = skjemanr,
				erHoveddokument = true,
				mimetype = Mimetype.applicationSlashPdf,
				innsendtdato = null,
				formioId = null
			).build()
			val hoveddokumentVariant = VedleggDtoTestBuilder(
				vedleggsnr = skjemanr,
				erHoveddokument = true,
				erVariant = true,
				erPdfa = false,
				mimetype = Mimetype.applicationSlashJson,
				innsendtdato = null,
				formioId = null
			).build()
			val vedlegg = vedleggsListe.map { vedleggsnr ->
				VedleggDtoTestBuilder(
					vedleggsnr = vedleggsnr,
					erPdfa = false,
					erPakrevd = vedleggsnr != "N6",
					innsendtdato = null,
					skjemaurl = null
				).build()
			}
			val input = DokumentSoknadDtoTestBuilder(
				brukerId = brukerid,
				skjemanr = skjemanr,
				spraak = spraak,
				tema = "BID",
				vedleggsListe = listOf(hoveddokument, hoveddokumentVariant) + vedlegg
			).build()
			val skjemaDto = testOgSjekkOpprettingAvSoknad(soknadService, input)
			val dokumentSoknadDto = soknadService.hentSoknad(requireNotNull(skjemaDto.innsendingsId))

			Assertions.assertEquals(brukerid, dokumentSoknadDto.brukerId)
			Assertions.assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
			Assertions.assertEquals(spraak, dokumentSoknadDto.spraak)
			Assertions.assertTrue(dokumentSoknadDto.innsendingsId != null)
			Assertions.assertEquals(vedleggsListe.size + 2, dokumentSoknadDto.vedleggsListe.size)
			Assertions.assertTrue(dokumentSoknadDto.vedleggsListe.all { it.id != null })
			Assertions.assertEquals(vedleggsListe, dokumentSoknadDto.vedleggsListe.filter { !it.erHoveddokument }.map { it.vedleggsnr })

			return dokumentSoknadDto

		}

		fun testOgSjekkOpprettingAvSoknad(
			soknadService: SoknadService,
			vedleggsListe: List<String> = listOf()
		): DokumentSoknadDto {
			return testOgSjekkOpprettingAvSoknad(soknadService, vedleggsListe, testpersonid)
		}

		fun testOgSjekkInnsendingAvSoknad(
			soknadsmottakerAPI: MottakerInterface,
			dokumentSoknadDto: DokumentSoknadDto,
			innsendingService: InnsendingService
		): KvitteringsDto {

			val soknad = slot<DokumentSoknadDto>()
			val vedleggDtos2 = slot<List<VedleggDto>>()
			every { soknadsmottakerAPI.sendInnSoknad(capture(soknad), capture(vedleggDtos2), any(), any()) } returns Unit

			val (kvitteringsDto) = innsendingService.forberedSoknadInnsending(dokumentSoknadDto)
			innsendingService.sendInnForArkivering(dokumentSoknadDto.innsendingsId!!)
			Assertions.assertTrue(soknad.isCaptured)
			Assertions.assertTrue(soknad.captured.innsendingsId == dokumentSoknadDto.innsendingsId)
			Assertions.assertTrue(vedleggDtos2.isCaptured)
			Assertions.assertTrue(vedleggDtos2.captured.filter { it.vedleggsnr == Constants.KVITTERINGS_NR }.isNotEmpty())
			val hoveddokumentVariant = vedleggDtos2.captured.filter { it.erVariant && it.erVariant }.toList()
			if (hoveddokumentVariant != null && hoveddokumentVariant.isNotEmpty()) {
				assertTrue { hoveddokumentVariant.first().mimetype == Mimetype.applicationSlashXml || hoveddokumentVariant.first().mimetype == Mimetype.applicationSlashJson }
			}

			Assertions.assertTrue(kvitteringsDto.innsendingsId == dokumentSoknadDto.innsendingsId)

			return kvitteringsDto
		}

		fun testOgSjekkOpprettingAvSoknad(
			soknadService: SoknadService,
			vedleggsListe: List<String> = listOf(),
			brukerid: String = testpersonid
		): DokumentSoknadDto {
			return testOgSjekkOpprettingAvSoknad(soknadService, vedleggsListe, brukerid, "nb_NO")
		}
	}
}
