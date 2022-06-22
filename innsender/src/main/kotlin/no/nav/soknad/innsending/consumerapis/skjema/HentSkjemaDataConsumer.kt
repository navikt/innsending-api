package no.nav.soknad.innsending.consumerapis.skjema

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.soknad.innsending.exceptions.SanityException
import no.nav.soknad.innsending.util.finnBackupLanguage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Service
import java.io.IOException
import java.util.*
import kotlin.concurrent.timerTask

@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
@Service
class HentSkjemaDataConsumer(private val hentSkjemaData: SkjemaClient) {

	private val logger = LoggerFactory.getLogger(javaClass)
	private var sanityList: List<SkjemaOgVedleggsdata> = emptyList()

	init {
		Timer().scheduleAtFixedRate(timerTask {
			sanityList = try {
				hentSkjemaData()
			} catch (e: Exception) {
				logger.error("Feil ved henting av skjemadata fra Sanity, ${e.message}")
				initSkjemaDataFromDisk()
			}
			if (sanityList.isEmpty()) sanityList = initSkjemaDataFromDisk()
		}, 0, 3600 * 1000)
	}

	// TODO implementere språk avhengig oppslag?
	fun hentSkjemaEllerVedlegg(id: String, spraak: String = "no"): KodeverkSkjema {
		for (data in sanityList) {
			if (id == data.skjemanummer || id == data.vedleggsid) {
				return createKodeverkSkjema(data, spraak, id)
			}
		}
		val message = "Skjema med id = $id ikke funnet"
		logger.info(message + " Antall skjema/vedleggstyper lest opp = ${sanityList.size}")
		throw SanityException(if (sanityList.isEmpty()) "Skjema cache er tom" else "Ikke funnet i skjema listen", message)
	}

	private fun createKodeverkSkjema(sanity: SkjemaOgVedleggsdata, spraak: String, id: String): KodeverkSkjema {
		val kodeverkSkjema = KodeverkSkjema()
		kodeverkSkjema.url = getUrl(sanity, spraak)
		kodeverkSkjema.skjemanummer = id
		kodeverkSkjema.vedleggsid = sanity.vedleggsid
		kodeverkSkjema.tittel = getTitle(sanity, spraak)
		kodeverkSkjema.tema = sanity.tema
		return kodeverkSkjema
	}

	private fun getTitle(sanity: SkjemaOgVedleggsdata, spraak: String): String? {
		return if ("no".equals(spraak, true))
			sanity.tittel_no
		else if ("nn".equals(spraak, true) && !sanity.tittel_nn.isNullOrBlank()) {
			sanity.tittel_nn
		} else if ("en".equals(spraak, true) && !sanity.tittel_en.isNullOrBlank()) {
			sanity.tittel_en
		} else
			return getTitle(sanity, finnBackupLanguage(spraak))
	}

	private fun getUrl(sanity: SkjemaOgVedleggsdata, spraak: String): String? {
		return if ("no".equals(spraak, true)) {
			sanity.url_no
		}	else if ("nn".equals(spraak, true) && !sanity.url_nn.isNullOrBlank()) {
			sanity.url_nn
		} else if ("en".equals(spraak, true) && !sanity.url_en.isNullOrBlank()) {
			sanity.url_en
		} else if ("de".equals(spraak, true) && !sanity.url_de.isNullOrBlank()) {
			sanity.url_de
		} else if ("fr".equals(spraak, true) && !sanity.url_fr.isNullOrBlank()) {
			sanity.url_fr
		} else if ("es".equals(spraak, true) && !sanity.url_es.isNullOrBlank()) {
			sanity.url_es
		} else if ("se".equals(spraak, true) && !sanity.url_se.isNullOrBlank()) {
			sanity.url_se
		} else if ("pl".equals(spraak, true) && !sanity.url_pl.isNullOrBlank()) {
			sanity.url_pl
		} else
			 return getUrl(sanity, finnBackupLanguage(spraak))
	}

	private fun hentSkjemaData() = hentSkjemaData.hent() ?: emptyList()

	@Throws(IOException::class)
	fun initSkjemaDataFromDisk(): List<SkjemaOgVedleggsdata> {
		val oldSanityResponse = readJsonResponseDataFromDisk()
		return ObjectMapper()
			.readValue(oldSanityResponse, Skjemaer::class.java)
			.skjemaer
			?: emptyList()
	}

	@Throws(IOException::class)
	private fun readJsonResponseDataFromDisk(): String? {
		HentSkjemaDataConsumer::class.java.classLoader
			.getResourceAsStream("sanity.json").use { inputStream ->
				assert(inputStream != null)
				val s: Scanner = Scanner(inputStream!!).useDelimiter("\\A")
				val json = if (s.hasNext()) s.next() else ""
				assert(json != "")
				return json
			}
	}
}
