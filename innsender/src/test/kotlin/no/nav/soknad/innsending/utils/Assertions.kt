package no.nav.soknad.innsending.utils

import io.mockk.every
import io.mockk.slot
import no.nav.soknad.innsending.consumerapis.soknadsfillager.FillagerInterface
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.KvitteringsDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.service.InnsendingService
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.util.testpersonid
import org.junit.jupiter.api.Assertions

private val defaultSkjemanr = "NAV 55-00.60"

fun testOgSjekkOpprettingAvSoknad(
	soknadService: SoknadService,
	vedleggsListe: List<String> = listOf(),
	brukerid: String = testpersonid
): DokumentSoknadDto {
	return testOgSjekkOpprettingAvSoknad(soknadService, vedleggsListe, brukerid, "nb_NO")
}

fun testOgSjekkOpprettingAvSoknad(
	soknadService: SoknadService,
	vedleggsListe: List<String> = listOf(),
	brukerid: String = testpersonid,
	spraak: String = "nb_NO"
): DokumentSoknadDto {
	val skjemanr = defaultSkjemanr
	val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak, vedleggsListe)

	Assertions.assertEquals(brukerid, dokumentSoknadDto.brukerId)
	Assertions.assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
	Assertions.assertEquals(spraak, dokumentSoknadDto.spraak)
	Assertions.assertTrue(dokumentSoknadDto.innsendingsId != null)
	Assertions.assertTrue(dokumentSoknadDto.vedleggsListe.size == vedleggsListe.size + 1)

	return dokumentSoknadDto

}

fun testOgSjekkOpprettingAvSoknad(
	soknadService: SoknadService,
	vedleggsListe: List<String> = listOf()
): DokumentSoknadDto {
	return testOgSjekkOpprettingAvSoknad(soknadService, vedleggsListe, testpersonid)
}

fun testOgSjekkInnsendingAvSoknad(
	fillagerAPI: FillagerInterface,
	soknadsmottakerAPI: MottakerInterface,
	dokumentSoknadDto: DokumentSoknadDto,
	innsendingService: InnsendingService
): KvitteringsDto {
	val vedleggDtos = slot<List<VedleggDto>>()
	every { fillagerAPI.lagreFiler(dokumentSoknadDto.innsendingsId!!, capture(vedleggDtos)) } returns Unit

	val soknad = slot<DokumentSoknadDto>()
	val vedleggDtos2 = slot<List<VedleggDto>>()
	every { soknadsmottakerAPI.sendInnSoknad(capture(soknad), capture(vedleggDtos2)) } returns Unit

	val kvitteringsDto = innsendingService.sendInnSoknad(dokumentSoknadDto)

	Assertions.assertTrue(vedleggDtos.isCaptured)
	Assertions.assertTrue(vedleggDtos.captured.isNotEmpty())

	Assertions.assertTrue(soknad.isCaptured)
	Assertions.assertTrue(soknad.captured.innsendingsId == dokumentSoknadDto.innsendingsId)

	Assertions.assertTrue(kvitteringsDto.innsendingsId == dokumentSoknadDto.innsendingsId)

	return kvitteringsDto
}
