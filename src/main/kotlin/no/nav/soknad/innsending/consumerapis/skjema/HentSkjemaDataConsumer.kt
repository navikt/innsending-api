package no.nav.soknad.innsending.consumerapis.skjema

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.io.IOException
import java.util.*

@Component
class HentSkjemaDataConsumer {

    private var sanityList: List<SkjemaOgVedleggsdata>

    init {
        val fromDisk = initSkjemaDataFromDisk()
        sanityList = if (fromDisk != null) fromDisk else emptyList()
    }


    // TODO implementere språk avhengig oppslag?
    fun hentSkjemaEllerVedlegg(id: String, spraak: String?): KodeverkSkjema {
        for (data in sanityList) {
            if (id == data.skjemanummer || id == data.vedleggsid) {
                return createKodeverkSkjema(data, spraak)
            }
        }
        throw RuntimeException("Skjema med id = ${id} ikke funnet")
    }

    private fun createKodeverkSkjema(sanity: SkjemaOgVedleggsdata, spraak: String?): KodeverkSkjema {
        val kodeverkSkjema = KodeverkSkjema()
        kodeverkSkjema.url = getUrl(sanity, spraak)
        kodeverkSkjema.skjemanummer = sanity.skjemanummer
        kodeverkSkjema.vedleggsid = sanity.vedleggsid
        kodeverkSkjema.tittel = getTitle(sanity, spraak)
        kodeverkSkjema.tema = sanity.tema
        return kodeverkSkjema
    }

    private fun getTitle(sanity: SkjemaOgVedleggsdata, spraak: String?): String? {
        if ("nn".equals(spraak,true) && !sanity.tittel_nn.isNullOrBlank()) {
            return sanity.tittel_nn
        } else if ("en".equals(spraak, true) && !sanity.tittel_en.isNullOrBlank()) {
            return sanity.tittel_en
        } else
            return sanity.tittel
    }

    private fun getUrl(sanity: SkjemaOgVedleggsdata, spraak: String?): String? {
        if ("nn".equals(spraak,true) && !sanity.url_nn.isNullOrBlank()) {
            return sanity.url_nn
        } else if ("en".equals(spraak, true) && !sanity.url_en.isNullOrBlank()) {
            return sanity.url_en
        } else
            return sanity.url_en
    }

    @Throws(IOException::class)
    private fun initSkjemaDataFromDisk(): List<SkjemaOgVedleggsdata>? {
        val oldSanityResponse: String? = readJsonResponseDataFromDisk()
        val jsonMapper = ObjectMapper()
        return jsonMapper.readValue(
            oldSanityResponse,
            Skjemaer::class.java
        ).skjemaer
    }

    @Throws(IOException::class)
    private fun readJsonResponseDataFromDisk(): String? {
        HentSkjemaDataConsumer::class.java.getClassLoader()
            .getResourceAsStream("sanity.json").use { `is` ->
                assert(`is` != null)
                val s: Scanner = Scanner(`is`).useDelimiter("\\A")
                val json = if (s.hasNext()) s.next() else ""
                assert("" != json)
                return json
            }
    }


}