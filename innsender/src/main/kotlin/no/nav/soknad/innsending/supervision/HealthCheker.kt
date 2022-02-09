package no.nav.soknad.innsending.supervision

interface HealthCheker {

	fun ping(): Ping?

	data class Ping(
		val metadata: PingMetadata? = null,
		val feilmelding: String? = null,
		val feil: Throwable? = null,
		var responstid: Long = -1
	) {
		fun harFeil(): Boolean {
			return feil != null || feilmelding != null
		}

		fun erVellykket(): Boolean {
			return !harFeil()
		}
	}

	data class PingMetadata(
		val endepunkt: String,
		val beskrivelse: String,
		val isKritisk: Boolean
	)

	companion object {
		/**
		 * @param metadata Metadata om den pingbare-ressursen. Inneholder endepunkt, beskrivelse og om det er
		 * en kritisk avhengighet eller ikke.
		 * @return Et vellykket pingresultat som kan bruks til generering av selftester.
		 */
		fun lyktes(metadata: PingMetadata): Ping {
			return Ping(metadata)
		}

		/**
		 * @param metadata Metadata om den pingbare-ressursen. Inneholder endepunkt, beskrivelse og om det er
		 * en kritisk avhengighet eller ikke.
		 * @param feil     Exceptionen som trigget feilen. I selftestene blir stacktracen vist om denne er lagt ved.
		 * @return Et feilet pingresultat som kan bruks til generering av selftester.
		 */
		fun feilet(metadata: PingMetadata, feil: Throwable?): Ping {
			return Ping(metadata, feil = feil)
		}

		/**
		 * @param metadata    Metadata om den pingbare-ressursen. Inneholder endepunkt, beskrivelse og om det er
		 * en kritisk avhengighet eller ikke.
		 * @param feilmelding En beskrivende feilmelding av hva som er galt.
		 * @return Et feilet pingresultat som kan bruks til generering av selftester.
		 */
		fun feilet(metadata: PingMetadata, feilmelding: String?): Ping {
			return Ping(metadata, feilmelding = feilmelding)
		}

		/**
		 * @param metadata    Metadata om den pingbare-ressursen. Inneholder endepunkt, beskrivelse og om det er
		 * en kritisk avhengighet eller ikke.
		 * @param feilmelding En beskrivende feilmelding av hva som er galt.
		 * @param feil        Exceptionen som trigget feilen. I selftestene blir stacktracen vist om denne er lagt ved.
		 * @return Et feilet pingresultat som kan bruks til generering av selftester.
		 */
		fun feilet(metadata: PingMetadata, feilmelding: String?, feil: Throwable?): Ping {
			return Ping(metadata, feilmelding = feilmelding, feil = feil)
		}
	}

}
