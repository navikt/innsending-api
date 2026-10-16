package no.nav.soknad.innsending.rest.admin

import com.ninjasquad.springmockk.SpykBean
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.verify
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.cleanup.TempCleanupArchiveFailure
import no.nav.soknad.innsending.model.AdminArkiveringsstatus
import no.nav.soknad.innsending.repository.HendelseRepository
import no.nav.soknad.innsending.repository.SoknadRepository
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.HendelseType
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.service.admin.AdminArkiveringsstatusService
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.builders.SoknadDbDataTestBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpStatus
import org.springframework.test.context.TestPropertySource
import org.springframework.test.util.ReflectionTestUtils
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@TestPropertySource(properties = ["NAIS_CLUSTER_NAME=dev-gcp"])
class AdminRestApiTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@SpykBean
	lateinit var tempCleanupArchiveFailure: TempCleanupArchiveFailure

	@Autowired
	lateinit var soknadRepository: SoknadRepository

	@Autowired
	lateinit var hendelseRepository: HendelseRepository

	@Autowired
	lateinit var adminArkiveringsstatusService: AdminArkiveringsstatusService

	@LocalServerPort
	var serverPort: Int = 0

	private var testApi: ApiWebClient? = null
	private val api: ApiWebClient
		get() = testApi!!

	@BeforeEach
	fun setup() {
		testApi = ApiWebClient(webTestClient, serverPort, mockOAuth2Server)
		clearAllMocks()
		every { tempCleanupArchiveFailure.fixAttachmentStatusAndResubmit() } returns Unit
	}

	@Test
	fun `should run cleanup job with admin scope`() {
		val response = api.runAdminJob("cleanup-klar-for-innsending")

		assertEquals(HttpStatus.CREATED, response.statusCode)
		verify(exactly = 1) { tempCleanupArchiveFailure.fixAttachmentStatusAndResubmit() }
	}

	@Test
	fun `should reject call without required scope`() {
		val token = TokenGenerator(mockOAuth2Server).lagAzureOBOToken(scopes = "random-scope")
		val response = api.runAdminJob("cleanup-klar-for-innsending", token)

		assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
	}

	@Test
	fun `should return bad request for unknown job name`() {
		val response = api.runAdminJob("unknown-job")

		assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
	}

	@Test
	fun `should set arkiveringsstatus to Arkivert when status is ArkiveringFeilet`() {
		val innsendingsId = lagreSoknad(ArkiveringsStatus.ArkiveringFeilet)

		val response = api.oppdaterArkiveringsstatus(innsendingsId, gyldigRequest())

		assertEquals(HttpStatus.OK, response.statusCode)
		assertEquals(innsendingsId, response.body.innsendingsId)
		assertEquals(AdminArkiveringsstatus.Arkivert, response.body.arkiveringsstatus)
		assertEquals(ArkiveringsStatus.Arkivert, soknadRepository.findByInnsendingsid(innsendingsId)!!.arkiveringsstatus)
		assertTrue(
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(innsendingsId)
				.any { it.hendelsetype == HendelseType.Arkivert }
		)
	}

	@Test
	fun `should use preferred_username when NAVident is missing`() {
		val innsendingsId = lagreSoknad(ArkiveringsStatus.ArkiveringFeilet)
		val token = TokenGenerator(mockOAuth2Server).lagAzureOBOToken(
			scopes = "admin-access",
			navIdent = null,
			azpName = INNSENDING_ADMIN,
		)

		val response = api.oppdaterArkiveringsstatus(innsendingsId, gyldigRequest(), token)

		assertEquals(HttpStatus.OK, response.statusCode)
	}

	@Test
	fun `should use preferred_username when NAVident is blank`() {
		val innsendingsId = lagreSoknad(ArkiveringsStatus.ArkiveringFeilet)
		val token = TokenGenerator(mockOAuth2Server).lagAzureOBOToken(
			scopes = "admin-access",
			navIdent = "   ",
			azpName = INNSENDING_ADMIN,
		)

		val response = api.oppdaterArkiveringsstatus(innsendingsId, gyldigRequest(), token)

		assertEquals(HttpStatus.OK, response.statusCode)
	}

	@Test
	fun `should return conflict when arkiveringsstatus is not ArkiveringFeilet`() {
		val innsendingsId = lagreSoknad(ArkiveringsStatus.IkkeSatt)

		val response = api.oppdaterArkiveringsstatus(innsendingsId, gyldigRequest())

		assertEquals(HttpStatus.CONFLICT, response.statusCode)
		assertEquals("conflict", response.errorBody.errorCode)
		assertEquals(ArkiveringsStatus.IkkeSatt, soknadRepository.findByInnsendingsid(innsendingsId)!!.arkiveringsstatus)
	}

	@Test
	fun `should return not found for unknown innsendingsId`() {
		val response = api.oppdaterArkiveringsstatus(UUID.randomUUID().toString(), gyldigRequest())

		assertEquals(HttpStatus.NOT_FOUND, response.statusCode)
	}

	@Test
	fun `should return bad request for invalid innsendingsId without changing status`() {
		val innsendingsId = lagreSoknad(ArkiveringsStatus.ArkiveringFeilet)

		val response = api.oppdaterArkiveringsstatus("invalid-uuid", gyldigRequest())

		assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
		assertEquals("illegalArgument", response.errorBody.errorCode)
		assertEquals(ArkiveringsStatus.ArkiveringFeilet, soknadRepository.findByInnsendingsid(innsendingsId)!!.arkiveringsstatus)
		assertTrue(
			hendelseRepository.findAllByInnsendingsidOrderByTidspunkt(innsendingsId)
				.none { it.hendelsetype == HendelseType.Arkivert }
		)
	}

	@Test
	fun `should return forbidden when azp_name is not innsending-admin`() {
		val innsendingsId = lagreSoknad(ArkiveringsStatus.ArkiveringFeilet)
		val token = TokenGenerator(mockOAuth2Server).lagAzureOBOToken(
			scopes = "admin-access",
			navIdent = "Z123456",
			azpName = "dev-gcp:team-soknad:annen-app",
		)

		val response = api.oppdaterArkiveringsstatus(innsendingsId, gyldigRequest(), token)

		assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
		assertEquals(ArkiveringsStatus.ArkiveringFeilet, soknadRepository.findByInnsendingsid(innsendingsId)!!.arkiveringsstatus)
	}

	@Test
	fun `should return forbidden without admin scope`() {
		val innsendingsId = lagreSoknad(ArkiveringsStatus.ArkiveringFeilet)
		val token = TokenGenerator(mockOAuth2Server).lagAzureOBOToken(
			scopes = "random-scope",
			navIdent = "Z123456",
			azpName = INNSENDING_ADMIN,
		)

		val response = api.oppdaterArkiveringsstatus(innsendingsId, gyldigRequest(), token)

		assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
	}

	@Test
	fun `should return forbidden when azp_name is missing`() {
		val innsendingsId = lagreSoknad(ArkiveringsStatus.ArkiveringFeilet)
		val token = TokenGenerator(mockOAuth2Server).lagAzureOBOToken(
			scopes = "admin-access",
			navIdent = "Z123456",
			azpName = null,
		)

		val response = api.oppdaterArkiveringsstatus(innsendingsId, gyldigRequest(), token)

		assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
		assertEquals(ArkiveringsStatus.ArkiveringFeilet, soknadRepository.findByInnsendingsid(innsendingsId)!!.arkiveringsstatus)
	}

	@Test
	fun `should return forbidden when user identity is blank`() {
		val innsendingsId = lagreSoknad(ArkiveringsStatus.ArkiveringFeilet)
		val token = TokenGenerator(mockOAuth2Server).lagAzureOBOToken(
			scopes = "admin-access",
			navIdent = "   ",
			azpName = INNSENDING_ADMIN,
			preferredUsername = "   ",
		)

		val response = api.oppdaterArkiveringsstatus(innsendingsId, gyldigRequest(), token)

		assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
		assertEquals(ArkiveringsStatus.ArkiveringFeilet, soknadRepository.findByInnsendingsid(innsendingsId)!!.arkiveringsstatus)
	}

	@Test
	fun `should return forbidden when user identity is missing`() {
		val innsendingsId = lagreSoknad(ArkiveringsStatus.ArkiveringFeilet)
		val token = TokenGenerator(mockOAuth2Server).lagAzureOBOToken(
			scopes = "admin-access",
			navIdent = null,
			azpName = INNSENDING_ADMIN,
			preferredUsername = null,
		)

		val response = api.oppdaterArkiveringsstatus(innsendingsId, gyldigRequest(), token)

		assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
		assertEquals(ArkiveringsStatus.ArkiveringFeilet, soknadRepository.findByInnsendingsid(innsendingsId)!!.arkiveringsstatus)
	}

	@Test
	fun `should return forbidden when cluster is not dev-gcp`() {
		val innsendingsId = lagreSoknad(ArkiveringsStatus.ArkiveringFeilet)
		ReflectionTestUtils.setField(adminArkiveringsstatusService, "clusterName", "prod-gcp")
		try {
			val response = api.oppdaterArkiveringsstatus(innsendingsId, gyldigRequest())

			assertEquals(HttpStatus.FORBIDDEN, response.statusCode)
			assertEquals(ArkiveringsStatus.ArkiveringFeilet, soknadRepository.findByInnsendingsid(innsendingsId)!!.arkiveringsstatus)
		} finally {
			ReflectionTestUtils.setField(adminArkiveringsstatusService, "clusterName", "dev-gcp")
		}
	}

	@Test
	fun `should return bad request for blank begrunnelse`() {
		val innsendingsId = lagreSoknad(ArkiveringsStatus.ArkiveringFeilet)

		listOf("", "   ").forEach { begrunnelse ->
			val response = api.oppdaterArkiveringsstatus(innsendingsId, gyldigRequest(begrunnelse = begrunnelse))
			assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
		}
		assertEquals(ArkiveringsStatus.ArkiveringFeilet, soknadRepository.findByInnsendingsid(innsendingsId)!!.arkiveringsstatus)
	}

	@Test
	fun `should return bad request for too long begrunnelse`() {
		val innsendingsId = lagreSoknad(ArkiveringsStatus.ArkiveringFeilet)

		val response = api.oppdaterArkiveringsstatus(innsendingsId, gyldigRequest(begrunnelse = "a".repeat(201)))

		assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
	}

	@Test
	fun `should return bad request for unsupported target status`() {
		val innsendingsId = lagreSoknad(ArkiveringsStatus.ArkiveringFeilet)

		val response = api.oppdaterArkiveringsstatus(
			innsendingsId,
			gyldigRequest(arkiveringsstatus = ArkiveringsStatus.IkkeSatt.name)
		)

		assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
		assertEquals(ArkiveringsStatus.ArkiveringFeilet, soknadRepository.findByInnsendingsid(innsendingsId)!!.arkiveringsstatus)
	}

	private fun gyldigRequest(
		arkiveringsstatus: String = AdminArkiveringsstatus.Arkivert.value,
		begrunnelse: String = "Arkivering feilet pga. testdata",
	): Map<String, Any?> = mapOf("arkiveringsstatus" to arkiveringsstatus, "begrunnelse" to begrunnelse)

	private fun lagreSoknad(arkiveringsStatus: ArkiveringsStatus): String =
		soknadRepository.save(
			SoknadDbDataTestBuilder(
				status = SoknadsStatus.Innsendt,
				arkiveringsStatus = arkiveringsStatus,
			).build()
		).innsendingsid

	private companion object {
		const val INNSENDING_ADMIN = "dev-gcp:team-soknad:innsending-admin"
	}
}
