package no.nav.soknad.innsending.brukernotifikasjon.kafka

import no.nav.brukernotifikasjon.schemas.Beskjed
import no.nav.soknad.innsending.repository.OpplastingsStatus
import org.joda.time.DateTime
import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

internal class BrukernotifikasjonPublisherTest {


/*

	@Test
	fun `sjekk melding om ny dokumentinnsending blir publisert`() {

		val henvendelsesId = "123456"
		val forrigeHenvendelse = null
		val ettersendelse = false
		val dokumentinnsending = true
		val personId = "12125912345"
		val tema = "PEN"
		val endringsDato = DateTime.now()
		val status = OpplastingsStatus.LASTET_OPP
		val tittel = "Dokumentasjon av utdanning"
		val xml = lagMetadataliste(false, "{\"tittel\":\"$tittel\",\"tema\":\"$tema\"}")

		val message = slot<Beskjed>()
		every { kafkaPublisher.putApplicationMessageOnTopic(any(), capture(message)) } returns Unit

		soknadStatusChangePublisher.soknadStatusChange(henvendelsesId, forrigeHenvendelse, ettersendelse, dokumentinnsending, personId, tema, endringsDato, status, marshal(xml))

		assertTrue(message.isCaptured)
		assertEquals(personId, message.captured.getFodselsnummer())
		assertEquals(henvendelsesId, message.captured.getGrupperingsId())
		assertTrue(message.captured.getTekst().contains(tittel))
		assertEquals(soknadStatusChangePublisher.tittelPrefixNySoknad + tittel, message.captured.getTekst())
		assertEquals("https://localhost" + soknadStatusChangePublisher.linkDokumentinnsending + henvendelsesId, message.captured.getLink())

	}
*/

}
