package no.nav.soknad.innsending.consumerapis.pdl

import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.*
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.util.testpersonid
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
@Profile("spring | test | docker | default")
@Qualifier("pdl")
class PdlAPITest(private val restConfig: RestConfig): PdlInterface, HealthRequestInterface {

	override fun ping(): String {
		return "pong"
	}
	override fun isReady(): String {
		return "ok"
	}
	override fun isAlive(): String {
		return "ok"
	}

	override fun hentPersonData(brukerId: String): PersonDto? {
		val personDto = dummyPersonDtos.get(brukerId)
		if (personDto != null) return personDto
		throw BackendErrorException("Pålogget bruker $brukerId ikke funnet i PDL", "Problem med å hente opp brukerdata")
	}

	override fun hentPersonIdents(brukerId: String): List<PersonIdent> {
		return dummyHentBrukerIdenter(brukerId)
	}

	private fun dummyHentBrukerIdenter(brukerId: String): List<PersonIdent> {
		return dummyIdents.filter {inneholderBrukerId(brukerId, it)}.toList().flatten()
	}

	private fun inneholderBrukerId(brukerId: String, liste: List<PersonIdent>): Boolean {
		return liste.any { it.ident == brukerId }
	}

	private val dummyIdents = listOf(
		listOf(PersonIdent(testpersonid, "FOLKEREGISTERIDENT", false), PersonIdent("12345678902","FOLKEREGISTERIDENT", true)),
		listOf(PersonIdent("12345678903", "FOLKEREGISTERIDENT", false)),
		listOf(PersonIdent("12345678904", "NPID", false)),
		listOf(
			PersonIdent("12345678906", "FOLKEREGISTERIDENT", false),
			PersonIdent("12345678905", "AKTORID", true)
		)
	)

	private val dummyPersonDtos = mapOf(
		testpersonid to PersonDto(
			listOf(Navn("F1", null, "E1")),
			listOf(
				Folkeregisteridentifikator("12345678901","FOLKEREGISTERIDENT", "gjeldende"),
				Folkeregisteridentifikator("12345678902","FOLKEREGISTERIDENT", "utgått")
				)
		),
		"12345678902" to PersonDto(
			listOf(Navn("F1", null, "E1")),
			listOf(
				Folkeregisteridentifikator("12345678901","FOLKEREGISTERIDENT", "gjeldende"),
				Folkeregisteridentifikator("12345678902","FOLKEREGISTERIDENT", "utgått")
			)
		),
		"12345678903" to PersonDto(
				listOf(Navn("F3", null, "E3")),
				listOf(
					Folkeregisteridentifikator("12345678903","FOLKEREGISTERIDENT", "gjeldende")
				)
			),
		"12345678904" to PersonDto(
			listOf(Navn("F4", null, "E4")),
			listOf(
				Folkeregisteridentifikator("12345678904","NPID", "gjeldende")
			)
		),
		"12345678905" to PersonDto(
			listOf(Navn("F5", null, "E5")),
			listOf(
				Folkeregisteridentifikator("12345678906","FOLKEREGISTERIDENT", "gjeldende"),
				Folkeregisteridentifikator("12345678905","AKTORID", "gjeldende")
			)
		),
		"12345678906" to PersonDto(
			listOf(Navn("F5", null, "E5")),
			listOf(
				Folkeregisteridentifikator("12345678906","FOLKEREGISTERIDENT", "gjeldende"),
				Folkeregisteridentifikator("12345678905","AKTORID", "gjeldende")
			)
		)
	)
}
