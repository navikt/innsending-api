package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.supervision.InnsenderMetrics
import no.nav.soknad.innsending.supervision.PdfFlatteningOutcome
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.util.*

@Component
class CheckAndFormatPdf(
	private val pdfConverter: FileToPdfInterface,
	private val pdfMerger: PdfMerger,
	private val innsenderMetrics: InnsenderMetrics,
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	companion object {
		// Bildekonvertering gjør hver side om til en PNG. Over denne grensen blir filen for stor, så da beholdes originalen.
		const val MAX_SIDER_BILDEKONVERTERING = 50
	}

	/**
	 * Flater ut skjemafelt i en opplastet PDF.
	 *
	 * Gotenberg prøves først. Resultatet godtas bare hvis det er en lesbar PDF med samme sideantall og uten skjemafelt.
	 * Ellers gjøres sidene om til bilder, slik det ble gjort før Gotenberg. Dokumenter over
	 * [MAX_SIDER_BILDEKONVERTERING] sider lagres da uendret.
	 *
	 * Etterkontrollen kan ikke oppdage at en utfylt verdi er tegnet feil eller mangler. Det må testes manuelt.
	 */
	fun flatUtPdf(fil: ByteArray, antallSider: Int, innsendingsId: String? = null): ByteArray {
		val prefix = innsendingsId?.let { "$it: " } ?: ""
		val start = System.currentTimeMillis()

		if (!trengerUtflating(fil)) {
			return registrer(PdfFlatteningOutcome.SKIPPED, prefix, antallSider, fil, fil, start, trace = null)
		}

		val trace = UUID.randomUUID().toString()
		val gotenbergResultat = try {
			pdfConverter.flattenPdf(fil, trace).let { fjernWidgetsUtenUtseende(it, prefix) }
		} catch (e: Exception) {
			logger.warn("${prefix}Utflating av PDF med Gotenberg feilet, gotenbergTrace=$trace", e)
			null
		}

		if (gotenbergResultat != null) {
			val avvik = avvikIUtflatet(gotenbergResultat, antallSider)
			if (avvik == null) {
				return registrer(PdfFlatteningOutcome.GOTENBERG, prefix, antallSider, fil, gotenbergResultat, start, trace)
			}
			logger.warn("${prefix}Resultat fra Gotenberg besto ikke etterkontrollen: $avvik, gotenbergTrace=$trace")
		}

		val fallbackUtfall = if (gotenbergResultat == null) PdfFlatteningOutcome.FALLBACK_ERROR else PdfFlatteningOutcome.FALLBACK_INVALID
		if (antallSider > MAX_SIDER_BILDEKONVERTERING) {
			return registrer(PdfFlatteningOutcome.UNFLATTENED, prefix, antallSider, fil, fil, start, trace)
		}

		val bildekonvertert = try {
			flatUtMedBildekonvertering(fil)
		} catch (e: Exception) {
			// Feilen går videre som før; registreres slik at tilfeller der begge veier feiler synes i metrikken
			innsenderMetrics.incPdfFlattening(PdfFlatteningOutcome.FAILED)
			logger.warn("${prefix}Utflating av PDF: utfall=${PdfFlatteningOutcome.FAILED.label} sider=$antallSider gotenbergTrace=$trace")
			throw e
		}
		return registrer(fallbackUtfall, prefix, antallSider, fil, bildekonvertert, start, trace)
	}

	fun harSkrivbareFelt(input: ByteArray?): Boolean {
		try {
			Loader.loadPDF(input).use { document ->
				return document.documentCatalog.acroForm != null
			}
		} catch (e: Exception) {
			throw BackendErrorException("Feil ved mottak av opplastet fil", e)
		}
	}

	// Et tomt AcroForm-objekt uten felt eller widgets har ingenting som kan flates ut
	private fun trengerUtflating(fil: ByteArray): Boolean {
		try {
			Loader.loadPDF(fil).use { document -> return harFeltEllerWidgets(document) }
		} catch (e: Exception) {
			throw BackendErrorException("Feil ved mottak av opplastet fil", e)
		}
	}

	/**
	 * qpdf flater ikke ut widgets uten lagret utseende (appearance stream), så de blir liggende igjen.
	 * De viser ingenting, verken i dag via bildekonvertering eller etter utflating, og fjernes derfor.
	 * Er resultatet ikke lesbart, returneres det uendret, slik at etterkontrollen avviser det.
	 */
	private fun fjernWidgetsUtenUtseende(resultat: ByteArray, prefix: String): ByteArray {
		val document = try {
			Loader.loadPDF(resultat)
		} catch (e: Exception) {
			return resultat
		}
		document.use {
			var fjernet = 0
			for (page in document.pages) {
				val annotasjoner = page.annotations
				val beholdes = annotasjoner.filterNot { it is PDAnnotationWidget && it.appearance == null }
				if (beholdes.size != annotasjoner.size) {
					fjernet += annotasjoner.size - beholdes.size
					page.annotations = beholdes
				}
			}
			if (fjernet == 0) return resultat

			logger.info("${prefix}Fjernet $fjernet widgets uten utseende etter utflating")
			return ByteArrayOutputStream().use { out ->
				document.save(out)
				out.toByteArray()
			}
		}
	}

	// Returnerer null når resultatet er gyldig, ellers en beskrivelse av avviket uten innhold fra dokumentet
	private fun avvikIUtflatet(resultat: ByteArray, forventetAntallSider: Int): String? =
		try {
			Loader.loadPDF(resultat).use { document ->
				val felt = document.documentCatalog.acroForm?.fields?.size ?: 0
				val widgets = document.pages.sumOf { page -> page.annotations.count { it is PDAnnotationWidget } }
				if (document.numberOfPages == forventetAntallSider && felt == 0 && widgets == 0) null
				else "sider=${document.numberOfPages}/$forventetAntallSider felt=$felt widgets=$widgets"
			}
		} catch (e: Exception) {
			"ikke lesbar PDF (${e.javaClass.simpleName})"
		}

	private fun harFeltEllerWidgets(document: PDDocument): Boolean {
		val harFelt = document.documentCatalog.acroForm?.fields?.isNotEmpty() == true
		return harFelt || document.pages.any { page -> page.annotations.any { it is PDAnnotationWidget } }
	}

	// Dagens løsning før Gotenberg. Sidene slås sammen med PDFBox, slik at vi ikke kaller Gotenberg på nytt etter en feil.
	private fun flatUtMedBildekonvertering(fil: ByteArray): ByteArray {
		val sider = KonverterTilPng().konverterTilPng(fil).map { ConvertImageToPdf().pdfFromImage(it).first }
		return if (sider.size == 1) sider.first() else pdfMerger.mergeWithPDFBox(sider)
	}

	private fun registrer(
		utfall: PdfFlatteningOutcome,
		prefix: String,
		antallSider: Int,
		original: ByteArray,
		resultat: ByteArray,
		start: Long,
		trace: String?,
	): ByteArray {
		innsenderMetrics.incPdfFlattening(utfall)
		logger.info(
			"${prefix}Utflating av PDF: utfall=${utfall.label} sider=$antallSider bytesFor=${original.size} " +
				"bytesEtter=${resultat.size} tidMs=${System.currentTimeMillis() - start} gotenbergTrace=${trace ?: "-"}"
		)
		return resultat
	}
}
