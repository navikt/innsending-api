package no.nav.soknad.innsending.consumerapis.skjema

import org.apache.commons.lang3.StringUtils

class KodeverkSkjema {
	var tittel: String? = null
	var url: String? = null
	var urlengelsk: String? = null
	var urlnynorsk: String? = null
	var urlpolsk: String? = null
	var urlfransk: String? = null
	var urlspansk: String? = null
	var urltysk: String? = null
	var urlsamisk: String? = null
	var vedleggsid: String? = null
	var beskrivelse: String? = null
	var skjemanummer: String? = null
	var tema: String? = null
	var gosysId: String? = null

	private fun defaultIfNull(url: String?): String? {
		return if (StringUtils.isBlank(url)) {
			url
		} else url
	}
}
