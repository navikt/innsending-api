package no.nav.soknad.innsending.util

import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.safselvbetjening.generated.enums.Kanal
import no.nav.soknad.innsending.util.dokumentsoknad.isUnauthenticatedSubmission
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplicationAuthenticationTest {
	@Test
	fun `only the explicit no-login view type identifies an unauthenticated local submission`() {
		assertTrue(VisningsType.nologin.isUnauthenticatedSubmission())
		VisningsType.entries.filterNot { it == VisningsType.nologin }.forEach {
			assertFalse(it.isUnauthenticatedSubmission())
		}
		assertFalse((null as VisningsType?).isUnauthenticatedSubmission())
	}

	@Test
	fun `only the explicit no-login archive channel identifies an unauthenticated archived submission`() {
		assertTrue(Kanal.NAV_NO_UINNLOGGET.isUnauthenticatedSubmission())
		Kanal.entries.filterNot { it == Kanal.NAV_NO_UINNLOGGET }.forEach {
			assertFalse(it.isUnauthenticatedSubmission())
		}
		assertFalse((null as Kanal?).isUnauthenticatedSubmission())
	}
}
