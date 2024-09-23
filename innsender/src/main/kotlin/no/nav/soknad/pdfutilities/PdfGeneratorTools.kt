package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.util.models.*
import no.nav.soknad.pdfutilities.utils.PdfUtils
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

class PdfGenerator {

	companion object {
		private val texts: Map<String, PropertyResourceBundle> = mapOf(
			"nb" to PropertyResourceBundle(PdfGenerator::class.java.getResourceAsStream("/tekster/innholdstekster.properties")),
			"nn" to PropertyResourceBundle(PdfGenerator::class.java.getResourceAsStream("/tekster/innholdstekster_nn.properties")),
			"en" to PropertyResourceBundle(PdfGenerator::class.java.getResourceAsStream("/tekster/innholdstekster_en.properties"))
		)
	}


	fun lagKvitteringsSide(
		soknad: DokumentSoknadDto,
		sammensattNavn: String?,
		opplastedeVedlegg: List<VedleggDto>,
		manglendeObligatoriskeVedlegg: List<VedleggDto>
	): ByteArray {
		val sprak = selectLanguage(soknad.spraak)
		val tekster = texts.get(sprak) ?: throw BackendErrorException("Mangler støtte for språk $sprak")

		val vedleggOpplastet = opplastedeVedlegg.filter { !it.erHoveddokument }
		val sendSenere = manglendeObligatoriskeVedlegg
		val alleredeInnsendt =
			innsendteVedlegg(soknad.opprettetDato, soknad.vedleggsListe) + soknad.vedleggsListe.tidligereLevert

		val fnr = soknad.brukerId
		val personInfo = if (sammensattNavn == null) fnr else "$sammensattNavn, $fnr"

		val now = LocalDateTime.now()
		val innsendtTidspunkt = java.lang.String.format(
			tekster.getString("kvittering.erSendt"),
			formaterDato(now),
			formaterKlokke(now)
		)
		val oppsummering = mutableListOf<VedleggsKategori>()
		if (vedleggOpplastet.isNotEmpty()) {
			oppsummering.addFirst(
				mapTilVedleggskategori(
					kategori = tekster.getString("kvittering.vedlegg.sendt"),
					vedlegg = vedleggOpplastet
				)
			)
		}
		if (sendSenere.isNotEmpty()) {
			oppsummering.addLast(
				mapTilVedleggskategori(
					kategori = tekster.getString("kvittering.vedlegg.ikkesendt"),
					vedlegg = sendSenere
				)
			)
		}
		if (soknad.vedleggsListe.skalSendesAvAndre.isNotEmpty()) {
			oppsummering.addLast(
				mapTilVedleggskategori(
					kategori = tekster.getString("kvittering.vedlegg.sendesAvAndre"),
					vedlegg = soknad.vedleggsListe.skalSendesAvAndre
				)
			)
		}
		if (soknad.vedleggsListe.sendesIkke.isNotEmpty()) {
			oppsummering.addLast(
				mapTilVedleggskategori(
					kategori = tekster.getString("kvittering.vedlegg.sendesIkke"),
					vedlegg = soknad.vedleggsListe.sendesIkke
				)
			)
		}
		if (alleredeInnsendt.isNotEmpty()) {
			oppsummering.addLast(
				mapTilVedleggskategori(
					kategori = tekster.getString("kvittering.vedlegg.tidligereInnsendt"),
					vedlegg = alleredeInnsendt
				)
			)
		}
		if (soknad.vedleggsListe.navKanInnhente.isNotEmpty()) {
			oppsummering.addLast(
				mapTilVedleggskategori(
					kategori = tekster.getString("kvittering.vedlegg.navKanHenteDokumentasjon"),
					vedlegg = soknad.vedleggsListe.navKanInnhente
				)
			)
		}
		return PdfGeneratorService().genererKvitteringPdf(
			KvitteringsPdfModel(
				sprak = if (sprak == "en") sprak + "-UK" else sprak + "-NO",
				beskrivelse = tekster.getString("kvittering.beskrivelse"),
				kvitteringHeader = tekster.getString("kvittering.tittel"),
				ettersendelseTittel = if (soknad.erEttersending) tekster.getString("kvittering.ettersendelse.tittel") else null,
				side = tekster.getString("footer.side"),
				av = tekster.getString("footer.av"),
				tittel = soknad.tittel,
				personInfo = personInfo,
				innsendtTidspunkt = innsendtTidspunkt,
				vedleggsListe = oppsummering
			).vasket()
		)
	}

	fun lagForsideEttersending(soknad: DokumentSoknadDto, sammensattNavn: String? = null): ByteArray {

		val sprak = selectLanguage(soknad.spraak)
		val tekster = texts.get(sprak) ?: throw BackendErrorException("Mangler støtte for språk ${soknad.spraak}")
		val fnr = soknad.brukerId
		val personInfo = sammensattNavn ?: ""
		val now = LocalDateTime.now()
		val innsendtTidspunkt = java.lang.String.format(
			tekster.getString("forside.innsendt"),
			formaterDato(now),
			formaterKlokke(now)
		)
		return PdfGeneratorService().genererEttersendingForsidePdf(
			EttersendingForsidePdfModel(
				sprak = if (sprak == "en") sprak + "-UK" else sprak + "-NO",
				beskrivelse = tekster.getString("forside.beskrivelse"),
				ettersendingHeader = tekster.getString("forside.tittel"),
				ettersendelseTittel = tekster.getString("kvittering.ettersendelse.tittel"),
				side = tekster.getString("footer.side"),
				av = tekster.getString("footer.av"),
				tittel = soknad.tittel,
				personInfo = personInfo,
				personIdent = fnr,
				innsendtTidspunkt = innsendtTidspunkt
			)
		)
	}

	private fun mapTilVedleggskategori(kategori: String, vedlegg: List<VedleggDto>): VedleggsKategori {
		return VedleggsKategori(
			kategori = kategori,
			vedlegg = vedlegg.map {
				VedleggMedKommentar(
					vedleggsTittel = it.tittel,
					kommentarTittel = if (it.opplastingsValgKommentarLedetekst == null) null else it.opplastingsValgKommentarLedetekst,
					kommentar = if (it.opplastingsValgKommentarLedetekst == null || it.opplastingsValgKommentar == null) null else it.opplastingsValgKommentar
				).vasket()
			}
		)

	}

	private fun formaterKlokke(now: LocalDateTime): String {
		val formatter = DateTimeFormatter.ofPattern("HH:mm")
		return now.format(formatter)
	}

	private fun formaterDato(now: LocalDateTime): String {
		return now.format(DateTimeFormatter.ofPattern("dd-MM-YYYY"))
	}

	private fun selectLanguage(language: String?): String {
		val defaultLanguage = "nb"
		if (language.isNullOrEmpty()
			|| language.length < 2
			|| !texts.keys.contains(language.substring(0, 2).lowercase())
		)
			return defaultLanguage
		else
			return language.substring(0, 2).lowercase()
	}

}

data class VedleggsKategori(
	val kategori: String,
	val vedlegg: List<VedleggMedKommentar>
)

data class VedleggMedKommentar(
	val vedleggsTittel: String,
	val kommentarTittel: String?,
	val kommentar: String?
) {
	fun vasket(): VedleggMedKommentar {
		return VedleggMedKommentar(
			vedleggsTittel = PdfUtils.fjernSpesielleKarakterer(this.vedleggsTittel) ?: "",
			kommentarTittel = this.kommentarTittel,
			kommentar = PdfUtils.fjernSpesielleKarakterer(this.kommentar)
		)
	}
}

