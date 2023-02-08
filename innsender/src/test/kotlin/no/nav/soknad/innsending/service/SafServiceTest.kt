package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.consumerapis.saf.SafAPITmp
import no.nav.soknad.innsending.util.testpersonid
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals

@SpringBootTest
@ActiveProfiles("spring")
@EnableTransactionManagement
class SafServiceTest {

	@Test
	fun hentInnsendteSoknaderForBrukerTest() {
		val brukerId = testpersonid
		val safService = SafService(SafAPITmp())
		val innsendteSoknader = safService.hentInnsendteSoknader(brukerId)

		assertTrue(innsendteSoknader.isNotEmpty())
		assertEquals(5, innsendteSoknader.size)
		assertTrue(innsendteSoknader.all{ it.innsendtVedleggDtos.isNotEmpty()} )
		assertTrue(innsendteSoknader.all{ it.innsendtVedleggDtos.filter { "L7".equals(it.vedleggsnr, true) }.isEmpty()} )
		assertTrue(innsendteSoknader.all{ it.innsendtVedleggDtos.filter { it.vedleggsnr.startsWith("NAVe") }.isEmpty()} )
	}

	@Test
	fun brukerHarIngenInnsendteSoknaderTest() {
		val brukerId = "999999999999"
		val safService = SafService(SafAPITmp())
		val innsendteSoknader = safService.hentInnsendteSoknader(brukerId)

		assertTrue(innsendteSoknader.isEmpty())
	}

	@Test
	fun testDatoKonvertering() {
		val formatter: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME
		val summerDateString = "2022-08-01T12:00:00.000000"
		val summerDateTime = LocalDateTime.parse(summerDateString, formatter)
		val summerZoneOffset = summerDateTime.atOffset(ZoneId.of("CET").rules.getOffset(summerDateTime))
		assertEquals("+02:00", summerZoneOffset.offset.id)
		val dateString = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
		val dateTime = LocalDateTime.parse(dateString, formatter)
		val zoneOffSet = OffsetDateTime.now().offset
		val dateOffset = dateTime.atOffset(zoneOffSet)
		assertTrue(dateOffset != null)

	}
}
