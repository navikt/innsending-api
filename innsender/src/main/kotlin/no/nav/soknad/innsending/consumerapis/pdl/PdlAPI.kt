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
@Profile("dev | prod")
@Qualifier("pdl")
class PdlAPI(private val restConfig: RestConfig): PdlInterface, HealthRequestInterface {

	override fun ping(): String {
		//healthApi.ping()
		return "pong"
	}
	override fun isReady(): String {
		//healthApi.isReady()
		return "ok"
	}
	override fun isAlive(): String {
		//healthApi.isAlive()
		return "ok"
	}

	override fun hentPersonData(brukerId: String): PersonDto? {
		val personDto = dummyPersonDtos.get(brukerId) // TODO erstatt med kall til PDL
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
	listOf(PersonIdent("12345678906", "FOLKEREGISTERIDENT", false),PersonIdent("12345678905", "AKTORID", true))
	)

	private val dummyPersonDtos = mapOf(
		testpersonid to PersonDto(
			listOf(NavnDto("F1", null, "E1",
				MetadataDto("FOLKEREGISTERET", listOf(EndringDto("FOLKEREGISTERET", LocalDateTime.now(),""))),
				FolkeregisterMetadataDto(LocalDateTime.now(), "FOLKEREGISTERET")))),
		"12345678902" to PersonDto(
			listOf(NavnDto("F1", null, "E1",
				MetadataDto("FOLKEREGISTERET", listOf(EndringDto("FOLKEREGISTERET", LocalDateTime.now(),""))),
				FolkeregisterMetadataDto(LocalDateTime.now(), "FOLKEREGISTERET")))),
		"12345678903" to PersonDto(
			listOf(NavnDto("F3", null, "E3",
				MetadataDto("FOLKEREGISTERET", listOf(EndringDto("FOLKEREGISTERET", LocalDateTime.now(),""))),
				FolkeregisterMetadataDto(LocalDateTime.now(), "FOLKEREGISTERET")))),
		"12345678904" to PersonDto(
			listOf(NavnDto("F4", null, "E4",
				MetadataDto("NAV", listOf(EndringDto("NAV", LocalDateTime.now(),""))), null))),
		"12345678905" to PersonDto(
			listOf(NavnDto("F5", null, "E5",
				MetadataDto("NAV", listOf(EndringDto("NAV", LocalDateTime.now(),""))), null))),
		"12345678906" to PersonDto(
			listOf(NavnDto("F6", null, "E6",
				MetadataDto("FOLKEREGISTERET", listOf(EndringDto("FOLKEREGISTERET", LocalDateTime.now(),""))),
				FolkeregisterMetadataDto(LocalDateTime.now(), "FOLKEREGISTERET"))))
	)
}
