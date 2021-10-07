package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.repository.OpplastingsStatus
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.SoknadsStatus
import no.nav.soknad.innsending.repository.VedleggRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.*

@SpringBootTest
internal class SoknadServiceTest {

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
        val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository )

        val brukerid = "12345678901"
        val skjemanr = "NAV 95-00.11"
        val spraak = "no"
        val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

        assertTrue(dokumentSoknadDto != null)

    }

    @Test
    fun opprettSoknadGittSoknadDokument() {
        val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository )
        val brukerid = "12345678901"
        val skjemanr = "NAV 95-00.11"
        val spraak = "no"
        val tittel = "Tittel"
        val tema = "tema"
        val dokumentSoknadDto = lagDokumentSoknad(brukerid, skjemanr, spraak, tittel, tema)

        val lagretDokumentSoknadDto = soknadService.opprettEllerOppdaterSoknad(dokumentSoknadDto)

        assertTrue(lagretDokumentSoknadDto != null)
        assertTrue(lagretDokumentSoknadDto.id != null)
        assertTrue(lagretDokumentSoknadDto.behandlingsId != null)
        assertTrue(lagretDokumentSoknadDto.brukerId.equals(brukerid))
        assertTrue(!lagretDokumentSoknadDto.vedleggsListe.isNullOrEmpty() && lagretDokumentSoknadDto.vedleggsListe.stream().findFirst().isPresent)

    }

    @Test
    fun hentOpprettetSoknadDokument() {

        val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository )
        val brukerid = "12345678901"
        val skjemanr = "NAV 95-00.11"
        val spraak = "no"
        val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

        val dokumentSoknadDtoHentet = soknadService.hentSoknad(dokumentSoknadDto.id!!)

        assertTrue(dokumentSoknadDto.id == dokumentSoknadDtoHentet.id)
    }
    @Test

    fun hentOpprettetVedlegg() {

        val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository )
        val brukerid = "12345678901"
        val skjemanr = "NAV 95-00.11"
        val spraak = "no"
        val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

        assertTrue(dokumentSoknadDto != null && !dokumentSoknadDto.vedleggsListe.isNullOrEmpty())

        val vedleggDto = soknadService.hentVedlegg(dokumentSoknadDto.vedleggsListe.get(0).id!!)

        assertTrue(vedleggDto != null && vedleggDto.id == dokumentSoknadDto.vedleggsListe.get(0).id!!)
    }

    @Test
    fun slettOpprettetSoknadDokument() {

        val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository )
        val brukerid = "12345678901"
        val skjemanr = "NAV 95-00.11"
        val spraak = "no"
        val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)
        soknadService.slettSoknad(dokumentSoknadDto.id!!)

        org.junit.jupiter.api.assertThrows<Exception> {
            soknadService.hentSoknad(dokumentSoknadDto.id!!)
        }

    }

    @Test
    fun oppdaterVedlegg() {
        val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository )

        val brukerid = "12345678901"
        val skjemanr = "NAV 95-00.11"
        val spraak = "no"
        val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

        assertTrue(dokumentSoknadDto != null)

        val oppdatertVedleggDto = lastOppDokumentTilVedlegg(dokumentSoknadDto.vedleggsListe.get(0))

        val oppdatertVedleggDtoDb = soknadService.lagreVedlegg(oppdatertVedleggDto, dokumentSoknadDto.id!!)

        assertTrue(oppdatertVedleggDtoDb != null)
        assertTrue(oppdatertVedleggDtoDb.id == oppdatertVedleggDto.id)
        assertTrue(oppdatertVedleggDtoDb.erHoveddokument == oppdatertVedleggDtoDb.erHoveddokument)

    }

    @Test
    fun oppdaterSoknadOgVedlegg() {
        val soknadService = SoknadService(skjemaService, soknadRepository, vedleggRepository )

        val brukerid = "12345678901"
        val skjemanr = "NAV 95-00.11"
        val spraak = "no"
        val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

        assertTrue(dokumentSoknadDto != null)

        val dokumentSoknadDtoOppdatert = oppdaterDokumentSoknad(dokumentSoknadDto)

        val dokumentSoknadDtoOppdatertDb = soknadService.opprettEllerOppdaterSoknad(dokumentSoknadDtoOppdatert)

        assertTrue(dokumentSoknadDtoOppdatertDb != null)
        assertTrue(dokumentSoknadDtoOppdatertDb.id == dokumentSoknadDtoOppdatert.id)
        assertTrue(dokumentSoknadDtoOppdatertDb.vedleggsListe.get(0).id == dokumentSoknadDtoOppdatert.vedleggsListe.get(0).id)
        assertTrue(dokumentSoknadDtoOppdatertDb.vedleggsListe.get(0).erHoveddokument == dokumentSoknadDtoOppdatert.vedleggsListe.get(0).erHoveddokument)

    }

    fun lagDokumentSoknad(brukerId: String, skjemanr: String, spraak: String, tittel: String, tema: String): DokumentSoknadDto {
        val vedleggDtoPdf = VedleggDto(null, skjemanr, tittel, null, UUID.randomUUID().toString(), "application/pdf"
            ,getBytesFromFile("/litenPdf.pdf"), true, false, true, OpplastingsStatus.LastetOpp,  LocalDateTime.now())
        val vedleggDtoJson = VedleggDto(null, skjemanr, tittel, null, UUID.randomUUID().toString(),"application/json"
            , getBytesFromFile("/sanity.json"), true, erVariant = true, false, OpplastingsStatus.LastetOpp, LocalDateTime.now())

        val vedleggDtoList = listOf(vedleggDtoPdf, vedleggDtoJson)
        return DokumentSoknadDto(null, null, null, brukerId, skjemanr, tittel, tema, spraak, SoknadsStatus.Opprettet, LocalDateTime.now(), LocalDateTime.now(), null, vedleggDtoList )
    }

    fun oppdaterDokumentSoknad(dokumentSoknadDto: DokumentSoknadDto): DokumentSoknadDto {
        val vedleggDto = lastOppDokumentTilVedlegg(dokumentSoknadDto.vedleggsListe.get(0))
        return DokumentSoknadDto(dokumentSoknadDto.id, dokumentSoknadDto.behandlingsId, dokumentSoknadDto.ettersendingsId, dokumentSoknadDto.brukerId, dokumentSoknadDto.skjemanr, dokumentSoknadDto.tittel, dokumentSoknadDto.tema
            , dokumentSoknadDto.spraak, SoknadsStatus.Opprettet, dokumentSoknadDto.opprettetDato, LocalDateTime.now(), null, listOf(vedleggDto) )
    }

    fun lastOppDokumentTilVedlegg(vedleggDto: VedleggDto): VedleggDto {
        return VedleggDto(vedleggDto.id, vedleggDto.vedleggsnr, vedleggDto.tittel, vedleggDto.skjemaurl, UUID.randomUUID().toString(), "application/pdf"
            ,getBytesFromFile("/litenPdf.pdf"), true, false, true, OpplastingsStatus.LastetOpp,  LocalDateTime.now())
    }

    fun getBytesFromFile(path: String): ByteArray {
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