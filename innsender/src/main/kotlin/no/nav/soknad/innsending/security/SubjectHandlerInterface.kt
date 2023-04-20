package no.nav.soknad.innsending.security

interface SubjectHandlerInterface {
	fun getConsumerId(): String
	fun getUserIdFromToken(): String
	fun getToken(): String
}
