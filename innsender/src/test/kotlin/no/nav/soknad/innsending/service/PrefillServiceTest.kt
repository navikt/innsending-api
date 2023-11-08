package no.nav.soknad.innsending.service

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import kotlinx.coroutines.runBlocking
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertNull

class PrefillServiceTest : ApplicationTest() {

	@MockkBean
	lateinit var subjectHandler: SubjectHandlerInterface

	@Autowired
	lateinit var prefillService: PrefillService

	@Test
	fun `Should get prefill data for PDL`() {
		// Given
		val properties = listOf("sokerFornavn", "sokerEtternavn")
		val userId = "12128012345"

		// When
		val result = runBlocking { prefillService.getPrefillData(properties, userId) }

		// Then
		assertEquals("Ola", result.sokerFornavn)
		assertEquals("Nordmann", result.sokerEtternavn)
	}

	@Test
	fun `Should get prefill data for Arena (målgrupper)`() {
		// Given
		val properties = listOf("sokerMaalgrupper")
		val userId = "12128012345"

		every { subjectHandler.getUserIdFromToken() } returns userId

		// When
		val result = runBlocking { prefillService.getPrefillData(properties, userId) }

		// Then
		assertEquals("NEDSARBEVN", result.sokerMaalgrupper?.get(0)?.maalgruppetype?.name)
		assertEquals("Person med nedsatt arbeidsevne pga. sykdom", result.sokerMaalgrupper?.get(0)?.maalgruppenavn)
		assertEquals("2023-01-01", result.sokerMaalgrupper?.get(0)?.gyldighetsperiode?.fom.toString())
		assertNull(result.sokerMaalgrupper?.get(0)?.gyldighetsperiode?.tom)
	}

	@Test
	fun `Should get prefill data for Arena (aktiviteter)`() {
		// Given
		val properties = listOf("sokerAktiviteter")
		val userId = "12128012345"

		every { subjectHandler.getUserIdFromToken() } returns userId

		// When
		val result = runBlocking { prefillService.getPrefillData(properties, userId) }

		// Then
		val aktivitet = result.sokerAktiviteter?.get(0)
		val vedtaksinformasjon = aktivitet?.saksinformasjon?.vedtaksinformasjon?.get(0)
		val betalingsplan1 = vedtaksinformasjon?.betalingsplan?.get(0)
		val betalingsplan2 = vedtaksinformasjon?.betalingsplan?.get(1)

		assertEquals("130892484", aktivitet?.aktivitetId)
		assertEquals("ARBTREN", aktivitet?.aktivitetstype)
		assertEquals("Arbeidstrening", aktivitet?.aktivitetsnavn)
		assertEquals("2020-05-04", aktivitet?.periode?.fom.toString())
		assertEquals("2021-06-30", aktivitet?.periode?.tom.toString())
		assertEquals(5, aktivitet?.antallDagerPerUke)
		assertEquals(100, aktivitet?.prosentAktivitetsdeltakelse)
		assertEquals("FULLF", aktivitet?.aktivitetsstatus)
		assertEquals("Fullført", aktivitet?.aktivitetsstatusnavn)
		assertEquals(true, aktivitet?.erStoenadsberettigetAktivitet)
		assertEquals(false, aktivitet?.erUtdanningsaktivitet)
		assertEquals("MOELV BIL & CARAVAN AS", aktivitet?.arrangoer)
		assertEquals("12837895", aktivitet?.saksinformasjon?.saksnummerArena)
		assertEquals("TSR", aktivitet?.saksinformasjon?.sakstype)

		assertEquals("34359921", vedtaksinformasjon?.vedtakId)
		assertEquals(63, vedtaksinformasjon?.dagsats)
		assertEquals("2020-06-06", vedtaksinformasjon?.periode?.fom.toString())
		assertEquals("2020-12-31", vedtaksinformasjon?.periode?.tom.toString())
		assertEquals(false, vedtaksinformasjon?.trengerParkering)

		assertEquals("14514540", betalingsplan1?.betalingsplanId)
		assertEquals(315, betalingsplan1?.beloep)
		assertEquals("2020-06-06", betalingsplan1?.utgiftsperiode?.fom.toString())
		assertEquals("2020-06-12", betalingsplan1?.utgiftsperiode?.tom.toString())
		assertEquals("480716180", betalingsplan1?.journalpostId)

		assertEquals("14514541", betalingsplan2?.betalingsplanId)
		assertEquals(315, betalingsplan2?.beloep)
		assertEquals("2020-06-13", betalingsplan2?.utgiftsperiode?.fom.toString())
		assertEquals("2020-06-19", betalingsplan2?.utgiftsperiode?.tom.toString())
		assertEquals("480716180", betalingsplan2?.journalpostId)

	}

}
