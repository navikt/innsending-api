package no.nav.soknad.innsending.rest.admin

import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.innsending.cleanup.TempCleanupArchiveFailure
import no.nav.soknad.innsending.exceptions.ForbiddenException
import no.nav.soknad.innsending.model.AdminArkiveringsstatus
import no.nav.soknad.innsending.model.OppdaterArkiveringsstatusRequest
import no.nav.soknad.innsending.security.SubjectHandlerImpl
import no.nav.soknad.innsending.security.SubjectHandlerTestImpl
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.service.admin.AdminArkiveringsstatusService
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.test.context.support.TestPropertySourceUtils
import java.util.UUID
import java.util.function.Supplier
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals

class AdminRestApiProfileTest {
	@ParameterizedTest
	@ValueSource(strings = ["endtoend", "local", "docker"])
	fun `should start without JWT configuration and reject archiving status changes`(profile: String) {
		val repo = mockk<RepositoryUtils>()
		AnnotationConfigApplicationContext().use { context ->
			context.environment.setActiveProfiles(profile)
			TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context, "NAIS_CLUSTER_NAME=dev-gcp")
			context.register(AdminRestApi::class.java, SubjectHandlerImpl::class.java, SubjectHandlerTestImpl::class.java)
			context.registerBean(TempCleanupArchiveFailure::class.java, Supplier { mockk<TempCleanupArchiveFailure>() })
			context.registerBean(
				AdminArkiveringsstatusService::class.java,
				Supplier { AdminArkiveringsstatusService(repo, "dev-gcp") },
			)
			context.refresh()

			val adminApi = context.getBean(AdminRestApi::class.java)
			val exception = assertFailsWith<ForbiddenException> {
				adminApi.oppdaterArkiveringsstatus(
					UUID.randomUUID(),
					OppdaterArkiveringsstatusRequest(AdminArkiveringsstatus.Arkivert, "Testdata"),
				)
			}
			assertEquals("Kallende applikasjon har ikke tilgang til å endre arkiveringsstatus", exception.message)
			verify { repo wasNot Called }
		}
	}
}
