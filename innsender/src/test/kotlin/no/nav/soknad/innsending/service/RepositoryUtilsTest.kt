package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.PatchVedleggDto
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.util.mapping.mapTilSoknadDb
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.awaitility.Awaitility.await
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class RepositoryUtilsTest : ApplicationTest() {

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var soknadService: SoknadService

	@Autowired
	private lateinit var vedleggService: VedleggService

	@Autowired
	private lateinit var transactionManager: PlatformTransactionManager

	@Autowired
	private lateinit var jdbc: JdbcTemplate

	@Test
	fun `application lock blocks a competing transaction until committed state can be read`() {
		val application = soknadService.opprettNySoknad(Hjelpemetoder.lagDokumentSoknad())
		val submissionId = application.innsendingsId!!
		val lockHeld = CountDownLatch(1)
		val releaseLock = CountDownLatch(1)
		val waitingConnection = CompletableFuture<Int>()
		val executor = Executors.newFixedThreadPool(2)
		val transaction = TransactionTemplate(transactionManager).apply { timeout = 30 }
		try {
			val first = executor.submit {
				transaction.executeWithoutResult {
					val current = repo.hentSoknadDbForUpdate(submissionId)
					repo.lagreSoknad(current.copy(status = SoknadsStatus.KlarForInnsending))
					lockHeld.countDown()
					assertTrue(releaseLock.await(20, TimeUnit.SECONDS), "Timed out waiting to release application lock")
				}
			}
			assertTrue(lockHeld.await(20, TimeUnit.SECONDS), "First transaction did not acquire application lock")
			val second = executor.submit<SoknadsStatus> {
				transaction.execute {
					waitingConnection.complete(jdbc.queryForObject("select pg_backend_pid()", Int::class.java)!!)
					repo.hentSoknadDbForUpdate(submissionId).status
				}!!
			}
			val connectionId = waitingConnection.get(10, TimeUnit.SECONDS)
			// This database-specific test checks actual lock contention, not HTTP scheduling.
			await().atMost(Duration.ofSeconds(10)).until {
				jdbc.queryForObject(
					"select exists(select 1 from pg_stat_activity where pid = ? and wait_event_type = 'Lock')",
					Boolean::class.java,
					connectionId,
				) == true
			}
			releaseLock.countDown()
			first.get(10, TimeUnit.SECONDS)
			assertEquals(SoknadsStatus.KlarForInnsending, second.get(10, TimeUnit.SECONDS))
		} finally {
			releaseLock.countDown()
			executor.shutdownNow()
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Transaction workers did not terminate")
		}
	}

	@Test
	fun `patch acquires the application lock before mutating while application deletion waits`() {
		assertMutationBeforeApplicationDeletion { soknad, vedleggsId ->
			vedleggService.endreVedlegg(PatchVedleggDto(tittel = "Oppdatert tittel"), vedleggsId, soknad)
		}
	}

	@Test
	fun `attachment deletion acquires the application lock before mutating while application deletion waits`() {
		assertMutationBeforeApplicationDeletion { soknad, vedleggsId ->
			vedleggService.slettVedlegg(soknad, vedleggsId)
		}
	}

	@Test
	fun `patch rejects a stale application after application deletion`() {
		assertApplicationDeletionBeforeMutation { soknad, vedleggsId ->
			vedleggService.endreVedlegg(PatchVedleggDto(tittel = "Skal ikke lagres"), vedleggsId, soknad)
		}
	}

	@Test
	fun `attachment deletion rejects a stale application after application deletion`() {
		assertApplicationDeletionBeforeMutation { soknad, vedleggsId ->
			vedleggService.slettVedlegg(soknad, vedleggsId)
		}
	}

	@Test
	fun `patch rejects stale application state after submission`() {
		val (soknad, vedleggsId) = createApplicationWithOptionalAttachment()
		val dbSoknad = repo.hentSoknadDb(soknad.innsendingsId!!)
		repo.lagreSoknad(dbSoknad.copy(status = SoknadsStatus.Innsendt))

		val exception = assertThrows<IllegalActionException> {
			vedleggService.endreVedlegg(PatchVedleggDto(tittel = "Skal ikke lagres"), vedleggsId, soknad)
		}

		assertEquals(
			"Søknad ${soknad.innsendingsId} kan ikke endres da den er innsendt eller slettet. Det kan ikke gjøres endring på en slettet eller innsendt søknad",
			exception.message,
		)
		assertEquals("Annet", repo.hentVedlegg(vedleggsId).tittel)
	}

	@Test
	fun `attachment deletion rejects stale application state after submission`() {
		val (soknad, vedleggsId) = createApplicationWithOptionalAttachment()
		val dbSoknad = repo.hentSoknadDb(soknad.innsendingsId!!)
		repo.lagreSoknad(dbSoknad.copy(status = SoknadsStatus.Innsendt))

		val exception = assertThrows<IllegalActionException> {
			vedleggService.slettVedlegg(soknad, vedleggsId)
		}

		assertEquals(
			"Søknad ${soknad.innsendingsId} kan ikke endres da den allerede er innsendt. Det kan ikke gjøres endring på en slettet eller innsendt søknad.",
			exception.message,
		)
		assertEquals("Annet", repo.hentVedlegg(vedleggsId).tittel)
	}

	@Test
	fun `attachment deletion uses current required status rather than stale dto`() {
		val (soknad, vedleggsId) = createApplicationWithOptionalAttachment()
		val staleAttachment = soknad.vedleggsListe.single { it.id == vedleggsId }
		assertFalse(staleAttachment.erPakrevd)
		repo.lagreVedlegg(repo.hentVedlegg(vedleggsId).copy(erpakrevd = true))

		val exception = assertThrows<IllegalActionException> {
			vedleggService.slettVedlegg(soknad, vedleggsId)
		}

		assertEquals(
			"Kan ikke slette påkrevd vedlegg. Vedlegg som er obligatorisk for søknaden kan ikke slettes av søker",
			exception.message,
		)
		assertTrue(repo.hentVedlegg(vedleggsId).erpakrevd)
	}

	@Test
	fun `attachment deletion uses current main document status rather than stale dto`() {
		val (soknad, _) = createApplicationWithOptionalAttachment()
		val mainDocument = soknad.vedleggsListe.first { it.erHoveddokument && !it.erVariant }
		val mainDocumentId = assertNotNull(mainDocument.id)
		val staleSoknad = soknad.copy(
			vedleggsListe = soknad.vedleggsListe.map {
				if (it.id == mainDocumentId) it.copy(erHoveddokument = false, erPakrevd = false, vedleggsnr = "N6") else it
			},
		)

		val exception = assertThrows<IllegalActionException> {
			vedleggService.slettVedlegg(staleSoknad, mainDocumentId)
		}

		assertEquals(
			"Kan ikke slette hovedskjema på en søknad. Søknaden må alltid ha sitt hovedskjema",
			exception.message,
		)
		assertTrue(repo.hentVedlegg(mainDocumentId).erhoveddokument)
	}

	private fun assertMutationBeforeApplicationDeletion(mutation: (DokumentSoknadDto, Long) -> Unit) {
		val (soknad, vedleggsId) = createApplicationWithOptionalAttachment()
		val executor = Executors.newFixedThreadPool(3)
		val transaction = TransactionTemplate(transactionManager).apply { timeout = 30 }
		val releaseAttachmentLock = CountDownLatch(1)
		val attachmentLockHeld = CountDownLatch(1)
		val mutationPid = CompletableFuture<Int>()
		val deletionPid = CompletableFuture<Int>()
		try {
			val attachmentLock = executor.submit {
				transaction.executeWithoutResult {
					jdbc.queryForObject("select id from vedlegg where id = ? for update", Long::class.java, vedleggsId)
					attachmentLockHeld.countDown()
					assertTrue(releaseAttachmentLock.await(20, TimeUnit.SECONDS), "Timed out waiting to release attachment lock")
				}
			}
			assertTrue(attachmentLockHeld.await(10, TimeUnit.SECONDS), "Transaction did not acquire attachment lock")

			val mutationFuture = executor.submit {
				transaction.executeWithoutResult {
					mutationPid.complete(jdbc.queryForObject("select pg_backend_pid()", Int::class.java)!!)
					mutation(soknad, vedleggsId)
				}
			}
			val mutationConnectionId = mutationPid.get(10, TimeUnit.SECONDS)
			awaitLockWait(mutationConnectionId)

			val deletionFuture = executor.submit {
				transaction.executeWithoutResult {
					deletionPid.complete(jdbc.queryForObject("select pg_backend_pid()", Int::class.java)!!)
					soknadService.slettSoknadAvBruker(soknad)
				}
			}
			val deletionConnectionId = deletionPid.get(10, TimeUnit.SECONDS)
			awaitSoknadLockWait(deletionConnectionId)

			releaseAttachmentLock.countDown()
			attachmentLock.get(10, TimeUnit.SECONDS)
			mutationFuture.get(10, TimeUnit.SECONDS)
			deletionFuture.get(10, TimeUnit.SECONDS)
			assertFalse(repo.existsByInnsendingsId(soknad.innsendingsId!!))
		} finally {
			releaseAttachmentLock.countDown()
			executor.shutdownNow()
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Transaction workers did not terminate")
		}
	}

	private fun assertApplicationDeletionBeforeMutation(mutation: (DokumentSoknadDto, Long) -> Unit) {
		val (soknad, vedleggsId) = createApplicationWithOptionalAttachment()
		val executor = Executors.newFixedThreadPool(2)
		val transaction = TransactionTemplate(transactionManager).apply { timeout = 30 }
		val applicationDeleted = CountDownLatch(1)
		val releaseApplicationDeletion = CountDownLatch(1)
		val mutationPid = CompletableFuture<Int>()
		try {
			val deletionFuture = executor.submit {
				transaction.executeWithoutResult {
					soknadService.slettSoknadAvBruker(soknad)
					applicationDeleted.countDown()
					assertTrue(releaseApplicationDeletion.await(20, TimeUnit.SECONDS), "Timed out waiting to commit application deletion")
				}
			}
			assertTrue(applicationDeleted.await(10, TimeUnit.SECONDS), "Application deletion did not reach its commit gate")

			val mutationFuture = executor.submit {
				transaction.executeWithoutResult {
					mutationPid.complete(jdbc.queryForObject("select pg_backend_pid()", Int::class.java)!!)
					mutation(soknad, vedleggsId)
				}
			}
			val mutationConnectionId = mutationPid.get(10, TimeUnit.SECONDS)
			awaitSoknadLockWait(mutationConnectionId)

			releaseApplicationDeletion.countDown()
			deletionFuture.get(10, TimeUnit.SECONDS)
			val exception = assertThrows<ExecutionException> { mutationFuture.get(10, TimeUnit.SECONDS) }
			assertTrue(exception.cause is ResourceNotFoundException, "Expected deleted application to be rejected, got ${exception.cause}")
			assertFalse(repo.existsByInnsendingsId(soknad.innsendingsId!!))
		} finally {
			releaseApplicationDeletion.countDown()
			executor.shutdownNow()
			assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS), "Transaction workers did not terminate")
		}
	}

	private fun createApplicationWithOptionalAttachment(): Pair<DokumentSoknadDto, Long> {
		val created = soknadService.opprettNySoknad(Hjelpemetoder.lagDokumentSoknad())
		val soknad = soknadService.hentSoknad(created.innsendingsId!!)
		val attachment = vedleggService.leggTilVedlegg(soknad, null)
		return soknadService.hentSoknad(created.innsendingsId!!) to assertNotNull(attachment.id)
	}

	private fun awaitLockWait(connectionId: Int) {
		await().atMost(Duration.ofSeconds(10)).until {
			jdbc.queryForObject(
				"select exists(select 1 from pg_stat_activity where pid = ? and wait_event_type = 'Lock')",
				Boolean::class.java,
				connectionId,
			) == true
		}
	}

	private fun awaitSoknadLockWait(connectionId: Int) {
		await().atMost(Duration.ofSeconds(10)).until {
			jdbc.queryForObject(
				"select exists(select 1 from pg_stat_activity where pid = ? and wait_event_type = 'Lock' and query ilike '%from soknad%')",
				Boolean::class.java,
				connectionId,
			) == true
		}
	}

	@Test
	fun testFailureWhenStatusUpdatedFromInnsendtToUtfylt() {
		val dokumentSoknadDto = Hjelpemetoder.lagDokumentSoknad()
		val soknad = soknadService.opprettNySoknad(dokumentSoknadDto)
		val innsendingsId = soknad.innsendingsId!!
		val dbData = repo.hentSoknadDb(innsendingsId)
		repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.Innsendt, dbData.id))

		val exception = assertThrows<Exception> {
			repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.Utfylt, dbData.id))
		}
		assertTrue { exception.message!!.contains("Feil i lagring av søknad") }
	}

	@Test
	fun testFailureWhenStatusUpdatedFromInnsendtToAutomatiskSlettet() {
		val dokumentSoknadDto = Hjelpemetoder.lagDokumentSoknad()
		val soknad = soknadService.opprettNySoknad(dokumentSoknadDto)
		val innsendingsId = soknad.innsendingsId!!
		val dbData = repo.hentSoknadDb(innsendingsId)
		repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.Innsendt, dbData.id))

		val exception = assertThrows<Exception> {
			repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.AutomatiskSlettet, dbData.id))
		}
		assertTrue { exception.message!!.contains("Feil i lagring av søknad") }
	}

	@Test
	fun testFailureWhenStatusUpdatedFromInnsendtToOpprettet() {
		val dokumentSoknadDto = Hjelpemetoder.lagDokumentSoknad()
		val soknad = soknadService.opprettNySoknad(dokumentSoknadDto)
		val innsendingsId = soknad.innsendingsId!!
		val dbData = repo.hentSoknadDb(innsendingsId)
		repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.Innsendt, dbData.id))

		val exception = assertThrows<Exception> {
			repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.Opprettet, dbData.id))
		}
		assertTrue { exception.message!!.contains("Feil i lagring av søknad") }
	}

	@Test
	fun testFailureWhenStatusUpdatedFromInnsendtToSlettetAvBruker() {
		val dokumentSoknadDto = Hjelpemetoder.lagDokumentSoknad()
		val soknad = soknadService.opprettNySoknad(dokumentSoknadDto)
		val innsendingsId = soknad.innsendingsId!!
		val dbData = repo.hentSoknadDb(innsendingsId)
		repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.Innsendt, dbData.id))

		val exception = assertThrows<Exception> {
			repo.lagreSoknad(mapTilSoknadDb(dokumentSoknadDto, innsendingsId, SoknadsStatus.SlettetAvBruker, dbData.id))
		}
		assertTrue { exception.message!!.contains("Feil i lagring av søknad") }
	}
}
