package no.nav.soknad.innsending.service

import com.ninjasquad.springmockk.MockkBean
import com.ninjasquad.springmockk.MockkSpyBean
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.pdl.PdlInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.PersonDto
import no.nav.soknad.innsending.consumerapis.soknadsmottaker.MottakerInterface
import no.nav.soknad.innsending.exceptions.ExceptionHelper
import no.nav.soknad.innsending.security.SubjectHandlerInterface
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.utils.withTransactions
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.PlatformTransactionManager

/** Shared setup for tests that build InnsendingService by hand against the real database and storage. */
abstract class InnsendingServiceTestBase : ApplicationTest() {

	@Autowired
	protected lateinit var repo: RepositoryUtils

	@Autowired
	protected lateinit var transactionManager: PlatformTransactionManager

	@Autowired
	protected lateinit var innsenderMetrics: InnsenderMetrics

	@Autowired
	lateinit var soknadService: SoknadService

	@Autowired
	protected lateinit var filService: FilService

	@Autowired
	protected lateinit var restConfig: RestConfig

	@Autowired
	protected lateinit var vedleggService: VedleggService

	@Autowired
	protected lateinit var tilleggstonadService: TilleggsstonadService

	@MockkSpyBean
	protected lateinit var ettersendingService: EttersendingService

	@Autowired
	protected lateinit var documentService: DocumentService

	@Autowired
	protected lateinit var exceptionHelper: ExceptionHelper

	val soknadsmottakerAPI = mockk<MottakerInterface>()
	protected val pdlInterface = mockk<PdlInterface>()

	@MockkBean
	protected lateinit var subjectHandler: SubjectHandlerInterface

	fun lagInnsendingService(soknadService: SoknadService): InnsendingService = InnsendingService(
		soknadService = soknadService,
		repo = repo,
		vedleggService = vedleggService,
		tilleggstonadService = tilleggstonadService,
		ettersendingService = ettersendingService,
		filService = filService,
		innsenderMetrics = innsenderMetrics,
		exceptionHelper = exceptionHelper,
		soknadsmottakerAPI = soknadsmottakerAPI,
		restConfig = restConfig,
		pdlInterface = pdlInterface,
		documentService = documentService,
	).let { withTransactions(it, transactionManager) }

	@BeforeEach
	fun setup() {
		clearAllMocks()
		every { pdlInterface.hentPersonData(any()) } returns PersonDto("1234567890", "Kan", null, "Søke")
		every { subjectHandler.getClientId() } returns "application"
	}
}
