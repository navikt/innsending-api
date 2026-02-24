package no.nav.soknad.innsending.cleanup

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.service.DocumentService
import no.nav.soknad.innsending.service.RepositoryUtils
import no.nav.soknad.innsending.service.fillager.FileStorageNamespace
import no.nav.soknad.innsending.util.stringextensions.toUUID
import no.nav.soknad.innsending.utils.Hjelpemetoder
import no.nav.soknad.innsending.utils.asResource
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID
import kotlin.test.assertEquals

class DeleteUnsubmittedNologinApplicationsJobTest : ApplicationTest() {

	@Autowired
	lateinit var job: DeleteUnsubmittedNologinApplicationsJob

	@Autowired
	private lateinit var documentService: DocumentService

	@MockkBean
	private lateinit var leaderSelectionUtility: LeaderSelectionUtility

	@MockkBean
	private lateinit var repo: RepositoryUtils

	@BeforeEach
	fun setup() {
		every { leaderSelectionUtility.isLeader() } returns true
		every { repo.existsByInnsendingsId(any()) } returns false
	}

	@Test
	fun `should not delete files if innsending exists in database`() {
		every { repo.existsByInnsendingsId(any()) } returns true
		val innsendingsId = UUID.randomUUID()

		val file = documentService.saveAttachment(
			FileStorageNamespace.NOLOGIN,
			Hjelpemetoder.getBytesFromFile("/litenPdf.pdf").asResource(),
			"M2",
			innsendingsId
		)

		job.runWithOlderThanDays(-1)

		assertNotNull(
			documentService.getFile(
				FileStorageNamespace.NOLOGIN,
				innsendingsId,
				file.filId.toUUID()
			)
		)
	}

	@ParameterizedTest
	@MethodSource("cutoffParams")
	fun `should only delete files older than given cutoff`(
		olderThanDays: Int,
		shouldBeDeleted: Boolean,
	) {
		val innsendingsId = UUID.randomUUID()

		val files = listOf("M2", "K2").map { vedleggId ->
			documentService.saveAttachment(
				FileStorageNamespace.NOLOGIN,
				Hjelpemetoder.getBytesFromFile("/litenPdf.pdf").asResource(),
				vedleggId,
				innsendingsId
			)
		}

		job.runWithOlderThanDays(olderThanDays)

		files.forEach { file ->
			val fileExists = documentService.getFile(
				FileStorageNamespace.NOLOGIN,
				innsendingsId,
				file.filId.toUUID()
			) != null
			assertEquals(shouldBeDeleted, !fileExists)
		}
	}

	companion object {
		@JvmStatic
		fun cutoffParams() = listOf(
			Arguments.of( -2, true), // Cutoff om 2 dager, så alle skal slettes
			Arguments.of( -1, true), // Cutoff om 1 dag, så alle skal slettes
			Arguments.of( 0, false), // Cutoff i dag (midnatt), så ingen skal slettes
			Arguments.of(1, false), // Cutoff i går (midnatt), så ingen skal slettes
		)
	}

}
