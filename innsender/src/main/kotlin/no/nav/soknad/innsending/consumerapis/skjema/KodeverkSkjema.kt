package no.nav.soknad.innsending.consumerapis.skjema

import org.apache.commons.lang3.StringUtils

data class KodeverkSkjema (val tittel: String? = null, val beskrivelse: String? = null, val skjemanummer: String? = null, val vedleggsid: String? = null, val tema: String? = null, val url: String? = null) {
	var urlengelsk: String? = null
	var urlnynorsk: String? = null
	var urlpolsk: String? = null
	var urlfransk: String? = null
	var urlspansk: String? = null
	var urltysk: String? = null
	var urlsamisk: String? = null
	var gosysId: String? = null

	private fun defaultIfNull(url: String?): String? {
		return if (StringUtils.isBlank(url)) {
			url
		} else url
	}
}
