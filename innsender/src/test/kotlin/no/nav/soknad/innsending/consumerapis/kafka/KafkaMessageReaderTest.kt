import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.innsending.consumerapis.kafka.KafkaMessageReader
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.HendelseType
import no.nav.soknad.innsending.exceptions.ResourceNotFoundException
import no.nav.soknad.innsending.repository.domain.models.HendelseDbData
import no.nav.soknad.innsending.repository.domain.models.SoknadDbData
import no.nav.soknad.innsending.service.RepositoryUtils
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.kafka.support.Acknowledgment
import org.springframework.test.context.junit.jupiter.SpringExtension

@ExtendWith(SpringExtension::class)
class KafkaMessageReaderTest {

	var kafkaMessageReader: KafkaMessageReader? = null

	@MockkBean
	lateinit var repo: RepositoryUtils

	@BeforeEach
	fun setup() {
		kafkaMessageReader = KafkaMessageReader(repo)
	}

	@Test
	fun `test successful archiving`() {
		val message = "**Archiving: OK"
		val messageKey = "innsendingsid"
		val soknad = mockk<SoknadDbData>()
		val hendelse = mockk<HendelseDbData>()
		val ack = mockk<Acknowledgment>()
		every { ack.acknowledge() } returns Unit

		every { repo.hentSoknadDb(messageKey) } returns soknad
		every { soknad.innsendingsid } returns "testId"
		every { repo.oppdaterArkiveringsstatus(soknad, ArkiveringsStatus.Arkivert) } returns hendelse

		kafkaMessageReader?.listen(message, messageKey, ack)

		verify(exactly = 1) { repo.hentSoknadDb(messageKey) }
		verify { repo.oppdaterArkiveringsstatus(soknad, ArkiveringsStatus.Arkivert) }
		verify(exactly = 1) { ack.acknowledge() }
	}

	@Test
	fun `test failed archiving`() {
		val message = "**Archiving: FAILED"
		val messageKey = "innsendingsid"
		val soknad = mockk<SoknadDbData>()
		val hendelse = mockk<HendelseDbData>()
		val ack = mockk<Acknowledgment>()
		every { ack.acknowledge() } returns Unit

		every { repo.hentSoknadDb(messageKey) } returns soknad
		every { soknad.innsendingsid } returns "testId"
		every { repo.oppdaterArkiveringsstatus(soknad, ArkiveringsStatus.ArkiveringFeilet) } returns hendelse

		kafkaMessageReader?.listen(message, messageKey, ack)

		verify(exactly = 1) { repo.hentSoknadDb(messageKey) }
		verify { repo.oppdaterArkiveringsstatus(soknad, ArkiveringsStatus.ArkiveringFeilet) }
		verify(exactly = 1) { ack.acknowledge() }
	}

	@Test
	fun `test resource not found`() {
		val message = "**Archiving: OK"
		val messageKey = "innsendingsid"
		val ack = mockk<Acknowledgment>()
		every { ack.acknowledge() } returns Unit

		every { repo.hentSoknadDb(messageKey) } throws ResourceNotFoundException("Not found")

		kafkaMessageReader?.listen(message, messageKey, ack)

		verify(exactly = 1) { repo.hentSoknadDb(messageKey) }
		verify(exactly = 0) { repo.oppdaterArkiveringsstatus(any(), any()) }
		verify(exactly = 1) { ack.acknowledge() }
	}

	@Test
	fun `test general failure trying to fetch soknad`() {
		val message = "**Archiving: OK"
		val messageKey = "innsendingsid"
		val ack = mockk<Acknowledgment>()
		every { ack.acknowledge() } returns Unit

		every { repo.hentSoknadDb(messageKey) } throws RuntimeException("Could not connect to database")

		kafkaMessageReader?.listen(message, messageKey, ack)

		verify(exactly = 1) { repo.hentSoknadDb(messageKey) }
		verify(exactly = 0) { repo.oppdaterArkiveringsstatus(any(), any()) }
		verify(exactly = 0) { ack.acknowledge() }
	}

	@Test
	fun `test irrelevant message`() {
		val message = "dummymessage"
		val messageKey = "innsendingsid"
		val soknad = mockk<SoknadDbData>()
		val ack = mockk<Acknowledgment>()
		every { ack.acknowledge() } returns Unit

		every { repo.hentSoknadDb(messageKey) } returns soknad
		every { soknad.innsendingsid } returns "testId"

		kafkaMessageReader?.listen(message, messageKey, ack)

		verify(exactly = 1) { repo.hentSoknadDb(messageKey) }
		verify(exactly = 0) { repo.oppdaterArkiveringsstatus(any(), any()) }
		verify(exactly = 1) { ack.acknowledge() }
	}

}
