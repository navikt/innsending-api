package no.nav.soknad.innsending.rest.ekstern

import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.slot
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.InnsendingApiApplication
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.IdentDto
import no.nav.soknad.innsending.model.*
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.VedleggRepository
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.util.testpersonid
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.util.AopTestUtils
import org.junit.jupiter.api.Test
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.*
import java.lang.Thread.sleep
import java.time.LocalDateTime
import kotlin.test.assertNull


@SpringBootTest(
	webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
	properties = ["spring.main.allow-bean-definition-overriding=true"],
	classes = [InnsendingApiApplication::class]
)
@ExtendWith(SpringExtension::class)
class InternInitiertOppgaverTest: ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@SpykBean()
	lateinit var publisherInterface: PublisherInterface

	@SpykBean
	lateinit var pdlInterface: PdlInterface

	@Autowired
	lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var soknadRepository: SoknadRepository

	@Autowired
	private lateinit var vedleggRepository: VedleggRepository

	@LocalServerPort
	var serverPort: Int = 0

	private var testApi: ApiWebClient? = null
	private val api: ApiWebClient
		get() = testApi!!

	@BeforeEach
	fun setup() {
		testApi = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearAllMocks()
		vedleggRepository.deleteAll()
		soknadRepository.deleteAll()
	}

	@Test
	fun `Azure task links to the explicit user and not the fallback user's newer application`() {
		val brukerId = "10987654321"
		val skjemanr = "NAV 55-00.60"
		val pdl = AopTestUtils.getUltimateTargetObject<PdlInterface>(pdlInterface)
		every { pdl.hentPersonIdents(any()) } answers {
			listOf(IdentDto(firstArg(), "FOLKEREGISTERIDENT", false))
		}
		val ownApplication = repo.lagreSoknad(
			SoknadDbDataTestBuilder(
				brukerId = brukerId,
				skjemanr = skjemanr,
				status = SoknadsStatus.Innsendt,
				innsendtdato = LocalDateTime.now().minusDays(2),
			).build()
		)
		val otherApplication = repo.lagreSoknad(
			SoknadDbDataTestBuilder(
				brukerId = testpersonid,
				skjemanr = skjemanr,
				status = SoknadsStatus.Innsendt,
				innsendtdato = LocalDateTime.now().minusDays(1),
			).build()
		)

		val ettersending = opprettSoknad(brukerId, listOf("W1"), skjemanr, koblesTilEksisterendeSoknad = true)

		assertEquals(ownApplication.innsendingsid, ettersending.ettersendingsId)
		val stored = repo.hentSoknadDb(ettersending.innsendingsId!!)
		assertEquals(brukerId, stored.brukerid)
		assertEquals(ownApplication.innsendingsid, stored.ettersendingsid)
		assertTrue(stored.ettersendingsid != otherApplication.innsendingsid)
		verify(exactly = 1) { pdl.hentPersonIdents(brukerId) }
		verify(exactly = 0) { pdl.hentPersonIdents(testpersonid) }
		verify(timeout = 5000, exactly = 1) {
			publisherInterface.opprettBrukernotifikasjon(match { it.soknadRef.innsendingId == ettersending.innsendingsId })
		}
	}

	@Test
	fun `happy case create task`() {
		val brukerId = "12345678901"
		val vedlegg = listOf("W1", "W2")
		val skjemanr = "NAV 55-00.60"
		val soknadDto = opprettSoknad(brukerId, vedlegg, skjemanr)

		assertEquals(brukerId, soknadDto.brukerId)
		assertEquals(skjemanr, soknadDto.skjemanr)
	}

	@Test
	fun `happy case also creates oppgave  notification`() {
		// Given
		val brukerId = "12345678901"
		val vedlegg = listOf("W1", "W2")
		val skjemanr = "NAV 55-00.60"

		// When
		val soknadDto = opprettSoknad(brukerId, vedlegg, skjemanr)

		// Then
		sleep(50) // Liten delay for å sikre at asynkrone operasjoner er fullført før verifisering
		val noticationSlot = slot<AddNotification>()
		verify(exactly = 1) { publisherInterface.opprettBrukernotifikasjon(capture(noticationSlot)) }
		val notication = noticationSlot.captured
		assertEquals(true, notication.soknadRef.erSystemGenerert)
		assertTrue(notication.soknadRef.erEttersendelse)
		assertEquals(soknadDto.innsendingsId, notication.soknadRef.innsendingId)
		assertEquals(soknadDto.innsendingsId, notication.soknadRef.groupId)
		assertNull(notication.brukernotifikasjonInfo.utsettSendingTil)
	}


	@Test
	fun `happy case also creates utkast notification`() {
		// Given
		val brukerId = "12345678901"
		val vedlegg = listOf("W1", "W2")
		val skjemanr = "NAV 55-00.60"

		// When
		val soknadDto = opprettSoknad(brukerId, vedlegg, skjemanr, brukernotifikasjonstype = BrukernotifikasjonsType.utkast )

		// Then
		sleep(50) // Liten delay for å sikre at asynkrone operasjoner er fullført før verifisering
		val noticationSlot = slot<AddNotification>()
		verify(exactly = 1) { publisherInterface.opprettBrukernotifikasjon(capture(noticationSlot)) }
		val notication = noticationSlot.captured
		assertEquals(false, notication.soknadRef.erSystemGenerert)
		assertTrue(notication.soknadRef.erEttersendelse)
		assertEquals(soknadDto.innsendingsId, notication.soknadRef.innsendingId)
		assertEquals(soknadDto.innsendingsId, notication.soknadRef.groupId)
		assertNull(notication.brukernotifikasjonInfo.utsettSendingTil)
	}


	@Test
	fun `happy case delete application`() {
		val brukerId = "12345678901"
		val vedlegg = listOf("W1", "W2")
		val skjemanr = "NAV 55-00.60"
		val soknadDto = opprettSoknad(brukerId, vedlegg, skjemanr)

		val response = api.eksternOppgaveSlett(soknadDto.innsendingsId!! )

		assertNotNull(response)
		assertEquals(HttpStatus.OK, response.statusCode)

		val noticationSlot = slot<SoknadRef>()
		verify(exactly = 1) { publisherInterface.avsluttBrukernotifikasjon(capture(noticationSlot)) }
		assertEquals(soknadDto.innsendingsId, noticationSlot.captured.innsendingId)
	}

	@Test
	fun `Not found delete application`() {
		val brukerId = "12345678901"
		val vedlegg = listOf("W1", "W2")
		val skjemanr = "NAV 55-00.60"
		val soknadDto = opprettSoknad(brukerId, vedlegg, skjemanr)

		val response = api.eksternOppgaveSlettFail("12345" )

		assertNotNull(response)
		assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
	}


	@Test
	fun `happy case hent soknader`() {
		val brukerId = "12345678901"
		val brukerId2 = "12121212121"
		val vedlegg = listOf("W1", "W2")
		val skjemanr = "NAV 55-00.60"
		val skjemanr2 = "NAV 04-02.01"

		// Given
		val soknadDto = opprettSoknad(brukerId, vedlegg, skjemanr)
		val soknadDto2 = opprettSoknad(brukerId, vedlegg, skjemanr2)
		val soknadDto3 = opprettSoknad(brukerId2, vedlegg, skjemanr)

		// When
		val response = api.oppgaveHentSoknaderForSkjemanr(skjemanr, brukerId, listOf(SoknadType.ettersendelse), "nav-call-id" )

		// Then
		assertNotNull(response)
		assertEquals(HttpStatus.OK, response.statusCode)

		val list = response.body
		assertEquals(1, list?.size)
		assertEquals(brukerId, list?.get(0)?.brukerId)
	}

	private fun opprettSoknad(
		brukerId: String,
		vedlegg: List<String>,
		skjemanr: String,
		brukernotifikasjonstype: BrukernotifikasjonsType? = null,
		koblesTilEksisterendeSoknad: Boolean = false,
	): DokumentSoknadDto {
		val vedleggsListe = mutableListOf<InnsendtVedleggDto>()
		vedlegg.forEach { vedleggsListe.add(InnsendtVedleggDto(vedleggsnr = it, tittel = "Tittel"+it, url = null)) }
		val oppgave = EksternEttersendingsOppgave(
			brukerId = brukerId,
			skjemanr = skjemanr,
			sprak = "nb_NO",
			tema = "BID",
			tittel = "Avtale om barnebidrag",
			brukernotifikasjonstype = brukernotifikasjonstype,
			koblesTilEksisterendeSoknad = koblesTilEksisterendeSoknad,
			vedleggsListe = vedleggsListe
		)

		val response = api.createEttersendingsOppgave(oppgave )

		assertNotNull(response)
		assertEquals(HttpStatus.CREATED, response.statusCode)
		val opprettetSoknaddto = response.body
		assertEquals(true, opprettetSoknaddto?.erNavOpprettet)
		return response.body!!

	}


}
