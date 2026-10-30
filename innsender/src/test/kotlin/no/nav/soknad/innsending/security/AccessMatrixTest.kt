package no.nav.soknad.innsending.security

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.model.SoknadsStatusDto
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.utils.TokenGenerator
import no.nav.soknad.innsending.utils.builders.DokumentSoknadDtoTestBuilder
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.core.annotation.AnnotationUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.MultipartBodyBuilder
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.method.HandlerMethod
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.time.Duration
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Tilgangsmatrise for alle endepunkter (#461).
 *
 * Testen beskriver dagens tilgangskontrakt uavhengig av hvilket bibliotek som validerer token.
 * Den skal kjøre uendret før og etter byttet fra token-support til Spring Security og Texas.
 * Bare [deniedResponses] skal endres, og bare etter en vedtatt kontraktsendring.
 */
open class AccessMatrixTest : ApplicationTest() {

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@Autowired
	lateinit var soknadService: SoknadService

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	lateinit var handlerMapping: RequestMappingHandlerMapping

	@LocalServerPort
	var serverPort: Int = 0

	// Den delte webTestClient-en får port 0 fordi den lages før serveren starter
	private val client: WebTestClient
		get() = webTestClient.mutate()
			.baseUrl("http://localhost:$serverPort")
			.responseTimeout(Duration.ofMinutes(1))
			.build()

	private val tokens: TokenGenerator
		get() = TokenGenerator(mockOAuth2Server)

	enum class AccessPolicy {
		PUBLIC,
		TOKENX_HIGH_ACR,
		AZURE,
		AZURE_ADMIN,
		AZURE_NOLOGIN,
	}

	enum class Outcome { ALLOWED, UNAUTHENTICATED, FORBIDDEN }

	data class DeniedResponse(val status: Int, val errorCode: String)

	// Token-support pakker både manglende token og manglende claims inn i JwtTokenUnauthorizedException,
	// som RestExceptionHandler mapper til 403. Endres bare etter vedtatt kontraktsendring.
	private val deniedResponses = mapOf(
		Outcome.UNAUTHENTICATED to DeniedResponse(403, "errorCode.forbidden"),
		Outcome.FORBIDDEN to DeniedResponse(403, "errorCode.forbidden"),
	)

	// Tilgangsregel per controller. Metoder arver controllerens regel med mindre de står i methodPolicies.
	private val controllerPolicies = mapOf(
		"HealthCheck" to AccessPolicy.PUBLIC,
		"SoknadRestApi" to AccessPolicy.TOKENX_HIGH_ACR,
		"VedleggRestApi" to AccessPolicy.TOKENX_HIGH_ACR,
		"FilRestApi" to AccessPolicy.TOKENX_HIGH_ACR,
		"FyllutRestApi" to AccessPolicy.TOKENX_HIGH_ACR,
		"EttersendingRestApi" to AccessPolicy.TOKENX_HIGH_ACR,
		"LospostRestApi" to AccessPolicy.TOKENX_HIGH_ACR,
		"EksternRestApi" to AccessPolicy.TOKENX_HIGH_ACR,
		"EndToEndRestApi" to AccessPolicy.TOKENX_HIGH_ACR,
		"InternInitiertOppgaver" to AccessPolicy.AZURE,
		"FyllutMerge" to AccessPolicy.AZURE,
		"InnsendtListeApi" to AccessPolicy.AZURE,
		"NologinApplicationRestApi" to AccessPolicy.AZURE_NOLOGIN,
		"AdminRestApi" to AccessPolicy.AZURE_ADMIN,
		"ConfigRestApi" to AccessPolicy.AZURE_ADMIN,
	)

	private val methodPolicies = emptyMap<String, AccessPolicy>()

	/**
	 * @param valid tokenet er gyldig signert, utstedt av en godkjent issuer, til riktig audience og ikke utløpt
	 * @param grants reglene tokenet oppfyller
	 */
	inner class TestToken(
		val name: String,
		val valid: Boolean,
		val grants: Set<AccessPolicy>,
		val create: () -> String?,
	) {
		// Offentlige endepunkter testes bare med et utvalg token, fordi /health/backends kaller nedstrøms tjenester
		val probesPublicEndpoints: Boolean
			get() = name in setOf("ingen token", "ikke en JWT", "TokenX acr=idporten-loa-high", "TokenX ukjent signeringsnøkkel")
	}

	private fun testTokens(): List<TestToken> = listOf(
		TestToken("ingen token", false, emptySet()) { null },
		TestToken("ikke en JWT", false, emptySet()) { "not-a-jwt" },
		TestToken("TokenX acr=Level4", true, setOf(AccessPolicy.TOKENX_HIGH_ACR)) {
			tokens.lagTokenXToken(acr = TokenGenerator.ACR_LEVEL_4)
		},
		TestToken("TokenX acr=idporten-loa-high", true, setOf(AccessPolicy.TOKENX_HIGH_ACR)) {
			tokens.lagTokenXToken(acr = TokenGenerator.ACR_IDPORTEN_LOA_HIGH)
		},
		TestToken("TokenX acr=idporten-loa-substantial", true, emptySet()) {
			tokens.lagTokenXToken(acr = TokenGenerator.ACR_IDPORTEN_LOA_SUBSTANTIAL)
		},
		TestToken("TokenX uten acr", true, emptySet()) { tokens.lagTokenXToken(acr = null) },
		TestToken("TokenX feil audience", false, emptySet()) { tokens.lagTokenXToken(audience = "annen-app") },
		TestToken("TokenX utløpt", false, emptySet()) { tokens.lagTokenXToken(expiry = -3600) },
		TestToken("TokenX ukjent issuer", false, emptySet()) { tokens.lagTokenXToken(issuerId = "ukjent") },
		TestToken("TokenX ukjent signeringsnøkkel", false, emptySet()) { tokens.lagTokenXTokenSignedWithUnknownKey() },
		TestToken("TokenX usignert (alg=none)", false, emptySet()) { tokens.lagUnsignedTokenXToken() },
		// Kontroll: signert med issuerens nøkkel utenom mock-serveren. Beviser at tokenene uten claims under avvises på grunn av claimene
		TestToken("TokenX egensignert med alle claims", true, setOf(AccessPolicy.TOKENX_HIGH_ACR)) {
			tokens.lagTokenXTokenWithoutClaims()
		},
		// Texas krever iss, exp og iat, men ikke sub. Token-support krevde alle fire
		TestToken("TokenX uten exp", false, emptySet()) { tokens.lagTokenXTokenWithoutClaims("exp") },
		TestToken("TokenX uten iat", false, emptySet()) { tokens.lagTokenXTokenWithoutClaims("iat") },
		// Kontraktsendring i steg 3j (#461): Texas godtar TokenX-token uten sub. TokenX utsteder alltid sub, og
		// identiteten leses fra pid, så endringen har ingen praktisk betydning
		TestToken("TokenX uten sub", true, setOf(AccessPolicy.TOKENX_HIGH_ACR)) { tokens.lagTokenXTokenWithoutClaims("sub") },
		TestToken("Azure OBO scp=admin-access defaultaccess", true, setOf(AccessPolicy.AZURE, AccessPolicy.AZURE_ADMIN)) {
			tokens.lagAzureOBOTokenWithScp(scp = "admin-access defaultaccess", navIdent = "Z123456")
		},
		// Dagens regel krever eksakt streng, så omvendt rekkefølge og ekstra scope gir ikke admin
		TestToken("Azure OBO scp=defaultaccess admin-access", true, setOf(AccessPolicy.AZURE)) {
			tokens.lagAzureOBOTokenWithScp(scp = "defaultaccess admin-access", navIdent = "Z123456")
		},
		TestToken("Azure OBO scp=admin-access defaultaccess annet", true, setOf(AccessPolicy.AZURE)) {
			tokens.lagAzureOBOTokenWithScp(scp = "admin-access defaultaccess annet", navIdent = "Z123456")
		},
		TestToken("Azure OBO scp=defaultaccess", true, setOf(AccessPolicy.AZURE)) {
			tokens.lagAzureOBOTokenWithScp(scp = "defaultaccess", navIdent = "Z123456")
		},
		TestToken("Azure M2M", true, setOf(AccessPolicy.AZURE)) { tokens.lagAzureM2MToken() },
		TestToken("Azure M2M roles=nologin-access", true, setOf(AccessPolicy.AZURE, AccessPolicy.AZURE_NOLOGIN)) {
			tokens.lagAzureM2MToken(roles = listOf("nologin-access"))
		},
		TestToken("Azure feil audience", false, emptySet()) { tokens.lagAzureM2MToken(audience = "annen-app") },
		TestToken("Azure utløpt", false, emptySet()) { tokens.lagAzureM2MToken(expiry = -3600) },
	)

	data class Endpoint(
		val key: String,
		val method: HttpMethod,
		val pathPattern: String,
		val handler: HandlerMethod,
		val info: RequestMappingInfo,
	)

	private fun applicationEndpoints(): List<Endpoint> =
		handlerMapping.handlerMethods
			.filter { (_, handler) -> handler.beanType.packageName.startsWith("no.nav.soknad") }
			.flatMap { (info, handler) ->
				val methods = info.methodsCondition.methods.ifEmpty { setOf(org.springframework.web.bind.annotation.RequestMethod.GET) }
				val patterns = info.pathPatternsCondition?.patternValues ?: info.patternValues
				methods.flatMap { requestMethod ->
					patterns.map { pattern ->
						Endpoint(
							key = "${handler.beanType.simpleName}.${handler.method.name}",
							method = HttpMethod.valueOf(requestMethod.name),
							pathPattern = pattern,
							handler = handler,
							info = info,
						)
					}
				}
			}
			.sortedBy { "${it.pathPattern} ${it.method}" }

	private fun policyFor(endpoint: Endpoint): AccessPolicy? =
		methodPolicies[endpoint.key] ?: controllerPolicies[endpoint.handler.beanType.simpleName]

	private fun expectedOutcome(policy: AccessPolicy, token: TestToken): Outcome = when {
		policy == AccessPolicy.PUBLIC -> Outcome.ALLOWED
		!token.valid -> Outcome.UNAUTHENTICATED
		policy in token.grants -> Outcome.ALLOWED
		else -> Outcome.FORBIDDEN
	}

	@Test
	fun `every application endpoint has a declared access policy`() {
		val endpoints = applicationEndpoints()
		assertTrue(endpoints.size > 40, "Fant bare ${endpoints.size} endepunkter, sjekk oppslaget i handlerMapping")

		val missing = endpoints.filter { policyFor(it) == null }.map { "${it.method} ${it.pathPattern} (${it.key})" }
		assertTrue(missing.isEmpty(), "Endepunkter uten tilgangsregel i AccessMatrixTest:\n${missing.joinToString("\n")}")
	}

	// Spring Security-regelen (#461, steg 3b–3e). Metoderegelen går foran controllerens, som i Spring Security
	@Test
	fun `every endpoint has the Spring Security rule for its access policy`() {
		val expectedRule = mapOf(
			AccessPolicy.TOKENX_HIGH_ACR to KreverInnbygger::class.java,
			AccessPolicy.AZURE to KreverAzure::class.java,
			AccessPolicy.AZURE_ADMIN to KreverAzureAdmin::class.java,
			AccessPolicy.AZURE_NOLOGIN to KreverAzureNologin::class.java,
		).mapValues { (_, annotation) -> AnnotationUtils.findAnnotation(annotation, PreAuthorize::class.java)!!.value }

		val wrong = applicationEndpoints().mapNotNull { endpoint ->
			val policy = policyFor(endpoint) ?: return@mapNotNull null
			val actual = (AnnotatedElementUtils.findMergedAnnotation(endpoint.handler.method, PreAuthorize::class.java)
				?: AnnotatedElementUtils.findMergedAnnotation(endpoint.handler.beanType, PreAuthorize::class.java))?.value
			val expected = expectedRule[policy]
			if (actual == expected) null else "${endpoint.method} ${endpoint.pathPattern} [$policy]: har $actual, skal ha $expected"
		}
		assertTrue(wrong.isEmpty(), "Endepunkter med feil @PreAuthorize:\n${wrong.joinToString("\n")}")
	}

	@Test
	fun `access matrix matches the access contract for every endpoint and token type`() {
		val endpoints = applicationEndpoints()
		val testTokens = testTokens()
		val mismatches = mutableListOf<String>()
		val observedDenials = mutableMapOf<String, Int>()

		testTokens.forEach { token ->
			val bearer = token.create()
			endpoints.forEach { endpoint ->
				val policy = policyFor(endpoint) ?: return@forEach
				if (policy == AccessPolicy.PUBLIC && !token.probesPublicEndpoints) return@forEach
				val expected = expectedOutcome(policy, token)
				val response = call(endpoint, bearer)

				if (response.isAuthDenial()) {
					val key = "${response.status}/${response.errorCode}"
					observedDenials[key] = (observedDenials[key] ?: 0) + 1
				}

				val problem = verify(expected, response)
				if (problem != null) {
					mismatches += "${endpoint.method} ${endpoint.pathPattern} [$policy] med «${token.name}»: forventet $expected, $problem"
				}
			}
		}

		if (mismatches.isNotEmpty()) {
			fail(
				"${mismatches.size} avvik i tilgangsmatrisen. Observerte avvisninger: $observedDenials\n" +
					mismatches.joinToString("\n")
			)
		}
	}

	@Test
	fun `public documentation endpoints answer without token`() {
		listOf("/v3/api-docs", "/swagger-ui/index.html").forEach { path ->
			val status = client.get().uri(path).exchange().returnResult(String::class.java).status.value()
			assertEquals(200, status, "GET $path")
		}
	}

	// Actuator-endepunktene ligger utenfor controllerne og dekkes ikke av inventaret over. Token-support beskytter dem ikke i dag.
	@Test
	fun `actuator endpoints answer without token`() {
		listOf("/internal/health", "/internal/info", "/internal/metrics", "/internal/prometheus").forEach { path ->
			val status = client.get().uri(path).exchange().returnResult(String::class.java).status.value()
			// Helse kan svare 503 når en avhengighet er nede. Kontrakten gjelder tilgang, ikke status på helsa
			assertTrue(status !in setOf(401, 403, 404), "GET $path uten token ga $status")
		}
	}

	// refresh står i management.endpoints.web.exposure.include, men spring-cloud-context mangler, så endepunktet finnes ikke
	@Test
	fun `actuator refresh is not exposed`() {
		val status = client.post().uri("/internal/refresh").exchange().returnResult(String::class.java).status.value()
		// 404 fra manglende ressurs eller 405 fra ressurshåndtereren, som bare godtar GET og HEAD
		assertTrue(status in setOf(404, 405), "POST /internal/refresh ga $status")
	}

	@Test
	fun `unknown route is never served`() {
		listOf(null, tokens.lagTokenXToken()).forEach { bearer ->
			val status = client.get().uri("/finnes/ikke/${UUID.randomUUID()}")
				.headers { headers -> bearer?.let { headers.setBearerAuth(it) } }
				.exchange().returnResult(String::class.java).status
			assertTrue(status.is4xxClientError, "Ukjent rute ga $status")
		}
	}

	@Test
	fun `citizen only sees applications owned by one of their identities`() {
		// PDL-stubben (pdlIdenterOk.json) gir identene 12345678901, 12345678902 og 19876898104 for alle oppslag
		val ownedViaHistoricalIdent = opprettSoknad(brukerId = "19876898104")
		val ownedByOtherPerson = opprettSoknad(brukerId = "01017012345")
		val token = tokens.lagTokenXToken(fnr = TokenGenerator.subject)

		assertEquals(200, sendinn(HttpMethod.GET, ownedViaHistoricalIdent, token).status)

		val readOther = sendinn(HttpMethod.GET, ownedByOtherPerson, token)
		assertEquals(404, readOther.status, "Andres søknad skal se ut som om den ikke finnes")

		val deleteOther = sendinn(HttpMethod.DELETE, ownedByOtherPerson, token)
		assertEquals(404, deleteOther.status)
		assertEquals(SoknadsStatusDto.Opprettet, soknadService.hentSoknad(ownedByOtherPerson).status,
			"Søknaden skal fortsatt finnes etter avvist sletting")
	}

	private fun opprettSoknad(brukerId: String): String =
		soknadService.opprettNySoknad(DokumentSoknadDtoTestBuilder(brukerId = brukerId).build()).innsendingsId!!

	private fun sendinn(method: HttpMethod, innsendingsId: String, bearer: String): MatrixResponse =
		exchange(method, "/frontend/v1/soknad/$innsendingsId", bearer, contentType = null, multipart = false)

	data class MatrixResponse(val status: Int, val errorCode: String?) {
		fun isAuthDenial() = status == 401 || status == 403
	}

	private fun verify(expected: Outcome, response: MatrixResponse): String? {
		if (expected == Outcome.ALLOWED) {
			return if (response.isAuthDenial()) "fikk ${response.status} ${response.errorCode}" else null
		}
		val denied = deniedResponses.getValue(expected)
		return if (response.status == denied.status && response.errorCode == denied.errorCode) null
		else "fikk ${response.status} ${response.errorCode}, ventet ${denied.status} ${denied.errorCode}"
	}

	private val bodyMethods = setOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)

	private fun call(endpoint: Endpoint, bearer: String?): MatrixResponse {
		val consumes = endpoint.info.consumesCondition.consumableMediaTypes
		val multipart = consumes.any { it.isCompatibleWith(MediaType.MULTIPART_FORM_DATA) }
		// Endepunkter med consumes matcher bare med riktig Content-Type, også for GET
		val contentType = when {
			multipart -> MediaType.MULTIPART_FORM_DATA
			consumes.isNotEmpty() -> consumes.first()
			endpoint.method in bodyMethods -> MediaType.APPLICATION_JSON
			else -> null
		}
		return exchange(endpoint.method, concretePath(endpoint), bearer, contentType, multipart)
	}

	private fun exchange(
		method: HttpMethod,
		path: String,
		bearer: String?,
		contentType: MediaType?,
		multipart: Boolean,
	): MatrixResponse {
		val request = client.method(method).uri(path)
			.headers { headers ->
				bearer?.let { headers.set(HttpHeaders.AUTHORIZATION, "Bearer $it") }
				if (!multipart && contentType != null) {
					headers.contentType = contentType
				}
			}
		val spec = when {
			multipart -> request.body(BodyInserters.fromMultipartData(MultipartBodyBuilder().apply {
				part("files", "innhold".toByteArray()).filename("matrise.pdf").contentType(MediaType.APPLICATION_PDF)
			}.build()))
			method in bodyMethods -> request.bodyValue("{}")
			else -> request
		}
		val result = spec.exchange().returnResult(String::class.java)
		val body = result.responseBody.collectList().block()?.joinToString("").orEmpty()
		return MatrixResponse(result.status.value(), errorCodeFrom(body))
	}

	private fun errorCodeFrom(body: String): String? =
		runCatching { jacksonObjectMapper().readTree(body)?.get("errorCode")?.asText() }.getOrNull()

	// Fyller inn stivariabler med verdier som ikke finnes, så tillatte kall ikke endrer eksisterende data
	private fun concretePath(endpoint: Endpoint): String {
		return Regex("\\{([^}/]+)}").replace(endpoint.pathPattern) { match ->
			val name = match.groupValues[1].substringBefore(":")
			when {
				name in setOf("vedleggsId", "filId") -> "999999999"
				name == "key" -> "ukjent-config"
				name == "skjemanr" -> "NAV 00-00.00"
				name.endsWith("Id") || name == "uuids" -> UUID.randomUUID().toString()
				else -> "matrise"
			}
		}
	}
}
