package no.nav.soknad.innsending.security

import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.util.testpersonid
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class TilgangskontrollTest {

	private val subjectHandler = mockk<SubjectHandlerInterface>()
	private val pdlService = mockk<PdlInterface>()
	private val tilgangskontroll = Tilgangskontroll(subjectHandler, pdlService)

	@Test
	fun `identity extraction failure is propagated without looking up a fallback identity`() {
		val failure = IllegalStateException("Missing request context")
		every { subjectHandler.getUserIdFromToken() } throws failure

		assertSame(failure, assertFailsWith<IllegalStateException> { tilgangskontroll.hentBrukerFraToken() })
		assertSame(failure, assertFailsWith<IllegalStateException> { tilgangskontroll.hentPersonIdents() })

		verify { pdlService wasNot Called }
	}

	@Test
	fun `authenticated identity is returned unchanged`() {
		every { subjectHandler.getUserIdFromToken() } returns "authenticated-user"

		assertEquals("authenticated-user", tilgangskontroll.hentBrukerFraToken())
	}

	@Test
	fun `local handler still supplies its explicit test identity`() {
		val localTilgangskontroll = Tilgangskontroll(SubjectHandlerTestImpl(), pdlService)

		assertEquals(testpersonid, localTilgangskontroll.hentBrukerFraToken())
	}
}
