package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.util.models.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*


private const val FONT_EKSTRA_STOR = 18
private const val FONT_SUB_HEADER = 16
private const val FONT_STOR = 14
private const val FONT_LITEN_HEADER = 13
private const val FONT_DOCUMENT_HEADER = 11
private const val FONT_VANLIG = 10
private const val FONT_LITEN = 9
private const val FONT_INFORMASJON = 11
private const val LINJEAVSTAND = 1.4f
private const val LINJEAVSTAND_HEADER = 1f

const val INNRYKK = 50f


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
		val sendesAvAndre = soknad.vedleggsListe.skalSendesAvAndre

		val fnr = soknad.brukerId
		val personInfo = if (sammensattNavn == null) fnr else "$sammensattNavn, $fnr"

		val antallLastetOpp = vedleggOpplastet.size
		val now = LocalDateTime.now()
		val antallInnsendt = java.lang.String.format(
			tekster.getString("kvittering.erSendt"),
			antallLastetOpp,
			alleredeInnsendt.size + sendSenere.size + sendesAvAndre.size + antallLastetOpp,
			formaterDato(now),
			formaterKlokke(now)
		)
		val oppsummering = mutableListOf<VedleggsKategori>()
		if (!antallInnsendt.isNullOrEmpty()) {
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
				kvitteringHeader = tekster.getString("kvittering.tittel"),
				ettersendelseTittel = if (soknad.erEttersending) tekster.getString("kvittering.ettersendelse.tittel") else null,
				side = tekster.getString("footer.side"),
				av = tekster.getString("footer.av"),
				tittel = soknad.tittel,
				personInfo = personInfo,
				antallInnsendt = antallInnsendt,
				data = oppsummering
			)
		)
	}

	fun lagForsideEttersending(soknad: DokumentSoknadDto, sammensattNavn: String? = null): ByteArray {

		val sprak = selectLanguage(soknad.spraak)
		val tekster = texts.get(sprak) ?: throw BackendErrorException("Mangler støtte for språk ${soknad.spraak}")
		val fnr = soknad.brukerId
		val personInfo = if (sammensattNavn == null) fnr else "$sammensattNavn, $fnr"
		val now = LocalDateTime.now()
		val oppsummering = java.lang.String.format(
			tekster.getString("forside.innsendt"),
			formaterDato(now),
			formaterKlokke(now)
		)
		return PdfGeneratorService().genererEttersendingForsidePdf(
			EttersendingForsidePdfModel(
				ettersendingHeader = tekster.getString("forside.tittel"),
				ettersendelseTittel = tekster.getString("kvittering.ettersendelse.tittel"),
				side = tekster.getString("footer.side"),
				av = tekster.getString("footer.av"),
				tittel = soknad.tittel,
				personInfo = personInfo,
				oppsummering = oppsummering
			)
		)
	}

	private fun mapTilVedleggskategori(kategori: String, vedlegg: List<VedleggDto>): VedleggsKategori {
		return VedleggsKategori(
			kategori = kategori,
			vedlegg = vedlegg.map {
				VedleggMedKommentar(
					vedleggsTittel = it.tittel,
					kommentar = if (it.opplastingsValgKommentarLedetekst == null) null else it.opplastingsValgKommentarLedetekst + ":" + it.opplastingsValgKommentar
				)
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
	val kommentar: String? // <kommentarTittel>: <kommentar>
)

