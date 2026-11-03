package no.nav.soknad.innsending.rest

import no.nav.security.mock.oauth2.MockOAuth2Server
import com.github.tomakehurst.wiremock.client.WireMock.*
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.utils.ApiWebClient
import no.nav.soknad.innsending.utils.ApplicationTestRepository
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.builders.SkjemaDtoTestBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Import
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Import(ApplicationTestRepository::class)
class OwnerAccessIntegrationTest : ApplicationTest() {
	@Autowired lateinit var mockOAuth2Server: MockOAuth2Server
	@Autowired lateinit var storedApplications: ApplicationTestRepository
	@LocalServerPort var serverPort: Int = 0
	private lateinit var api: ApiWebClient

	@BeforeEach
	fun setup() { api = ApiWebClient(webTestClient, serverPort, mockOAuth2Server) }

	@ParameterizedTest
	@NullSource
	@ValueSource(strings = [
		"null", "[]", "42", "\"text\"", "{}",
		"{\"id\":\"12345678901\",\"idType\":\"ORGNR\"}",
		"{\"id\":\"12345678901\",\"idType\":\"UNKNOWN\"}",
		"{\"id\":12345678901,\"idType\":\"FNR\"}",
		"{\"id\":\"12345678901\",\"idType\":\"FNR\",\"extra\":true}",
		"{\"id\":\"invalid\",\"idType\":\"FNR\"}",
		"{\"id\":\"10987654321\",\"idType\":\"FNR\"}",
	])
	fun `invalid absent and unrelated owners give neutral denial and are not listed`(ownerJson: String?) {
		val created = api.createSoknad(SkjemaDtoTestBuilder().build()).assertHttpStatus(HttpStatus.CREATED).body
		val id = assertNotNull(created.innsendingsId)
		val attachment = api.getSoknadSendinn(id).assertHttpStatus(HttpStatus.OK).body.vedleggsListe.first()
		val file = api.uploadFile(id, assertNotNull(attachment.id)).assertHttpStatus(HttpStatus.CREATED).body
		// Owner is internal and malformed historical JSON cannot be created through REST.
		storedApplications.setRawOwner(id, ownerJson)
		val before = storedApplications.findBySubmissionId(id)

		api.getSoknadSendinn(id).assertHttpStatus(HttpStatus.NOT_FOUND).assertErrorCode(ErrorCode.NOT_FOUND)
		assertEquals(HttpStatus.NOT_FOUND, api.getSoknad(id).statusCode)
		api.getLegacyFile(id, assertNotNull(attachment.id), assertNotNull(file.id)).assertHttpStatus(HttpStatus.NOT_FOUND)
		api.submitDigitalApplication(created).assertHttpStatus(HttpStatus.NOT_FOUND)
		// An unreadable row has no public GET; verify rejected submission did not mutate its hidden state.
		assertEquals(before, storedApplications.findBySubmissionId(id))
		val listed = api.getExistingSoknader(created.skjemanr).assertHttpStatus(HttpStatus.OK).body
		assertTrue(listed.none { it.innsendingsId == id })
	}

	@Test
	fun `historical owner grants access through PDL history without rewriting owner`() {
		val created = api.createSoknad(SkjemaDtoTestBuilder().build()).assertHttpStatus(HttpStatus.CREATED).body
		val id = assertNotNull(created.innsendingsId)
		val historicalOwner = no.nav.soknad.innsending.repository.domain.models.ApplicationOwner.person("12345678902")
		// Historical ownership is internal; API requests cannot replace it.
		storedApplications.setOwner(id, historicalOwner)
		assertEquals(id, api.getSoknadSendinn(id).assertHttpStatus(HttpStatus.OK).body.innsendingsId)
		assertTrue(api.getExistingSoknader(created.skjemanr).assertHttpStatus(HttpStatus.OK).body.any { it.innsendingsId == id })
		assertEquals(historicalOwner, storedApplications.findBySubmissionId(id).owner)
	}

	@Test
	fun `nologin never grants person access even with a manually assigned matching owner`() {
		val created = api.createSoknad(SkjemaDtoTestBuilder().build()).assertHttpStatus(HttpStatus.CREATED).body
		val id = assertNotNull(created.innsendingsId)
		// Simulate an invalid anonymous row; no supported public request may set its owner.
		val attachment = api.getSoknadSendinn(id).assertHttpStatus(HttpStatus.OK).body.vedleggsListe.first()
		val file = api.uploadFile(id, assertNotNull(attachment.id)).assertHttpStatus(HttpStatus.CREATED).body
		storedApplications.setStoredDisplayType(id, "4")
		api.getSoknadSendinn(id).assertHttpStatus(HttpStatus.NOT_FOUND)
		api.getLegacyFile(id, assertNotNull(attachment.id), assertNotNull(file.id)).assertHttpStatus(HttpStatus.NOT_FOUND)
		api.submitDigitalApplication(created).assertHttpStatus(HttpStatus.NOT_FOUND)
		assertTrue(api.getExistingSoknader(created.skjemanr).assertHttpStatus(HttpStatus.OK).body.none { it.innsendingsId == id })
	}

	@Test
	fun `digital GCS files cannot be read through another application or another owner`() {
		val own = api.createSoknad(SkjemaDtoTestBuilder().build()).assertHttpStatus(HttpStatus.CREATED).body
		val foreign = api.createSoknad(SkjemaDtoTestBuilder().build()).assertHttpStatus(HttpStatus.CREATED).body
		val ownId = assertNotNull(own.innsendingsId)
		val foreignId = assertNotNull(foreign.innsendingsId)
		val file = api.uploadAttachmentFile(foreignId, "N6").assertHttpStatus(HttpStatus.CREATED).body
		api.getDigitalFile(foreignId, "N6", file.id).assertHttpStatus(HttpStatus.OK)
		api.getDigitalFile(ownId, "N6", file.id).assertHttpStatus(HttpStatus.NOT_FOUND)
		// Arrange a foreign owner; legacy user and file metadata must not override it.
		storedApplications.setOwner(foreignId, no.nav.soknad.innsending.repository.domain.models.ApplicationOwner.person("10987654321"))
		api.getDigitalFile(foreignId, "N6", file.id).assertHttpStatus(HttpStatus.NOT_FOUND)
	}

	@Test
	fun `foreign and crossed application attachment and file identifiers never expose files`() {
		val own = api.createSoknad(SkjemaDtoTestBuilder().build()).assertHttpStatus(HttpStatus.CREATED).body
		val foreign = api.createSoknad(SkjemaDtoTestBuilder().build()).assertHttpStatus(HttpStatus.CREATED).body
		val ownId = assertNotNull(own.innsendingsId)
		val foreignId = assertNotNull(foreign.innsendingsId)
		val ownAttachment = api.getSoknadSendinn(ownId).assertHttpStatus(HttpStatus.OK).body.vedleggsListe.first { !it.erVariant }
		val foreignAttachment = api.getSoknadSendinn(foreignId).assertHttpStatus(HttpStatus.OK).body.vedleggsListe.first { !it.erVariant }
		val file = api.uploadFile(foreignId, assertNotNull(foreignAttachment.id)).assertHttpStatus(HttpStatus.CREATED).body
		val fileId = assertNotNull(file.id)
		api.getLegacyFile(foreignId, foreignAttachment.id!!, fileId).assertHttpStatus(HttpStatus.OK)
		// Arrange a different verified owner independently of legacy identity and affected party.
		storedApplications.setOwner(foreignId, no.nav.soknad.innsending.repository.domain.models.ApplicationOwner.person("10987654321"))
		api.getSoknadSendinn(foreignId).assertHttpStatus(HttpStatus.NOT_FOUND)
		api.getLegacyFile(foreignId, foreignAttachment.id!!, fileId).assertHttpStatus(HttpStatus.NOT_FOUND)
		api.getLegacyFile(ownId, foreignAttachment.id!!, fileId).assertHttpStatus(HttpStatus.NOT_FOUND)
		api.getLegacyFile(ownId, assertNotNull(ownAttachment.id), fileId).assertHttpStatus(HttpStatus.NOT_FOUND)

		// Mock the external PDL boundary with an unrelated person's actual identifier set.
		wireMock.stubFor(post(urlEqualTo("/pdl-api/graphql"))
			.withRequestBody(matchingJsonPath("$.operationName", equalTo("HentIdenter")))
			.withRequestBody(matchingJsonPath("$.variables.ident", equalTo("10987654321")))
			.willReturn(okJson("""{"data":{"hentIdenter":{"identer":[{"ident":"10987654321","gruppe":"FOLKEREGISTERIDENT","historisk":false}]}}}""")))
		val foreignToken = TokenGenerator(mockOAuth2Server).lagTokenXToken("10987654321")
		api.getSoknadSendinn(foreignId, foreignToken).assertHttpStatus(HttpStatus.OK)
		api.getSoknadSendinn(ownId, foreignToken).assertHttpStatus(HttpStatus.NOT_FOUND)
		api.getLegacyFile(ownId, ownAttachment.id!!, fileId, foreignToken).assertHttpStatus(HttpStatus.NOT_FOUND)
		val foreignList = api.getExistingSoknader(foreign.skjemanr, authToken = foreignToken).assertHttpStatus(HttpStatus.OK).body
		assertTrue(foreignList.any { it.innsendingsId == foreignId })
	}
}
