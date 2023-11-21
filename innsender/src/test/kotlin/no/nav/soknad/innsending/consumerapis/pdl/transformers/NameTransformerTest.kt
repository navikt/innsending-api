package no.nav.soknad.innsending.consumerapis.pdl.transformers

import no.nav.soknad.innsending.consumerapis.pdl.transformers.NameTransformer.transformName
import no.nav.soknad.innsending.pdl.generated.prefilldata.Navn
import no.nav.soknad.innsending.utils.Date.formatToLocalDate
import no.nav.soknad.innsending.utils.builders.pdl.NavnTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class NameTransformerTest {
	@Test
	fun `Should return null if names list is empty`() {
		// Given
		val emptyList = emptyList<Navn>()

		// When
		val result = transformName(emptyList)

		// Then
		assertNull(result)
	}

	@Test
	fun `Should return null if names list is null`() {
		// Given, When
		val result = transformName(null)

		// Then
		assertNull(result)
	}

	@Test
	fun `Should get date before today`() {
		// Given
		val tenDaysAgo = formatToLocalDate(LocalDateTime.now().minusDays(10))
		val tenDaysFromNow = formatToLocalDate(LocalDateTime.now().plusDays(10))

		val nameTenDaysAgo = NavnTestBuilder().gyldigFraOgMed(tenDaysAgo).build()
		val nameTenDaysFromNow = NavnTestBuilder().gyldigFraOgMed(tenDaysFromNow).build()

		val names = listOf(nameTenDaysAgo, nameTenDaysFromNow)

		// When
		val result = transformName(names)

		// Then
		assertEquals(nameTenDaysAgo, result)
	}

	@Test
	fun `Should get null date if all other are invalid`() {
		// Given
		val tenDaysFromNow = formatToLocalDate(LocalDateTime.now().plusDays(10))

		val nameNull = NavnTestBuilder().gyldigFraOgMed(null).build()
		val nameTenDaysFromNow = NavnTestBuilder().gyldigFraOgMed(tenDaysFromNow).build()

		val names = listOf(nameTenDaysFromNow, nameNull)

		// When
		val result = transformName(names)

		// Then
		assertEquals(nameNull, result)
	}

	@Test
	fun `Should get closest date to now`() {
		// Given
		val oneDayAgo = formatToLocalDate(LocalDateTime.now().minusDays(10))
		val tenDaysAgo = formatToLocalDate(LocalDateTime.now().minusDays(10))
		val twentyDaysAgo = formatToLocalDate(LocalDateTime.now().minusDays(20))
		val tenDaysFromNow = formatToLocalDate(LocalDateTime.now().plusDays(10))

		val nameOneDayAgo = NavnTestBuilder().gyldigFraOgMed(oneDayAgo).build()
		val nameTenDaysAgo = NavnTestBuilder().gyldigFraOgMed(tenDaysAgo).build()
		val nameTwentyDaysAgo = NavnTestBuilder().gyldigFraOgMed(twentyDaysAgo).build()
		val nameTenDaysFromNow = NavnTestBuilder().gyldigFraOgMed(tenDaysFromNow).build()

		val names = listOf(nameTenDaysAgo, nameTwentyDaysAgo, nameTenDaysFromNow, nameOneDayAgo)

		// When
		val result = transformName(names)

		// Then
		assertEquals(nameTenDaysAgo, result)
	}


}
