package no.nav.soknad.innsending.consumerapis.pdl.dto

data class PersonIdent(
	val ident: String,
	val gruppe: String, // FOLKEREGISTERIDENT,AKTORID,NPID
	val historisk: Boolean
)
