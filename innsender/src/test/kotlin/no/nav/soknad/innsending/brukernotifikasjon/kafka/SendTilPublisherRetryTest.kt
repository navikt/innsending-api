package no.nav.soknad.innsending.brukernotifikasjon.kafka

import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.NotificationInfo
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.innsending.ApplicationTest
import no.nav.soknad.innsending.brukernotifikasjon.BrukernotifikasjonPublisher
import no.nav.soknad.innsending.config.BrukerNotifikasjonConfig
import no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher.PublisherInterface
import no.nav.soknad.innsending.model.SoknadType
import no.nav.soknad.innsending.utils.Hjelpemetoder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean

class SendTilPublisherRetryTest : ApplicationTest() {

	@Autowired
	private val notifikasjonConfig: BrukerNotifikasjonConfig = BrukerNotifikasjonConfig()

	@SpyBean
	private lateinit var sendTilPublisher: PublisherInterface

	private var brukernotifikasjonPublisher: BrukernotifikasjonPublisher? = null
	private val defaultSkjemanr = "NAV 55-00.60"
	private val defaultTema = "BID"
	private val defaultTittel = "Avtale om barnebidrag"

	@BeforeEach
	fun setUp() {
		brukernotifikasjonPublisher = BrukernotifikasjonPublisher(notifikasjonConfig, sendTilPublisher)
	}


	@Test
	fun `sjekk at notifikasjonsmelding resendes hvis den feiler`() {
		val innsendingsid = "123456"
		val skjemanr = defaultSkjemanr
		val tema = defaultTema
		val spraak = "no"
		val personId = "12125912345"
		val tittel = "Dokumentasjon av utdanning"
		val id = 1L

		val dokumentSoknad = Hjelpemetoder.lagDokumentSoknad(
			brukerId = personId,
			skjemanr = skjemanr,
			spraak = spraak,
			tittel = tittel,
			tema = tema,
			id = id,
			innsendingsid = innsendingsid,
			soknadstype = SoknadType.soknad
		)

		val soknadRef = SoknadRef(
			dokumentSoknad.innsendingsId!!,
			false,
			dokumentSoknad.innsendingsId!!,
			dokumentSoknad.brukerId,
			dokumentSoknad.opprettetDato,
			erSystemGenerert = dokumentSoknad.erSystemGenerert ?: false
		)

		val brukernotifikasjonInfo = NotificationInfo(
			notifikasjonsTittel = dokumentSoknad.tittel,
			lenke = "http://localhost:3001/fyllut/nav550060/oppsummering?sub=digital&innsendingsId=123456",
			antallAktiveDager = 28,
			eksternVarsling = emptyList()
		)
		val notificationInfo = AddNotification(soknadRef, brukernotifikasjonInfo)

		Mockito.doThrow(RuntimeException::class.java).`when`(sendTilPublisher)
			.opprettBrukernotifikasjon(notificationInfo)

		brukernotifikasjonPublisher?.soknadStatusChange(dokumentSoknad)

		Mockito.verify(sendTilPublisher, Mockito.times(3)).opprettBrukernotifikasjon(notificationInfo)

	}

}
