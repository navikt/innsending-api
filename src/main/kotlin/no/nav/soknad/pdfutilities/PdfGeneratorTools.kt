package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.repository.OpplastingsStatus
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.*

private val FONT_HEADER: PDFont = PDType1Font.HELVETICA_BOLD
private val FONT_DOKUMENT: PDFont = PDType1Font.HELVETICA
private val FONT_EKSTRA_STOR = 18
private val FONT_SUB_HEADER = 16
private val FONT_STOR = 14
private val FONT_LITEN_HEADER = 13
private val FONT_VANLIG = 10
private val FONT_INFORMASJON = 11
private val LINJEAVSTAND = 1.4f
private val LINJEAVSTAND_HEADER = 1f
private val NO_LOCALE = Locale("nb", "no")

const val INNRYKK = 50f


class PdfGenerator {

		private val tekster: Properties = PdfGenerator::class.java.getResourceAsStream("/tekster/innholdstekster_nb.properties").use {
			Properties().apply { load(it) }
		}

		fun lagKvitteringsSide(soknad: DokumentSoknadDto, sammensattNavn: String?): ByteArray? {
			val kvitteringHeader: String = tekster.getProperty("kvittering.tittel")
			val tittel: String = soknad.tittel
			val ettersendelseTittel: String = tekster.getProperty("kvittering.ettersendelse.tittel")
			val fnr: String = soknad.brukerId
			val personInfo = if (sammensattNavn == null) fnr else "$sammensattNavn, $fnr"
			val del1: String = tekster.getProperty("kvittering.informasjonstekst.del1")
			val del2: String = tekster.getProperty("kvittering.informasjonstekst.del2")
			val vedleggSendtHeader: String = tekster.getProperty("kvittering.vedlegg.sendt")
			val vedleggIkkeSendtHeader: String = tekster.getProperty("kvittering.vedlegg.ikkesendt")
			val lastetOpp: List<VedleggDto> = soknad.vedleggsListe
				.filter{ !it.erVariant && (it.opplastingsStatus == OpplastingsStatus.LASTET_OPP || it.opplastingsStatus == OpplastingsStatus.INNSENDT) }.toList()
			val antallLastetOpp = lastetOpp.size
			val ikkeLastetOppDenneGang: List<VedleggDto> =
				soknad.vedleggsListe.filter {it.opplastingsStatus == OpplastingsStatus.SEND_SENERE }.toList()
			val now = DateTime.now()
			val antallInnsendt = java.lang.String.format(
				tekster.getProperty("kvittering.erSendt"),
				antallLastetOpp,
				ikkeLastetOppDenneGang.size + antallLastetOpp,
				formaterDato(now),
				formaterKlokke(now)
			)
			return try {
				PdfBuilder()
					.startSide()
					.leggTilNavLogo()
					.startTekst()
					.flyttTilTopp()
					.leggTilHeaderMidstilt(kvitteringHeader, FONT_EKSTRA_STOR, FONT_HEADER)
					.flyttNedMed(5f)
					.leggTilEttersendelseTeksOgFlyttNedHvisEttersendelse(soknad, ettersendelseTittel, 0)
					.leggTilHeaderMidstilt(tittel, FONT_SUB_HEADER, FONT_DOKUMENT)
					.leggTilTekstMidtstilt(personInfo, FONT_STOR, LINJEAVSTAND)
					.flyttNedMed(30f)
					.leggTilTekst(antallInnsendt, FONT_VANLIG, LINJEAVSTAND)
					.flyttNedMed(20f)
					.leggTilDokumenter(lastetOpp, vedleggSendtHeader)
					.flyttNedMed(20f)
					.leggTilDokumenter(ikkeLastetOppDenneGang, vedleggIkkeSendtHeader)
					.flyttNedMed(25f)
					.leggTilTekst(del1, FONT_INFORMASJON, LINJEAVSTAND)
					.flyttNedMed(15f)
					.leggTilTekst(del2, FONT_INFORMASJON, LINJEAVSTAND)
					.avsluttTekst()
					.avsluttSide()
					.generer()
			} catch (e: IOException) {
				throw RuntimeException("Kunne ikke generere kvitteringside", e)
			}
		}

		private fun formaterKlokke(now: DateTime): String? {
			val time = DateTimeFormat.forPattern("HH.mm").withLocale(NO_LOCALE)
			return time.print(now)
		}

		private fun formaterDato(now: DateTime): String? {
			val dato = DateTimeFormat.forPattern("d. MMMM yyyy").withLocale(NO_LOCALE)
			return dato.print(now)
		}

		fun lagForsideEttersending(soknad: DokumentSoknadDto): ByteArray? {
			return try {
				PdfBuilder()
					.startSide()
					.leggTilNavLogo()
					.startTekst()
					.flyttTilTopp()
					.leggTilHeader("Ettersending for:", 16)
					.flyttNedMed(60f)
					.leggTilHeader(soknad.tittel, FONT_STOR)
					.avsluttTekst()
					.avsluttSide()
					.generer()
			} catch (e: IOException) {
				throw RuntimeException("Kunne ikke generere kvitteringside", e)
			}
		}

	}

	class PdfBuilder {

		private val pdDocument = PDDocument()

		fun start(): PdfBuilder {
			return PdfBuilder()
		}

		fun startSide(): PageBuilder {
			return PageBuilder(this)
		}

		fun getPdDocument(): PDDocument {
			return this.pdDocument
		}

		@Throws(IOException::class)
		fun generer(): ByteArray {
			ByteArrayOutputStream().use { stream ->
				pdDocument.save(stream)
				pdDocument.close()
				return stream.toByteArray()
			}
		}
	}

	class PageBuilder(private val pdfBuilder: PdfBuilder) {
		private var page: PDPage? = null
		private var logo: PDImageXObject? = null
		private var contentStream: PDPageContentStream? = null

		init {
			initPageBuilder()
		}

		fun initPageBuilder() {
			page = PDPage(PDRectangle.A4)
			pdfBuilder.getPdDocument().addPage(page)
			try {
				logo = JPEGFactory.createFromStream(pdfBuilder.getPdDocument(), PageBuilder::class.java.getResourceAsStream("/icons/navlogo.jpg"))
				contentStream = PDPageContentStream(pdfBuilder.getPdDocument(), page, AppendMode.APPEND, true)
			} catch (e: IOException) {
				throw Exception(
					"navlogo.jpg er fjernet fra prosjektet, eller feilet under åpning av contentStream. ",
					e
				)
			}
		}

		fun getContentStream(): PDPageContentStream {
			return this.contentStream!!
		}

		fun getPage(): PDPage {
			return this.page!!
		}

		@Throws(IOException::class)
		fun leggTilNavLogo(): PageBuilder {
			contentStream!!.drawImage(logo, (page!!.mediaBox.width - logo!!.width) / 2, 750f)
			return this
		}

		@Throws(IOException::class)
		fun startTekst(): TextBuilder {
			return TextBuilder(this)
		}

		@Throws(IOException::class)
		fun avsluttSide(): PdfBuilder {
			contentStream!!.close()
			return pdfBuilder
		}
	}

	class TextBuilder(private val pageBuilder: PageBuilder) {
		private val tekstBredde: Float
		private val pageWidth: Float
		private val contentStream = pageBuilder.getContentStream()

		init {
			contentStream.beginText()
			pageWidth = pageBuilder.getPage().mediaBox.width
			tekstBredde = pageWidth - INNRYKK * 2
		}

		@Throws(IOException::class)
		fun leggTilHeaderMidstilt(tekst: String, storrelse: Int, font: PDFont): TextBuilder {
			contentStream.setFont(font, storrelse.toFloat())
			brytAvTekstSomErForBredForSiden(tekst, font, storrelse, LINJEAVSTAND_HEADER, true)
			return this
		}

		@Throws(IOException::class)
		fun leggTilHeader(tekst: String, storrelse: Int): TextBuilder {
			contentStream.setFont(FONT_HEADER, storrelse.toFloat())
			brytAvTekstSomErForBredForSiden(tekst, FONT_HEADER, storrelse, LINJEAVSTAND_HEADER, false)
			return this
		}

		@Throws(IOException::class)
		fun leggTilTekst(tekst: String, storrelse: Int, linjeavstand: Float): TextBuilder {
			contentStream.setFont(FONT_DOKUMENT, storrelse.toFloat())
			brytAvTekstSomErForBredForSiden(tekst, FONT_DOKUMENT, storrelse, linjeavstand, false)
			return this
		}

		@Throws(IOException::class)
		fun leggTilTekstMidtstilt(tekst: String, storrelse: Int, linjeavstand: Float): TextBuilder {
			contentStream.setFont(FONT_DOKUMENT, storrelse.toFloat())
			brytAvTekstSomErForBredForSiden(tekst, FONT_DOKUMENT, storrelse, linjeavstand, true)
			return this
		}

		@Throws(IOException::class)
		private fun brytAvTekstSomErForBredForSiden(
			tekst: String,
			font: PDFont,
			fontSize: Int,
			linjeavstand: Float,
			midstilt: Boolean
		) {
			var startIndex = 0
			val heigth = font.fontDescriptor.fontBoundingBox.height / 1000 * fontSize
			while (startIndex < tekst.length - 1) {
				val linje = finnLinje(tekst.substring(startIndex), font, fontSize)
				val sluttIndex = tekst.indexOf(linje) + linje.length
				if (midstilt) {
					skrivLinjeMidtstilt(linje, font, fontSize)
				} else {
					contentStream.showText(linje)
				}
				flyttNedMed(heigth * linjeavstand)
				startIndex = sluttIndex
			}
		}

		private fun finnLinje(tekst: String, font: PDFont, fontSize: Int): String {
			val split = tekst.split(" ").toTypedArray()
			val sb = StringBuilder()
			for (ord in split) {
				val nesteOrd = if (sb.toString().isEmpty()) ord else " $ord"
				if (leggTilOrdFeiler(sb, nesteOrd, font, fontSize)) {
					return sb.toString()
				} else {
					sb.append(nesteOrd)
				}
			}
			return sb.toString()
		}

		private fun leggTilOrdFeiler(sb: StringBuilder, nestOrd: String, font: PDFont, fontSize: Int): Boolean {
			return try {
				val linje = sb.toString() + nestOrd
				val linjeBredde = font.getStringWidth(linje) / 1000.0f * fontSize
				linjeBredde > tekstBredde
			} catch (e: IOException) {
				false
			}
		}

		@Throws(IOException::class)
		private fun skrivLinjeMidtstilt(linje: String, font: PDFont, fontSize: Int) {
			val width = font.getStringWidth(linje) / 1000.0f * fontSize
			contentStream.newLineAtOffset((pageWidth - width - INNRYKK * 2) / 2, 0f)
			contentStream.showText(linje)
			contentStream.newLineAtOffset((pageWidth - width - INNRYKK * 2) / -2, 0f)
		}

		@Throws(IOException::class)
		fun flyttTilTopp(): TextBuilder {
			contentStream.newLineAtOffset(INNRYKK, 700f)
			return this
		}

		@Throws(IOException::class)
		fun flyttNedMed(piksler: Float): TextBuilder {
			contentStream.newLineAtOffset(0f, -1 * piksler)
			return this
		}

		@Throws(IOException::class)
		fun leggTilDokumenter(dokumenter: List<VedleggDto>, overskrift: String): TextBuilder {
			var textBuilder = this
			if (!dokumenter.isNullOrEmpty()) {
				textBuilder = textBuilder
					.leggTilHeader(overskrift, FONT_LITEN_HEADER)
					.flyttNedMed(5f)
				for (dokument in dokumenter) {
					textBuilder = textBuilder.leggTilTekst(dokument.tittel, FONT_VANLIG, LINJEAVSTAND)
				}
			}
			return textBuilder
		}

		@Throws(IOException::class)
		fun avsluttTekst(): PageBuilder {
			contentStream.endText()
			return pageBuilder
		}

		@Throws(IOException::class)
		fun leggTilEttersendelseTeksOgFlyttNedHvisEttersendelse(
			soknad: DokumentSoknadDto,
			ettersendelseTittel: String,
			flyttNedMed: Int
		): TextBuilder {
			if (soknad.ettersendingsId != null) {
				leggTilHeaderMidstilt(ettersendelseTittel, FONT_SUB_HEADER, FONT_DOKUMENT)
					.flyttNedMed(flyttNedMed.toFloat())
			}
			return this
		}

	}



