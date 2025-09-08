package no.nav.soknad.innsending.consumerapis.skjema

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.util.finnBackupLanguage
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Service
import java.io.IOException
import java.time.Duration
import java.util.*

@Scope(ConfigurableBeanFactory.SCOPE_SINGLETON)
@Service
class HentSkjemaDataConsumer(private val hentSkjemaData: SkjemaClient) {

	private val logger = LoggerFactory.getLogger(javaClass)

	val cache: LoadingCache<String, List<SkjemaOgVedleggsdata>> = Caffeine
		.newBuilder().refreshAfterWrite(Duration.ofHours(1))
		.build { hentSkjemaData.hent() ?: emptyList()}

	// TODO implementere språk avhengig oppslag?
	fun hentSkjemaEllerVedlegg(id: String, spraak: String = "no"): KodeverkSkjema {

		val sanityList = try {
			// Hent fra cache, Cache Loader funksjonen (hentSkjemaData.hent()) blir kalt hvis cache er tom for "sanityList"
			cache.get("sanityList") { hentSkjemaData.hent() ?: emptyList() }
		} catch (e: Exception) {
			logger.warn("Sanity cache er tom, forsøker å lese fra disk")
			initSkjemaDataFromDisk()
		}

		for (data in sanityList) {
			if (id == data.skjemanummer || id == data.vedleggsid) {
				return createKodeverkSkjema(data, spraak, id)
			}
		}

		var message = "Skjema med id = $id ikke funnet. "
		message += if (sanityList.isEmpty()) {
			"Skjema cache er tom"
		} else {
			"Ikke funnet i skjema listen"
		}

		logger.info(message + " Antall skjema/vedleggstyper lest opp = ${sanityList.size}")
		throw BackendErrorException(message)
	}

	private fun createKodeverkSkjema(sanity: SkjemaOgVedleggsdata, spraak: String, id: String): KodeverkSkjema =
		KodeverkSkjema(
			url = getUrl(sanity, spraak),
			skjemanummer = id,
			vedleggsid = sanity.vedleggsid,
			tittel = getTitle(sanity, spraak),
			tema = sanity.tema
		)

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
		} else if ("nn".equals(spraak, true) && !sanity.url_nn.isNullOrBlank()) {
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
