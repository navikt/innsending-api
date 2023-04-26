package no.nav.soknad.innsending.consumerapis.pdl

import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.consumerapis.pdl.dto.*
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.util.testpersonid
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("spring | docker | default")
@Qualifier("pdl")
class PdlAPITest : PdlInterface, HealthRequestInterface {

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
		val personDto = dummyPersonDtos[brukerId]
		if (personDto != null) return personDto
		throw BackendErrorException(
			"Pålogget bruker $brukerId ikke funnet i PDL", "Problem med å hente opp brukerdata",
			"errorCode.backendError.pdlError"
		)
	}

	override fun hentPersonIdents(brukerId: String): List<IdentDto> {
		return dummyHentBrukerIdenter(brukerId)
	}

	private fun dummyHentBrukerIdenter(brukerId: String): List<IdentDto> {
		return dummyIdents.filter { inneholderBrukerId(brukerId, it) }.flatten()
	}

	private fun inneholderBrukerId(brukerId: String, liste: List<IdentDto>): Boolean {
		return liste.any { it.ident == brukerId }
	}

	private val dummyIdents = listOf(
		listOf(IdentDto(testpersonid, "FOLKEREGISTERIDENT", false), IdentDto("12345678902", "FOLKEREGISTERIDENT", true)),
		listOf(IdentDto("12345678903", "FOLKEREGISTERIDENT", false)),
		listOf(IdentDto("12345678904", "NPID", false)),
		listOf(
			IdentDto("12345678906", "FOLKEREGISTERIDENT", false),
			IdentDto("12345678905", "AKTORID", true)
		)
	)

	private val dummyPersonDtos = mapOf(
		testpersonid to PersonDto(
			testpersonid,
			"F1", null, "E1"
		),
		"12345678902" to PersonDto(
			testpersonid,
			"F1", null, "E1"
		),
		"12345678903" to PersonDto(
			"12345678903",
			"F3", null, "E3"
		),
		"12345678904" to PersonDto(
			"12345678904",
			"F4", null, "E4"
		),
		"12345678905" to PersonDto(
			"12345678905",
			"F5", null, "E5"
		),
		"12345678906" to PersonDto(
			"12345678906",
			"F5", null, "E5"
		)
	)
}
