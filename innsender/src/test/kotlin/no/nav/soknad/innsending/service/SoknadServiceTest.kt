package no.nav.soknad.innsending.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.PublisherConfig
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.kafka.KafkaPublisher
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto
import no.nav.soknad.innsending.consumerapis.skjema.HentSkjemaDataConsumer
import no.nav.soknad.innsending.consumerapis.skjema.SkjemaClient
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.FilRepository
import no.nav.soknad.innsending.repository.HendelseRepository
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.VedleggRepository
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.HendelseType
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.util.mapping.mapTilOffsetDateTime
import no.nav.soknad.innsending.util.models.hovedDokument
import no.nav.soknad.innsending.util.models.hovedDokumentVariant
import no.nav.soknad.innsending.util.models.hoveddokument
import no.nav.soknad.innsending.util.models.hoveddokumentVariant
import no.nav.soknad.innsending.util.testpersonid
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.SoknadAssertions
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import no.nav.soknad.innsending.utils.builders.VedleggDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.InnsendtVedleggDtoTestBuilder
import no.nav.soknad.innsending.utils.builders.ettersending.OpprettEttersendingTestBuilder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*


class SoknadServiceTest : ApplicationTest() {

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var innsenderMetrics: InnsenderMetrics

	@Autowired
	private lateinit var soknadService: SoknadService

	@Autowired
	private lateinit var hentSkjemaDataConsumer: HentSkjemaDataConsumer

	@Autowired
	private lateinit var soknadRepository: SoknadRepository

	@Autowired
	private lateinit var hendelseRepository: HendelseRepository

	@Autowired
	private lateinit var vedleggRepository: VedleggRepository

	@Autowired
	private lateinit var tilleggstonadService: TilleggsstonadService

	@Autowired
	private lateinit var filRepository: FilRepository

	@Autowired
	private lateinit var vedleggService: VedleggService

	@Autowired
	private lateinit var ettersendingService: EttersendingService

	@Autowired
	private lateinit var filService: FilService

	@Autowired
	private lateinit var exceptionHelper: ExceptionHelper

	@Autowired
	private lateinit var restConfig: RestConfig

	private val brukernotifikasjonPublisher = mockk<BrukernotifikasjonPublisher>()

	private val hentSkjemaData = mockk<SkjemaClient>()

	private val soknadsmottakerAPI = mockk<MottakerInterface>()

	private val pdlInterface = mockk<PdlInterface>()

	@MockkBean
	private lateinit var subjectHandler: SubjectHandlerInterface

	@MockkBean
	private lateinit var kafkaPublisher: KafkaPublisher

	@Autowired
	private lateinit var publisherConfig: PublisherConfig

	private val defaultSkjemanr = "NAV 55-00.60"

	@BeforeEach
	fun setup() {
		every { hentSkjemaData.hent() } returns hentSkjemaDataConsumer.initSkjemaDataFromDisk()
		every { brukernotifikasjonPublisher.soknadStatusChange(any()) } returns true
		every { pdlInterface.hentPersonData(any()) } returns PersonDto("1234567890", "Kan", null, "Søke")
		every { subjectHandler.getClientId() } returns "application"
		every { kafkaPublisher.publishToKvitteringsSide(any(), any())} returns Unit
	}


	@AfterEach
	fun ryddOpp() {
		filRepository.deleteAll()
		vedleggRepository.deleteAll()
		soknadRepository.deleteAll()
		hendelseRepository.deleteAll()
	}


	private fun lagInnsendingService(soknadService: SoknadService): InnsendingService = InnsendingService(
		soknadService = soknadService,
		repo = repo,
		vedleggService = vedleggService,
		tilleggstonadService = tilleggstonadService,
		ettersendingService = ettersendingService,
		filService = filService,
		brukernotifikasjonPublisher = brukernotifikasjonPublisher,
		innsenderMetrics = innsenderMetrics,
		exceptionHelper = exceptionHelper,
		soknadsmottakerAPI = soknadsmottakerAPI,
		restConfig = restConfig,
		pdlInterface = pdlInterface,
		kafkaPublisher = kafkaPublisher,
		publisherConfig = publisherConfig
	)

	@Test
	fun opprettSoknadGittSkjemanr() {
		val brukerid = testpersonid
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

		assertEquals(brukerid, dokumentSoknadDto.brukerId)
		assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
		assertEquals(spraak, dokumentSoknadDto.spraak)
		assertNotNull(dokumentSoknadDto.innsendingsId)

		val hendelseDbDatas = hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(dokumentSoknadDto.innsendingsId!!)
		assertTrue(hendelseDbDatas.isNotEmpty())
		assertEquals(HendelseType.Opprettet, hendelseDbDatas[0].hendelsetype)
	}

	@Test
	fun opprettSoknadGittSkjemanrOgIkkeStottetSprak() {
		val brukerid = testpersonid
		val skjemanr = defaultSkjemanr
		val skjemaTittel_en = hentSkjemaDataConsumer.hentSkjemaEllerVedlegg(defaultSkjemanr, "en").tittel
		val spraak = "fr"
		val dokumentSoknadDto = soknadService.opprettSoknad(brukerid, skjemanr, spraak)

		assertEquals(brukerid, dokumentSoknadDto.brukerId)
		assertEquals(skjemanr, dokumentSoknadDto.skjemanr)
		assertEquals(spraak, dokumentSoknadDto.spraak) // Beholder ønsket språk
		assertEquals(skjemaTittel_en, dokumentSoknadDto.tittel) // engelsk backup for fransk
		assertNotNull(dokumentSoknadDto.innsendingsId)

		val hendelseDbDatas = hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(dokumentSoknadDto.innsendingsId!!)
		assertTrue(hendelseDbDatas.isNotEmpty())
		assertEquals(HendelseType.Opprettet, hendelseDbDatas[0].hendelsetype)
	}


	@Test
	fun opprettSoknadGittUkjentSkjemanrKasterException() {
		val brukerid = testpersonid
		val skjemanr = "NAV XX-00.11"
		val spraak = "nb_NO"
		val exception = assertThrows(
			ResourceNotFoundException::class.java,
			{ soknadService.opprettSoknad(brukerid, skjemanr, spraak) },
			"ResourceNotFoundException was expected"
		)

		assertEquals("Skjema med id = $skjemanr ikke funnet. Ikke funnet i skjema listen", exception.message)
	}

	@Test
	fun opprettSoknadGittSoknadDokument() {
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf())

		assertNotNull(dokumentSoknadDto)
	}

	@Test
	fun testAtFilerIkkeSlettesVedInnsending() {
		val innsendingService = lagInnsendingService(soknadService)
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		// Laster opp skjema (hoveddokumentet) til soknaden
		filService.lagreFil(
			dokumentSoknadDto,
			Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument })
		)

		// laster opp fil til vedlegget
		val lagretFilForVedlegg =
			filService.lagreFil(dokumentSoknadDto, Hjelpemetoder.lagFilDtoMedFil(dokumentSoknadDto.vedleggsListe.first {
				!it.erHoveddokument && it.vedleggsnr.equals(
					"W1",
					true
				)
			}))

		assertTrue(lagretFilForVedlegg.id != null)
		assertTrue(lagretFilForVedlegg.vedleggsid == dokumentSoknadDto.vedleggsListe.first {
			!it.erHoveddokument && it.vedleggsnr.equals(
				"W1",
				true
			)
		}.id)

		// Sender inn søknaden
		val kvitteringsDto =
			SoknadAssertions.testOgSjekkInnsendingAvSoknad(
				soknadsmottakerAPI,
				dokumentSoknadDto,
				innsendingService
			)
		assertTrue(kvitteringsDto.hoveddokumentRef != null)
		assertTrue(kvitteringsDto.innsendteVedlegg!!.isNotEmpty())
		assertTrue(kvitteringsDto.skalEttersendes!!.isEmpty())

		// Test at filen til hoveddokumentet ikke er slettet
		val filerForHovedDokument = filService.hentFiler(
			dokumentSoknadDto,
			dokumentSoknadDto.innsendingsId!!,
			(dokumentSoknadDto.vedleggsListe.first { it.erHoveddokument }).id!!,
			true
		)
		assertTrue(filerForHovedDokument.isNotEmpty())

		// Test at filen til vedlegget ikke er slettet
		val filerForVedlegg = filService.hentFiler(
			dokumentSoknadDto,
			dokumentSoknadDto.innsendingsId!!,
			lagretFilForVedlegg.vedleggsid,
			true
		)
		assertTrue(filerForVedlegg.isNotEmpty())
	}


	@Test
	fun opprettSoknadForettersendingAvVedleggGittArkivertSoknadTest_MedUkjentSkjemanr() {
		val vedleggsnr = "NAV 08-09.10"
		val arkiverteVedlegg: List<InnsendtVedleggDto> = listOf(
			InnsendtVedleggDto(
				vedleggsnr = vedleggsnr,
				tittel = "Søknad om å beholde sykepenger under opphold i utlandet"
			)
		)

		val arkivertSoknad = AktivSakDto(
			"NAV 08-07.04D",
			"Søknad om Sykepenger",
			"SYK",
			mapTilOffsetDateTime(LocalDateTime.now(), -10L),
			ettersending = false,
			innsendingsId = UUID.randomUUID().toString(),
			innsendtVedleggDtos = arkiverteVedlegg
		)

		val opprettEttersending =
			OpprettEttersendingTestBuilder().vedleggsListe(
				listOf(InnsendtVedleggDtoTestBuilder().vedleggsnr(vedleggsnr).build())
			).build()

		val ettersending = ettersendingService.createEttersendingFromArchivedSoknad(
			brukerId = "1234",
			archivedSoknad = arkivertSoknad,
			ettersending = opprettEttersending,
			forsteInnsendingsDato = null
		)

		assertNotNull(ettersending)
		assertEquals(1, ettersending.vedleggsListe.size)

		val vedlegg = ettersending.vedleggsListe.first()
		assertEquals(vedleggsnr, vedlegg.vedleggsnr)
		assertNotNull(vedlegg.innsendtdato)
		assertEquals(OpplastingsStatusDto.IkkeValgt, vedlegg.opplastingsStatus)

	}

	@Test
	fun hentOpprettetSoknadDokument() {
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"))

		val dokumentSoknadDtoHentet = soknadService.hentSoknad(dokumentSoknadDto.id!!)

		assertEquals(dokumentSoknadDto.id, dokumentSoknadDtoHentet.id)
	}

	@Test
	fun hentIkkeEksisterendeSoknadDokumentKasterFeil() {
		val exception = assertThrows(
			ResourceNotFoundException::class.java,
			{ soknadService.hentSoknad("9999") },
			"ResourceNotFoundException was expected"
		)

		assertEquals("Fant ikke søknad med innsendingsid 9999", exception.message)
	}

	@Test
	fun hentOpprettedeAktiveSoknadsDokument() {
		SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), testpersonid)
		SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), testpersonid)
		SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W2"), "12345678902")
		SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1"), "12345678903")

		val dokumentSoknadDtos = soknadService.hentAktiveSoknader(listOf(testpersonid, "12345678902"))

		assertTrue(dokumentSoknadDtos.filter { listOf(testpersonid, "12345678902").contains(it.brukerId) }.size == 3)
		assertTrue(dokumentSoknadDtos.none { listOf("12345678903").contains(it.brukerId) })
	}


	@Test
	fun slettOpprettetSoknadDokument() {
		val dokumentSoknadDto = SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf())

		slot<List<VedleggDto>>()

		soknadService.slettSoknadAvBruker(dokumentSoknadDto)

		/*
				assertTrue(slett.isCaptured)
				assertTrue(slett.captured.size == dokumentSoknadDto.vedleggsListe.size)
		*/
		assertThrows<Exception> {
			soknadService.hentSoknad(dokumentSoknadDto.id!!)
		}

		val hendelseDbDatas = hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(dokumentSoknadDto.innsendingsId!!)
		assertTrue(hendelseDbDatas.size == 2)
		assertEquals(HendelseType.SlettetPermanentAvBruker, hendelseDbDatas[1].hendelsetype)
	}

	@Test
	fun lesOppTeksterTest() {
		val prop = Properties()
		val inputStream = SoknadServiceTest::class.java.getResourceAsStream("/tekster/innholdstekster.properties")

		inputStream.use {
			prop.load(it)
		}
		assertTrue(!prop.isEmpty)
	}

	@Test
	fun opprettingAvSoknadVedKallFraFyllUt() {
		val tema = "HJE"
		val skjemanr = "NAV 10-07.04"
		val innsendingsId = soknadService.opprettNySoknad(lagDokumentSoknad(tema, skjemanr)).innsendingsId!!

		val soknad = soknadService.hentSoknad(innsendingsId)

		assertTrue(soknad.tema == tema && soknad.skjemanr == skjemanr)
		assertTrue(soknad.vedleggsListe.size == 2)
		assertTrue(soknad.vedleggsListe.any { it.erHoveddokument && !it.erVariant && it.opplastingsStatus == OpplastingsStatusDto.LastetOpp })

		val hendelseDbDatas = hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(soknad.innsendingsId!!)
		assertTrue(hendelseDbDatas.size == 1)
		assertEquals(HendelseType.Opprettet, hendelseDbDatas[0].hendelsetype)
	}

	@Test
	fun `Skal oppdatere søknad med ny data (språk og tittel)`() {
		// Gitt
		val tema = "HJE"
		val skjemanr = "NAV 10-07.04"
		val innsendingsId = soknadService.opprettNySoknad(lagDokumentSoknad(tema, skjemanr)).innsendingsId!!

		val oppdatertSpraak = "Nytt språk"
		val oppdatertTittel = "Ny tittel"

		val dokumentSoknad = Hjelpemetoder.lagDokumentSoknad(
			brukerId = testpersonid,
			skjemanr = skjemanr,
			spraak = oppdatertSpraak,
			tittel = oppdatertTittel,
			tema = tema,
		)

		// Når
		soknadService.updateSoknad(innsendingsId, dokumentSoknad)
		val oppdatertSoknad = soknadService.hentSoknad(innsendingsId)

		// Så
		assertEquals(oppdatertSpraak, oppdatertSoknad.spraak)
		assertEquals(oppdatertTittel, oppdatertSoknad.tittel)

		// og ingen ny hendelse registrert på søknad
		val hendelseDbDatas = hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(innsendingsId)
		assertEquals(2, hendelseDbDatas.size)
		assertEquals(HendelseType.Opprettet, hendelseDbDatas[0].hendelsetype)
		assertEquals(HendelseType.Endret, hendelseDbDatas[1].hendelsetype)

	}

	@Test
	fun `Should delete søknad from external application`() {
		// Given
		val applicationName = "application"
		val soknadDbData = SoknadDbDataTestBuilder(applikasjon = applicationName).build()
		repo.lagreSoknad(soknadDbData)

		val createdSoknad = soknadService.hentSoknad(soknadDbData.innsendingsid)

		// When
		soknadService.deleteSoknadFromExternalApplication(createdSoknad)

		// Then
		assertThrows<ResourceNotFoundException> {
			soknadService.hentSoknad(soknadDbData.innsendingsid)
		}
	}

	@Test
	fun `Should update utfylt søknad with new vedlegg`() {
		// Given
		val skjemanr = "NAV 10-07.04"

		val newSoknad = DokumentSoknadDtoTestBuilder(brukerId = testpersonid, skjemanr = skjemanr).build()
		val innsendingsId = soknadService.opprettNySoknad(newSoknad).innsendingsId!!

		val vedleggDto1 = VedleggDtoTestBuilder(erHoveddokument = false, vedleggsnr = "vedleggsnr1").build()
		val vedleggDto2 = VedleggDtoTestBuilder(erHoveddokument = false, vedleggsnr = "vedleggsnr2").build()

		val vedleggDtoList = listOf(newSoknad.hoveddokument!!, newSoknad.hoveddokumentVariant!!, vedleggDto1, vedleggDto2)

		val utfyltSoknad = DokumentSoknadDtoTestBuilder(
			brukerId = testpersonid,
			innsendingsId = innsendingsId,
			vedleggsListe = vedleggDtoList,
			skjemanr = skjemanr
		).build()

		// When
		soknadService.updateUtfyltSoknad(innsendingsId, utfyltSoknad)
		val updatedSoknad = soknadService.hentSoknad(innsendingsId)

		val files = updatedSoknad.vedleggsListe.flatMap {
			filService.hentFiler(
				updatedSoknad, innsendingsId,
				it.id!!, true
			)
		}

		// Then
		assertEquals(
			4,
			updatedSoknad.vedleggsListe.size,
			"Skal ha to vedlegg i den oppdaterte søknaden + hoveddokument og variant"
		)

		assertEquals(2, files.size, "Skal ha 2 filer lagret i databasen")

		assertTrue(
			updatedSoknad.vedleggsListe.any { it.vedleggsnr == vedleggDto1.vedleggsnr },
			"Skal ha vedlegg 1 i den oppdaterte søknaden"
		)
		assertTrue(
			updatedSoknad.vedleggsListe.any { it.vedleggsnr == vedleggDto2.vedleggsnr },
			"Skal ha vedlegg 2 i den oppdaterte søknaden"
		)


		// and no new ny hendelse registered on søknad
		val hendelseDbDatas = hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(innsendingsId)
		assertEquals(2, hendelseDbDatas.size)
		assertEquals(HendelseType.Opprettet, hendelseDbDatas[0].hendelsetype)
		assertEquals(HendelseType.Utfylt, hendelseDbDatas[1].hendelsetype)

	}

	@Test
	fun `Skal hente søknad for fyllut oppsummeringssiden med hovedokumentVariant fil`() {
		// Gitt
		val tema = "HJE"
		val skjemanr = "NAV 10-07.04"
		val dokumentSoknadDto = lagDokumentSoknad(tema, skjemanr)
		val innsendingsId = soknadService.opprettNySoknad(dokumentSoknadDto).innsendingsId!!

		// Når
		val soknad = soknadService.hentSoknadMedHoveddokumentVariant(innsendingsId)

		// Så
		val hoveddokumentVariant = soknad.vedleggsListe.hovedDokumentVariant
		val hoveddokument = soknad.vedleggsListe.hovedDokument

		assertNotNull(hoveddokumentVariant?.document, "Skal ha json filen")
		assertNull(hoveddokument?.document, "Skal ikke ha pdf filen")

		assertEquals(tema, soknad.tema, "Skal ha riktig tema")
		assertEquals(skjemanr, soknad.skjemanr, "Skal ha riktig skjemanr")

	}


	@Test
	fun testAutomatiskSlettingAvGamleSoknader() {
		val brukerid = testpersonid
		val skjemanr = defaultSkjemanr
		val spraak = "nb_NO"

		val dokumentSoknadDtoList = mutableListOf<DokumentSoknadDto>()
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak))
		dokumentSoknadDtoList.add(soknadService.opprettSoknad(brukerid, skjemanr, spraak))

		soknadService.slettGamleSoknader(1L)
		dokumentSoknadDtoList.forEach { assertEquals(soknadService.hentSoknad(it.id!!).status, SoknadsStatusDto.Opprettet) }

		soknadService.slettGamleSoknader(-1L)
		dokumentSoknadDtoList.forEach {
			assertEquals(
				SoknadsStatusDto.AutomatiskSlettet,
				soknadService.hentSoknad(it.id!!).status,
			)
		}

		// og hendelse for oppretting og seltting registrert på søknad
		val hendelseDbDatas =
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(dokumentSoknadDtoList[0].innsendingsId!!)
		assertTrue(hendelseDbDatas.size > 1)
		assertEquals(HendelseType.Opprettet, hendelseDbDatas[0].hendelsetype)
		assertEquals(HendelseType.SlettetAvSystem, hendelseDbDatas[1].hendelsetype)
	}

	@Test
	fun `Should delete soknader with skalSlettesDato in the past`() {
		// Given
		val oneDayAgo = DokumentSoknadDtoTestBuilder(skalslettesdato = OffsetDateTime.now().minusDays(1)).build()
		val tenDaysAgo = DokumentSoknadDtoTestBuilder(skalslettesdato = OffsetDateTime.now().minusDays(10)).build()
		val now = DokumentSoknadDtoTestBuilder(skalslettesdato = OffsetDateTime.now()).build()
		val tomorrow =
			DokumentSoknadDtoTestBuilder(skalslettesdato = OffsetDateTime.now().plusDays(1)).build()

		val soknadOneDayAgo = soknadService.opprettNySoknad(oneDayAgo)
		val soknadTenDaysAgo = soknadService.opprettNySoknad(tenDaysAgo)
		val soknadNow = soknadService.opprettNySoknad(now)
		val soknadTomorrow = soknadService.opprettNySoknad(tomorrow)

		// When
		soknadService.deleteSoknadBeforeCutoffDate(OffsetDateTime.now())

		// Then
		assertEquals(SoknadsStatusDto.AutomatiskSlettet, soknadService.hentSoknad(soknadOneDayAgo.innsendingsId!!).status)
		assertEquals(SoknadsStatusDto.AutomatiskSlettet, soknadService.hentSoknad(soknadTenDaysAgo.innsendingsId!!).status)
		assertEquals(SoknadsStatusDto.AutomatiskSlettet, soknadService.hentSoknad(soknadNow.innsendingsId!!).status)
		assertEquals(SoknadsStatusDto.Opprettet, soknadService.hentSoknad(soknadTomorrow.innsendingsId!!).status)
	}

	@Test
	fun `Should delete soknader with skalSlettesDato in the future`() {
		// Given
		val oneDayAgo = DokumentSoknadDtoTestBuilder(skalslettesdato = OffsetDateTime.now().minusDays(1)).build()
		val now = DokumentSoknadDtoTestBuilder(skalslettesdato = OffsetDateTime.now()).build()
		val tomorrow =
			DokumentSoknadDtoTestBuilder(skalslettesdato = OffsetDateTime.now().plusDays(1)).build()
		val tenDaysFromNow =
			DokumentSoknadDtoTestBuilder(skalslettesdato = OffsetDateTime.now().plusDays(10)).build()

		val soknadOneDayAgo = soknadService.opprettNySoknad(oneDayAgo)
		val soknadNow = soknadService.opprettNySoknad(now)
		val soknadTomorrow = soknadService.opprettNySoknad(tomorrow)
		val soknadTenDaysFromNow = soknadService.opprettNySoknad(tenDaysFromNow)

		// When
		soknadService.deleteSoknadBeforeCutoffDate(OffsetDateTime.now().plusDays(5))

		// Then
		assertEquals(SoknadsStatusDto.AutomatiskSlettet, soknadService.hentSoknad(soknadOneDayAgo.innsendingsId!!).status)
		assertEquals(SoknadsStatusDto.AutomatiskSlettet, soknadService.hentSoknad(soknadNow.innsendingsId!!).status)
		assertEquals(SoknadsStatusDto.AutomatiskSlettet, soknadService.hentSoknad(soknadTomorrow.innsendingsId!!).status)
		assertEquals(SoknadsStatusDto.Opprettet, soknadService.hentSoknad(soknadTenDaysFromNow.innsendingsId!!).status)
	}


	/**
	 *  A sent in application might have state archived (=Arkivert), archived failed (ArkiveringFeilet) or not yet handled (IkkeSatt).
	 *  All attachment files to an applications shall be deleted after a given time when sent in and archived
	 */
	@Test
	fun testAutomatiskSlettingAvFilerTilInnsendteSoknader() {

		val dokumentSoknadDtoList = mutableListOf<DokumentSoknadDto>()
		dokumentSoknadDtoList.add(SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1")))
		dokumentSoknadDtoList.add(SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1")))
		dokumentSoknadDtoList.add(SoknadAssertions.testOgSjekkOpprettingAvSoknad(soknadService, listOf("W1")))

		dokumentSoknadDtoList.forEach { it ->
			filService.lagreFil(
				it,
				Hjelpemetoder.lagFilDtoMedFil(it.vedleggsListe.first { it.erHoveddokument })
			)
		}
		dokumentSoknadDtoList.forEach { it ->
			filService.lagreFil(
				it,
				Hjelpemetoder.lagFilDtoMedFil(it.vedleggsListe.first { !it.erHoveddokument })
			)
		}

		slot<List<VedleggDto>>()

		val soknad = slot<DokumentSoknadDto>()
		val vedleggDtos2 = slot<List<VedleggDto>>()
		every { soknadsmottakerAPI.sendInnSoknad(capture(soknad), capture(vedleggDtos2)) } returns Unit

		val innsendingService = lagInnsendingService(soknadService)
		// Check that reciept is returned for each sent in application
		val kvitteringsDtoList = mutableListOf<KvitteringsDto>()
		dokumentSoknadDtoList.forEach { kvitteringsDtoList.add(innsendingService.sendInnSoknad(it)) }
		assertTrue(kvitteringsDtoList.size == dokumentSoknadDtoList.size)

		// Check that the applications attachments are kept after the applications are sent in
		dokumentSoknadDtoList.all {
			filService.hentFiler(
				it,
				it.innsendingsId!!,
				it.vedleggsListe.first { vedlegg -> vedlegg.erHoveddokument && !vedlegg.erVariant }.id!!
			).isNotEmpty()
		}

		// First archived, second archiving failed, third not handled
		simulateKafkaPolling(true, dokumentSoknadDtoList[0].innsendingsId!!)
		simulateKafkaPolling(false, dokumentSoknadDtoList[1].innsendingsId!!)

		// Delete attachment files for all sent in and archived applications
		soknadService.finnOgSlettArkiverteSoknader(-1, 100)

		assertThrows<Exception> {
			soknadService.hentSoknad(dokumentSoknadDtoList[0].innsendingsId!!)
		}
		assertNotNull(soknadService.hentSoknad(dokumentSoknadDtoList[1].innsendingsId!!))

		val hendelseDbDatasArkivert =
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(dokumentSoknadDtoList[0].innsendingsId!!)
		assertTrue(hendelseDbDatasArkivert.size == 4)
		assertEquals(HendelseType.Opprettet, hendelseDbDatasArkivert[0].hendelsetype)
		assertEquals(HendelseType.Innsendt, hendelseDbDatasArkivert[1].hendelsetype)
		assertEquals(HendelseType.Arkivert, hendelseDbDatasArkivert[2].hendelsetype)
		assertEquals(HendelseType.SlettetPermanentAvSystem, hendelseDbDatasArkivert[3].hendelsetype)

		val hendelseDbDatasArkiveringFeilet =
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(dokumentSoknadDtoList[1].innsendingsId!!)
		assertTrue(hendelseDbDatasArkiveringFeilet.size == 3)
		assertEquals(HendelseType.Opprettet, hendelseDbDatasArkiveringFeilet[0].hendelsetype)
		assertEquals(HendelseType.Innsendt, hendelseDbDatasArkiveringFeilet[1].hendelsetype)
		assertEquals(HendelseType.ArkiveringFeilet, hendelseDbDatasArkiveringFeilet[2].hendelsetype)

	}

	private fun simulateKafkaPolling(ok: Boolean, innsendingId: String) {
		val soknad = repo.hentSoknadDb(innsendingId)
		repo.oppdaterArkiveringsstatus(soknad, (if (ok) ArkiveringsStatus.Arkivert else ArkiveringsStatus.ArkiveringFeilet))
	}

	fun lagDokumentSoknad(tema: String, skjemanr: String): DokumentSoknadDto {
		return Hjelpemetoder.lagDokumentSoknad(testpersonid, skjemanr, "nb_NO", "Fullmaktsskjema", tema)
	}


}
