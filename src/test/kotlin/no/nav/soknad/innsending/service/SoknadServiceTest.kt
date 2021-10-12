package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.repository.OpplastingsStatus
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.SoknadsStatus
import no.nav.soknad.innsending.repository.VedleggRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.*

@SpringBootTest
class SoknadServiceTest {

	@Autowired
	private lateinit var soknadRepository: SoknadRepository

	@Autowired
	private lateinit var vedleggRepository: VedleggRepository

	@Autowired
	private lateinit var skjemaService: HentSkjemaDataConsumer

	@AfterEach
	fun ryddOpp() {
		vedleggRepository.deleteAll()
		soknadRepository.deleteAll()
	}

	@Test
	fun opprettSoknadGittSkjemanr() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository)

		val brukerid = "12345678901"
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak, null)

		assertEquals(brukerid, dokumentSoknadDto.brukerId)
		assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
		assertEquals(spraak, dokumentSoknadDto.spraak)
	}

	@Test
	fun opprettSoknadGittSoknadDokument() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository)
		val brukerid = "12345678901"
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val tittel = "Tittel"
		val tema = "tema"
		val dokumentSoknadDto = lagDokumentSoknad(brukerid, skjemanr, spraak, tittel, tema)

		val lagretDokumentSoknadDto = soknadService.opprettEllerOppdaterSoknad(dokumentSoknadDto)

		assertNotNull(lagretDokumentSoknadDto.id)
		assertNotNull(lagretDokumentSoknadDto.behandlingsId)
		assertEquals(brukerid, lagretDokumentSoknadDto.brukerId)
		assertTrue(lagretDokumentSoknadDto.vedleggsListe.isNotEmpty())
	}

	@Test
	fun hentOpprettetSoknadDokument() {

		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository)
		val brukerid = "12345678901"
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak, null)

		val dokumentSoknadDtoHentet = soknadService.hentSoknad(dokumentSoknadDto.id!!)

		assertEquals(dokumentSoknadDto.id, dokumentSoknadDtoHentet.id)
	}

	@Test
	fun hentOpprettetVedlegg() {

		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository)
		val brukerid = "12345678901"
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak, null)

		assertFalse(dokumentSoknadDto.vedleggsListe.isEmpty())

		val vedleggDto = soknadService.hentVedlegg(dokumentSoknadDto.vedleggsListe[0].id!!)

		assertEquals(vedleggDto.id, dokumentSoknadDto.vedleggsListe[0].id!!)
	}

	@Test
	fun slettOpprettetSoknadDokument() {

		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository)
		val brukerid = "12345678901"
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak, null)
		soknadService.slettSoknad(dokumentSoknadDto.id!!)

		assertThrows<Exception> {
			soknadService.hentSoknad(dokumentSoknadDto.id!!)
		}
	}

	@Test
	fun oppdaterVedlegg() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository)

		val brukerid = "12345678901"
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak, null)

		assertEquals(brukerid, dokumentSoknadDto.brukerId)
		assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
		assertEquals(spraak, dokumentSoknadDto.spraak)

		val oppdatertVedleggDto = lastOppDokumentTilVedlegg(dokumentSoknadDto.vedleggsListe[0])

		val oppdatertVedleggDtoDb = soknadService.lagreVedlegg(oppdatertVedleggDto, dokumentSoknadDto.id!!)

		assertEquals(oppdatertVedleggDtoDb.id, oppdatertVedleggDto.id)
		assertEquals(oppdatertVedleggDtoDb.erHoveddokument, oppdatertVedleggDtoDb.erHoveddokument)
	}

	@Test
	fun oppdaterSoknadOgVedlegg() {
		val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository)

		val brukerid = "12345678901"
		val skjemanr = "NAV 95-00.11"
		val spraak = "no"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak, null)

		assertEquals(brukerid, dokumentSoknadDto.brukerId)
		assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
		assertEquals(spraak, dokumentSoknadDto.spraak)

		val dokumentSoknadDtoOppdatert = oppdaterDokumentSoknad(dokumentSoknadDto)

		val dokumentSoknadDtoOppdatertDb = soknadService.opprettEllerOppdaterSoknad(dokumentSoknadDtoOppdatert)

		assertEquals(dokumentSoknadDtoOppdatertDb.id, dokumentSoknadDtoOppdatert.id)
		assertEquals(dokumentSoknadDtoOppdatertDb.vedleggsListe[0].id, dokumentSoknadDtoOppdatert.vedleggsListe[0].id)
		assertEquals(dokumentSoknadDtoOppdatertDb.vedleggsListe[0].erHoveddokument, dokumentSoknadDtoOppdatert.vedleggsListe[0].erHoveddokument)
	}

	private fun lagDokumentSoknad(brukerId: String, skjemanr: String, spraak: String, tittel: String, tema: String): DokumentSoknadDto {
			val vedleggDtoPdf = VedleggDto(null, skjemanr, tittel, UUID.randomUUID().toString(), "application/pdf",
					getBytesFromFile("/litenPdf.pdf"), true, erVariant = false, true, OpplastingsStatus.LastetOpp,  LocalDateTime.now())
			val vedleggDtoJson = VedleggDto(null, skjemanr, tittel, UUID.randomUUID().toString(),"application/json",
					getBytesFromFile("/sanity.json"), true, erVariant = true, false, OpplastingsStatus.LastetOpp, LocalDateTime.now())

		val vedleggDtoList = listOf(vedleggDtoPdf, vedleggDtoJson)
		return DokumentSoknadDto(null, null, null, brukerId, skjemanr, tittel, tema, spraak, null,
			SoknadsStatus.Opprettet, LocalDateTime.now(), LocalDateTime.now(), null, vedleggDtoList)
	}

	private fun oppdaterDokumentSoknad(dokumentSoknadDto: DokumentSoknadDto): DokumentSoknadDto {
		val vedleggDto = lastOppDokumentTilVedlegg(dokumentSoknadDto.vedleggsListe[0])
		return DokumentSoknadDto(dokumentSoknadDto.id, dokumentSoknadDto.behandlingsId, dokumentSoknadDto.ettersendingsId,
			dokumentSoknadDto.brukerId, dokumentSoknadDto.skjemanr, dokumentSoknadDto.tittel, dokumentSoknadDto.tema,
			dokumentSoknadDto.spraak, dokumentSoknadDto.skjemaurl, SoknadsStatus.Opprettet, dokumentSoknadDto.opprettetDato, LocalDateTime.now(),
			null, listOf(vedleggDto))
	}

	private fun lastOppDokumentTilVedlegg(vedleggDto: VedleggDto) =
		VedleggDto(vedleggDto.id, vedleggDto.vedleggsnr, vedleggDto.tittel, UUID.randomUUID().toString(),
			"application/pdf", getBytesFromFile("/litenPdf.pdf"), true, erVariant = false,
			true, OpplastingsStatus.LastetOpp, LocalDateTime.now())

	private fun getBytesFromFile(path: String): ByteArray {
		val resourceAsStream = SoknadServiceTest::class.java.getResourceAsStream(path)
		val outputStream = ByteArrayOutputStream()
		resourceAsStream.use { input ->
			outputStream.use { output ->
				input!!.copyTo(output)
			}
		}
		return outputStream.toByteArray()
	}
}
