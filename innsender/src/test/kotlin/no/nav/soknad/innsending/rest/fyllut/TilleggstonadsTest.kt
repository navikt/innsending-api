package no.nav.soknad.innsending.rest.fyllut

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenResponse
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.model.AktivitetEndepunkt
import no.nav.soknad.innsending.model.MaalgruppeType
import no.nav.soknad.innsending.service.FilService
import no.nav.soknad.innsending.service.KodeverkService
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.service.SoknadService
import no.nav.soknad.innsending.utils.Api
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.web.client.TestRestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TilleggstonadsTest : ApplicationTest() {

	var api: Api? = null

	val postnummerMap = mapOf(
		"7950" to "ABELVÆR",
		"3812" to "AKKERHAUGEN",
		"5575" to "AKSDAL",
		"7318" to "AGDENES",
	)

	@MockkBean
	lateinit var oauth2TokenService: OAuth2AccessTokenService

	@MockkBean
	lateinit var kodeverkService: KodeverkService

	@Autowired
	lateinit var restTemplate: TestRestTemplate

	@Autowired
	lateinit var soknadService: SoknadService

	@Autowired
	lateinit var repo: RepositoryUtils

	@Autowired
	lateinit var filService: FilService

	@Autowired
	lateinit var mockOAuth2Server: MockOAuth2Server

	@BeforeEach
	fun setup() {
		api = Api(restTemplate, serverPort!!, mockOAuth2Server)
		every { oauth2TokenService.getAccessToken(any()) } returns OAuth2AccessTokenResponse(access_token = "token")
		every { kodeverkService.getPoststed(any()) } answers { postnummerMap[firstArg()] }
	}

	@Value("\${server.port}")
	var serverPort: Int? = 9064

	@Test
	fun `Should return aktiviteter from Arena`() {
		// When
		val response = api?.getAktiviteter(AktivitetEndepunkt.aktivitet)

		// Then
		assertTrue(response != null)
		assertEquals(200, response.statusCode.value())

		val aktivitet = response.body!!.first()
		assertEquals(MaalgruppeType.NEDSARBEVN, aktivitet.maalgruppe?.maalgruppetype)
		assertEquals("Person med nedsatt arbeidsevne pga. sykdom", aktivitet.maalgruppe?.maalgruppenavn)
		assertEquals("130892484", aktivitet.aktivitetId)
		assertEquals("ARBTREN", aktivitet.aktivitetstype)
		assertEquals("Arbeidstrening", aktivitet.aktivitetsnavn)
		assertEquals("2020-05-04", aktivitet.periode.fom.toString())
		assertEquals("2023-06-30", aktivitet.periode.tom.toString())
		assertEquals(5, aktivitet.antallDagerPerUke)
		assertEquals(100, aktivitet.prosentAktivitetsdeltakelse)
		assertEquals("FULLF", aktivitet.aktivitetsstatus)
		assertEquals("Fullført", aktivitet.aktivitetsstatusnavn)
		assertEquals(true, aktivitet.erStoenadsberettigetAktivitet)
		assertEquals(false, aktivitet.erUtdanningsaktivitet)
		assertEquals("MOELV BIL & CARAVAN AS", aktivitet.arrangoer)
		assertNull(aktivitet.saksinformasjon)
	}

	@Test
	fun `Should return daglig reise aktiviteter from Arena`() {
		// When
		val response = api?.getAktiviteter(AktivitetEndepunkt.dagligreise)

		// Then
		assertTrue(response != null)
		assertEquals(200, response.statusCode.value())

		val aktivitet = response.body!!.first()
		val vedtaksinformasjon = aktivitet.saksinformasjon!!.vedtaksinformasjon!![0]
		val betalingsplan1 = vedtaksinformasjon.betalingsplan!![0]
		val betalingsplan2 = vedtaksinformasjon.betalingsplan!![1]

		assertEquals(MaalgruppeType.NEDSARBEVN, aktivitet.maalgruppe?.maalgruppetype)
		assertEquals("Person med nedsatt arbeidsevne pga. sykdom", aktivitet.maalgruppe?.maalgruppenavn)
		assertEquals("130892484", aktivitet.aktivitetId)
		assertEquals("ARBTREN", aktivitet.aktivitetstype)
		assertEquals("Arbeidstrening", aktivitet.aktivitetsnavn)
		assertEquals("2020-05-04", aktivitet.periode.fom.toString())
		assertEquals("2023-06-30", aktivitet.periode.tom.toString())
		assertEquals(5, aktivitet.antallDagerPerUke)
		assertEquals(100, aktivitet.prosentAktivitetsdeltakelse)
		assertEquals("FULLF", aktivitet.aktivitetsstatus)
		assertEquals("Fullført", aktivitet.aktivitetsstatusnavn)
		assertEquals(true, aktivitet.erStoenadsberettigetAktivitet)
		assertEquals(false, aktivitet.erUtdanningsaktivitet)
		assertEquals("MOELV BIL & CARAVAN AS", aktivitet.arrangoer)
		assertEquals("12837895", aktivitet.saksinformasjon?.saksnummerArena)
		assertEquals("TSR", aktivitet.saksinformasjon?.sakstype)

		assertEquals("34359921", vedtaksinformasjon.vedtakId)
		assertEquals(63, vedtaksinformasjon.dagsats)
		assertEquals("2020-06-06", vedtaksinformasjon.periode.fom.toString())
		assertEquals("2020-12-31", vedtaksinformasjon.periode.tom.toString())
		assertEquals(false, vedtaksinformasjon.trengerParkering)

		assertEquals("14514540", betalingsplan1.betalingsplanId)
		assertEquals(315, betalingsplan1.beloep)
		assertEquals("2020-06-06", betalingsplan1.utgiftsperiode.fom.toString())
		assertEquals("2020-06-12", betalingsplan1.utgiftsperiode.tom.toString())
		assertEquals("480716180", betalingsplan1.journalpostId)

		assertEquals("14514541", betalingsplan2.betalingsplanId)
		assertEquals(315, betalingsplan2.beloep)
		assertEquals("2020-06-13", betalingsplan2.utgiftsperiode.fom.toString())
		assertEquals("2020-06-19", betalingsplan2.utgiftsperiode.tom.toString())
		assertEquals("480716180", betalingsplan2.journalpostId)

	}


	@Test
	fun `Should return correct prefill-data from Arena (maalgruppe)`() {
		// Given
		val properties = "sokerMaalgruppe"

		// When
		val response = api?.getPrefillData(properties)

		// Then
		assertTrue(response != null)
		assertEquals(200, response.statusCode.value())
		assertEquals(MaalgruppeType.NEDSARBEVN, response.body?.sokerMaalgruppe?.maalgruppetype)
		assertEquals("Person med nedsatt arbeidsevne pga. sykdom", response.body?.sokerMaalgruppe?.maalgruppenavn)
	}

}
