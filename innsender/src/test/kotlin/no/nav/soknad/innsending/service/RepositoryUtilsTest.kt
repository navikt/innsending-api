package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.ApplicationTest
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
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

class RepositoryUtilsTest : ApplicationTest() {

	@Autowired
	private lateinit var repo: RepositoryUtils

	@Autowired
	private lateinit var soknadService: SoknadService

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
