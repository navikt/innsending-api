package no.nav.soknad.innsending.utils

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.scheduling.annotation.Async
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.TransactionStatus
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TransactionalTestProxyTest {
	private val manager = mockk<PlatformTransactionManager>(relaxed = true)
	private val status = mockk<TransactionStatus>(relaxed = true)

	init {
		every { manager.getTransaction(any()) } returns status
	}

	@Test
	fun `transaction annotations are applied and successful calls commit`() {
		val service = withTransactions(ExampleService(), manager)

		assertEquals("result", service.read())

		verify(exactly = 1) {
			manager.getTransaction(match {
				it != null && it.isReadOnly && it.timeout == 7 &&
					it.propagationBehavior == TransactionDefinition.PROPAGATION_REQUIRED
			})
		}
		verify(exactly = 1) { manager.commit(status) }
		verify(exactly = 0) { manager.rollback(any()) }
	}

	@Test
	fun `runtime failures roll back and preserve the original exception`() {
		val service = withTransactions(ExampleService(), manager)
		val failure = IllegalStateException("Expected test failure")

		assertSame(failure, assertThrows<IllegalStateException> { service.fail(failure) })

		verify(exactly = 1) { manager.rollback(status) }
		verify(exactly = 0) { manager.commit(any()) }
	}

	@Test
	fun `methods without transaction annotations do not start transactions`() {
		val service = withTransactions(ExampleService(), manager)

		assertEquals("plain", service.plain())

		verify(exactly = 0) { manager.getTransaction(any()) }
		verify(exactly = 0) { manager.commit(any()) }
	}

	@Test
	fun `another service type retains its return type and async calls stay on the caller thread`() {
		val service = withTransactions(AnotherService(), manager)

		assertSame(Thread.currentThread(), service.callingThread().join())

		verify(exactly = 1) { manager.getTransaction(any()) }
		verify(exactly = 1) { manager.commit(status) }
	}

	open class ExampleService {
		@Transactional(readOnly = true, timeout = 7)
		open fun read(): String = "result"

		@Transactional
		open fun fail(failure: IllegalStateException): Unit = throw failure

		open fun plain(): String = "plain"
	}

	open class AnotherService {
		@Async
		@Transactional
		open fun callingThread(): CompletableFuture<Thread> = CompletableFuture.completedFuture(Thread.currentThread())
	}
}
