package no.nav.soknad.innsending.repository.domain.models

data class ApplicationOwner(val id: String?, val idType: String?) {

	override fun toString(): String = "ApplicationOwner(id=***, idType=***)"

	companion object {
		fun person(id: String): ApplicationOwner = ApplicationOwner(id, "FNR")
	}
}
