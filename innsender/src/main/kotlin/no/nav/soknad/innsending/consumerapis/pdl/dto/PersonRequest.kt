package no.nav.soknad.innsending.consumerapis.pdl.dto

enum class IdentGruppe {
	FOLKEREGISTERIDENT, NPID, AKTORID
}

data class GraphqlQuery(
	val query: String,
	val variables: Variables
)

data class PersonGraphqlQuery (
	val query: String,
	val variables: HentPersonVariabler
)

data class HentPersonVariabler(
	val ident: String,
	val historikk: Boolean
)

data class Variables(
	val ident: String,
	val historikk: Boolean,
	val grupper: List<IdentGruppe> = listOf(IdentGruppe.AKTORID, IdentGruppe.FOLKEREGISTERIDENT, IdentGruppe.NPID)
)

fun hentPersonQuery(fnr: String, historikk: Boolean): PersonGraphqlQuery {
	val query = GraphqlQuery::class.java.getResource("graphql/pdl-person-query.graphql").readText().replace("[\n\r]", "")
	return PersonGraphqlQuery(query, HentPersonVariabler(fnr, historikk))
}
