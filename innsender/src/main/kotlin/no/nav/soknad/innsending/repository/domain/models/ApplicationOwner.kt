package no.nav.soknad.innsending.repository.domain.models

data class ApplicationOwner(val id: String?, val idType: String?) {

	@get:com.fasterxml.jackson.annotation.JsonIgnore
	val personId: String?
		get() = id?.takeIf { idType == "FNR" && PERSON_ID.matches(it) }

	override fun toString(): String = "ApplicationOwner(id=***, idType=***)"

	companion object {
		private val PERSON_ID = Regex("[0-9]{11}")
		fun person(id: String): ApplicationOwner = ApplicationOwner(id, "FNR")
	}
}
