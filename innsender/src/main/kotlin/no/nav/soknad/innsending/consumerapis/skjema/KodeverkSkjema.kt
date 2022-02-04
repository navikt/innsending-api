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

	fun getUrl(languageCode: Spraak?): String? = when (languageCode) {
		Spraak.NB -> defaultIfNull(url)
		Spraak.NN -> defaultIfNull(urlnynorsk)
		Spraak.EN -> defaultIfNull(urlengelsk)
		Spraak.PL -> defaultIfNull(urlpolsk)
		Spraak.ES -> defaultIfNull(urlspansk)
		Spraak.DE -> defaultIfNull(urltysk)
		Spraak.FR -> defaultIfNull(urlfransk)
		Spraak.SA -> defaultIfNull(urlsamisk)
		else -> url
	}

	private fun defaultIfNull(url: String?): String? {
		return if (StringUtils.isBlank(url)) {
			url
		} else url
	}
}
