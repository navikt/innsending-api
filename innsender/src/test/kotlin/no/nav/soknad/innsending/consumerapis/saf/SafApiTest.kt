package no.nav.soknad.innsending.consumerapis.saf

import org.junit.Test
import kotlin.test.assertEquals

class SafApiTest {

	@Test
	fun brevkodeKontrollSjekk() {
		val defaultVerdi = "N6"
		val lovligSkjemanr = "NAV 01-01.02"
		val lovligSkjemanr2 = "NAV 08-07.04D"
		val lovligSkjemanr3 = "NAVe 08-07.04D"
		val lovligVedleggsnr = "X2"

		// brevkoder som skal repareres
		val ekstraMellomrom = "  X2"
		val ekstraMellomrom2 = "X2 "
		val ekstraMellomrom3 = "NAV 08-07.04 D"
		val ulovligBrevKode = "1234567"

		assertEquals(defaultVerdi, KonverteringsUtility().brevKodeKontroll(defaultVerdi))
		assertEquals(lovligSkjemanr, KonverteringsUtility().brevKodeKontroll(lovligSkjemanr))
		assertEquals(lovligSkjemanr2, KonverteringsUtility().brevKodeKontroll(lovligSkjemanr2))
		assertEquals(lovligSkjemanr3, KonverteringsUtility().brevKodeKontroll(lovligSkjemanr3))
		assertEquals(lovligVedleggsnr, KonverteringsUtility().brevKodeKontroll(lovligVedleggsnr))

		assertEquals(lovligVedleggsnr, KonverteringsUtility().brevKodeKontroll(ekstraMellomrom))
		assertEquals(lovligVedleggsnr, KonverteringsUtility().brevKodeKontroll(ekstraMellomrom2))
		assertEquals(defaultVerdi, KonverteringsUtility().brevKodeKontroll(ulovligBrevKode))

	}
}
