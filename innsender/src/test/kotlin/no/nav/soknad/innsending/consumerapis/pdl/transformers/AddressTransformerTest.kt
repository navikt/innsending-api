package no.nav.soknad.innsending.consumerapis.pdl.transformers

import no.nav.soknad.innsending.consumerapis.pdl.transformers.AddressTransformer.transformAddresses
import no.nav.soknad.innsending.utils.Date.formatDate
import no.nav.soknad.innsending.utils.builders.pdl.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class AddressTransformerTest {
	@Test
	fun `Should return null and empty lists if addresses are null`() {
		// When
		val result = transformAddresses(null, null, null)

		// Then
		assertNull(result.bostedsadresse)
		assertEquals(0, result.kontaktadresser?.size)
		assertEquals(0, result.oppholdsadresser?.size)
	}

	@Test
	fun `Should get bostedsAdresse with date before today`() {
		// Given
		val tenDaysAgo = formatDate(LocalDateTime.now().minusDays(10))
		val tenDaysFromNow = formatDate(LocalDateTime.now().plusDays(10))

		val bostedTenDaysAgo = BostedsadresseTestBuilder().gyldigFraOgMed(tenDaysAgo).build()
		val bostedTenDaysFromNow = BostedsadresseTestBuilder().gyldigFraOgMed(tenDaysFromNow).build()

		val bostedsAdresser = listOf(bostedTenDaysFromNow, bostedTenDaysAgo)

		// When
		val result = transformAddresses(bostedsAdresser, null, null)

		// Then
		assertEquals(LocalDate.parse(bostedTenDaysAgo.gyldigFraOgMed), result.bostedsadresse?.gyldigFraOgMed)
	}

	@Test
	fun `Should get bostedsAdresse with null date if all other are invalid`() {
		// Given
		val tenDaysFromNow = formatDate(LocalDateTime.now().plusDays(10))

		val bostedNull = BostedsadresseTestBuilder().gyldigFraOgMed(null).build()
		val bostedTenDaysFromNow = BostedsadresseTestBuilder().gyldigFraOgMed(tenDaysFromNow).build()

		val bostedsAdresser = listOf(bostedTenDaysFromNow, bostedNull)

		// When
		val result = transformAddresses(bostedsAdresser, null, null)

		// Then
		assertNull(result.bostedsadresse?.gyldigFraOgMed)
	}

	@Test
	fun `Should get closest bostedsAdresse to now`() {
		// Given
		val oneDayAgo = formatDate(LocalDateTime.now().minusDays(10))
		val tenDaysAgo = formatDate(LocalDateTime.now().minusDays(10))
		val twentyDaysAgo = formatDate(LocalDateTime.now().minusDays(20))
		val tenDaysFromNow = formatDate(LocalDateTime.now().plusDays(10))

		val bostedOneDayAgo = BostedsadresseTestBuilder().gyldigFraOgMed(oneDayAgo).build()
		val bostedTenDaysAgo = BostedsadresseTestBuilder().gyldigFraOgMed(tenDaysAgo).build()
		val bostedTwentyDaysAgo = BostedsadresseTestBuilder().gyldigFraOgMed(twentyDaysAgo).build()
		val bostedTenDaysFromNow = BostedsadresseTestBuilder().gyldigFraOgMed(tenDaysFromNow).build()

		val bostedsAdresser = listOf(bostedTwentyDaysAgo, bostedTenDaysFromNow, bostedTenDaysAgo, bostedOneDayAgo)

		// When
		val result = transformAddresses(bostedsAdresser, null, null)

		// Then
		assertEquals(LocalDate.parse(bostedTenDaysAgo.gyldigFraOgMed), result.bostedsadresse?.gyldigFraOgMed)
	}

	@Test
	fun `Should get all relevant kontaktadresse`() {
		// Given
		val oneDayAgo = formatDate(LocalDateTime.now().minusDays(10))
		val tenDaysAgo = formatDate(LocalDateTime.now().minusDays(10))
		val twentyDaysAgo = formatDate(LocalDateTime.now().minusDays(20))
		val tenDaysFromNow = formatDate(LocalDateTime.now().plusDays(10))

		val kontaktOneDayAgo = KontaktadresseTestBuilder().gyldigFraOgMed(oneDayAgo).build()
		val kontakTenDaysAgo = KontaktadresseTestBuilder().gyldigFraOgMed(tenDaysAgo).build()
		val kontakTwentyDaysAgo = KontaktadresseTestBuilder().gyldigFraOgMed(twentyDaysAgo).build()
		val kontakTenDaysFromNow = KontaktadresseTestBuilder().gyldigFraOgMed(tenDaysFromNow).build()
		val kontakEndedTenDaysAgo =
			KontaktadresseTestBuilder().gyldigFraOgMed(twentyDaysAgo).gyldigTilOgMed(tenDaysAgo).build()
		val kontaktNull = KontaktadresseTestBuilder().gyldigFraOgMed(null).gyldigTilOgMed(null).build()

		val kontaktAdresser =
			listOf(
				kontaktOneDayAgo,
				kontakTenDaysFromNow,
				kontakTwentyDaysAgo,
				kontakTenDaysAgo,
				kontakEndedTenDaysAgo,
				kontaktNull
			)

		// When
		val result = transformAddresses(null, null, kontaktAdresser)

		// Then
		assertEquals(4, result.kontaktadresser?.size, "4 valid kontaktadresser")
	}

	@Test
	fun `Should get one norwegian and one international oppholdsAdresse`() {
		// Given
		val oneDayAgo = formatDate(LocalDateTime.now().minusDays(10))
		val tenDaysAgo = formatDate(LocalDateTime.now().minusDays(10))
		val twentyDaysAgo = formatDate(LocalDateTime.now().minusDays(20))
		val tenDaysFromNow = formatDate(LocalDateTime.now().plusDays(10))

		val vegadresse = VegadresseTestBuilder().build()
		val utenlandskAdresse = UtenlandsadresseTestBuilder().build()

		val utlandOneDayAgo =
			OppholdsadresseTestBuilder().gyldigFraOgMed(oneDayAgo).utenlandskAdresse(utenlandskAdresse).vegadresse(null)
				.build()

		val norgeTenDaysAgo =
			OppholdsadresseTestBuilder().gyldigFraOgMed(tenDaysAgo).utenlandskAdresse(null).vegadresse(vegadresse).build()

		val norgeTwentyDaysAgo =
			OppholdsadresseTestBuilder().gyldigFraOgMed(twentyDaysAgo).utenlandskAdresse(null).vegadresse(vegadresse).build()

		val utlandTenDaysFromNow =
			OppholdsadresseTestBuilder().gyldigFraOgMed(tenDaysFromNow).utenlandskAdresse(utenlandskAdresse)
				.vegadresse(vegadresse).build()

		val norgeEndedTenDaysAgo =
			OppholdsadresseTestBuilder().gyldigFraOgMed(twentyDaysAgo).gyldigTilOgMed(tenDaysAgo).utenlandskAdresse(null)
				.vegadresse(vegadresse).build()

		val norgeNull =
			OppholdsadresseTestBuilder().gyldigFraOgMed(null).gyldigTilOgMed(null).utenlandskAdresse(utenlandskAdresse)
				.vegadresse(vegadresse)
				.build()

		val utlandEndedTenDaysAgo = OppholdsadresseTestBuilder().gyldigFraOgMed(twentyDaysAgo).gyldigTilOgMed(tenDaysAgo)
			.utenlandskAdresse(utenlandskAdresse).vegadresse(null).build()

		val oppholdsAdresser =
			listOf(
				utlandOneDayAgo,
				norgeEndedTenDaysAgo,
				utlandTenDaysFromNow,
				norgeTenDaysAgo,
				norgeNull,
				norgeTwentyDaysAgo,
				utlandEndedTenDaysAgo
			)

		// When
		val result = transformAddresses(null, oppholdsAdresser, null)

		// Then
		assertEquals(2, result.oppholdsadresser?.size, "Should be max 2 oppholdsadresser")
		val norwegianAddress = result.oppholdsadresser?.find { it.landkode == "NOR" }
		val internationalAddress = result.oppholdsadresser?.find { it.landkode != "NOR" }
		assertEquals(LocalDate.parse(norgeTenDaysAgo.gyldigFraOgMed), norwegianAddress?.gyldigFraOgMed)
		assertEquals(LocalDate.parse(utlandOneDayAgo.gyldigFraOgMed), internationalAddress?.gyldigFraOgMed)

	}

}
