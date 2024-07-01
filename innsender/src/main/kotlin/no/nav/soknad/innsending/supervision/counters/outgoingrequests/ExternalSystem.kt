package no.nav.soknad.innsending.supervision.counters.outgoingrequests

enum class ExternalSystem(val id: String, val methods: List<String>) {
	ARENA("arena", listOf("get_maalgrupper", "get_aktiviteter"))
}
