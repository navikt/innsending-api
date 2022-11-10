package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VedleggDto
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.font.PDFont
import org.apache.pdfbox.pdmodel.font.PDType0Font
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.slf4j.LoggerFactory
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.Paths
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

private const val FONT_EKSTRA_STOR = 18
private const val FONT_SUB_HEADER = 16
private const val FONT_STOR = 14
private const val FONT_LITEN_HEADER = 13
private const val FONT_VANLIG = 10
private const val FONT_INFORMASJON = 11
private const val LINJEAVSTAND = 1.4f
private const val LINJEAVSTAND_HEADER = 1f
private val FONT_HEADER = PDType1Font.HELVETICA_BOLD
private val FONT_DOKUMENT = PDType1Font.HELVETICA
private val NO_LOCALE = Locale("nb", "no")

const val INNRYKK = 50f


class PdfGenerator {

	val logger = LoggerFactory.getLogger(javaClass)

	private val tekster: Properties = PdfGenerator::class.java.getResourceAsStream("/tekster/innholdstekster_nb.properties").use {
		Properties().apply { load(it) }
	}

	fun lagKvitteringsSide(soknad: DokumentSoknadDto, sammensattNavn: String?, opplastedeVedlegg: List<VedleggDto>, manglendeObligatoriskeVedlegg: List<VedleggDto>): ByteArray {
		val kvitteringHeader = tekster.getProperty("kvittering.tittel")
		val tittel = soknad.tittel
		val ettersendelseTittel = tekster.getProperty("kvittering.ettersendelse.tittel")
		val fnr = soknad.brukerId
		val personInfo = if (sammensattNavn == null) fnr else "$sammensattNavn, $fnr"
		val del1 = tekster.getProperty("kvittering.informasjonstekst.del1")
		val del2 = tekster.getProperty("kvittering.informasjonstekst.del2")
		val vedleggSendtHeader = tekster.getProperty("kvittering.vedlegg.sendt")
		val vedleggIkkeSendtHeader = tekster.getProperty("kvittering.vedlegg.ikkesendt")
		val lastetOpp = opplastedeVedlegg
		val antallLastetOpp = lastetOpp.size
		val ikkeLastetOppDenneGang = manglendeObligatoriskeVedlegg
		val now = LocalDateTime.now()
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

	private fun formaterKlokke(now: LocalDateTime): String {
		return now.format(DateTimeFormatter.ISO_LOCAL_TIME)
	}

	private fun formaterDato(now: LocalDateTime): String {
		return now.format(DateTimeFormatter.ISO_LOCAL_DATE)
	}

	fun lagForsideEttersending(soknad: DokumentSoknadDto) = try {
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

class PdfBuilder {
	private val pdDocument = PDDocument()

	fun start() = PdfBuilder()

	fun startSide() = PageBuilder(this)

	fun getPdDocument() = pdDocument

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
	private var page = PDPage(PDRectangle.A4)
	private var logo: PDImageXObject
	private var contentStream: PDPageContentStream
	val logger = LoggerFactory.getLogger(javaClass)

	init {
		pdfBuilder.getPdDocument().addPage(page)
		try {
			logo = JPEGFactory.createFromStream(pdfBuilder.getPdDocument(), PageBuilder::class.java.getResourceAsStream("/icons/navlogo.jpg"))
			contentStream = PDPageContentStream(pdfBuilder.getPdDocument(), page, AppendMode.APPEND, true)
		} catch (e: IOException) {
			throw Exception("navlogo.jpg er fjernet fra prosjektet, eller feilet under åpning av contentStream.", e)
		}
	}

	fun getFont(path: String): PDFont = PDType0Font.load(pdfBuilder.getPdDocument(), File(javaClass.classLoader.getResource(path)?.file
		?: throw BackendErrorException("Fant ikke ressursfil $path", "Feil ved generering av PDF")))

	fun getPdDocument() = pdfBuilder.getPdDocument()

	fun getContentStream() = contentStream

	fun getPage() = page

	@Throws(IOException::class)
	fun leggTilNavLogo(): PageBuilder {
		contentStream.drawImage(logo, (page.mediaBox.width - logo.width) / 2, 750f)
		return this
	}

	@Throws(IOException::class)
	fun startTekst() = TextBuilder(this)

	@Throws(IOException::class)
	fun avsluttSide(): PdfBuilder {
		contentStream.close()
		return pdfBuilder
	}
}

class TextBuilder(private val pageBuilder: PageBuilder) {
	private val tekstBredde: Float
	private val pageWidth: Float
	private val contentStream = pageBuilder.getContentStream()

	private val logger = LoggerFactory.getLogger(javaClass)
	private val regex = Regex("\t")
	private var arialFont: PDFont? = null
	private var arialBoldFont: PDFont? = null

	init {
		contentStream.beginText()
		pageWidth = pageBuilder.getPage().mediaBox.width
		tekstBredde = pageWidth - INNRYKK * 2
	}

	private fun hentArial(): PDFont {
		if (arialFont == null) {
			arialFont = pageBuilder.getFont("fonts/arial/arial.ttf")
/*
			val res: URL? = javaClass.classLoader.getResource("fonts/arial/arial.ttf")
			if (res == null) {
				throw BackendErrorException("Arial font ikke funnet", "Feil ved generering av PDF")
			}
			val file: File = Paths.get(res.toURI()).toFile()
			val absolutePath = file.absolutePath
			//arialFont = pageBuilder.getFont(absolutePath, pageBuilder.getPdDocument())
			arialFont = PDType0Font.load(pageBuilder.getPdDocument(), File(absolutePath))
*/
			return arialFont as PDFont
		} else {
			return arialFont as PDFont
		}
	}
	private fun hentArialBold(): PDFont {
		if (arialBoldFont == null) {
			arialBoldFont = pageBuilder.getFont("fonts/arial/arialbd.ttf")
/*
			val res: URL? = javaClass.classLoader.getResource("fonts/arial/arialbd.ttf")
			if (res == null) {
				throw BackendErrorException("Arialbld font ikke funnet ", "Feil ved generering av PDF")
			}
			val file: File = Paths.get(res.toURI()).toFile()
			val absolutePath = file.absolutePath
			//arialBoldFont = pageBuilder.getFont(absolutePath, pageBuilder.getPdDocument())
			arialBoldFont = PDType0Font.load(pageBuilder.getPdDocument(), File(absolutePath))
*/
			return arialBoldFont as PDFont
		} else {
			return arialBoldFont as PDFont
		}
	}

	@Throws(IOException::class)
	fun leggTilHeaderMidstilt(tekst: String, storrelse: Int, font: PDFont): TextBuilder {
		val useFont = hentArialBold()
		contentStream.setFont(useFont, storrelse.toFloat())
		brytAvTekstSomErForBredForSiden(tekst, useFont, storrelse, LINJEAVSTAND_HEADER, true)
		return this
	}

	@Throws(IOException::class)
	fun leggTilHeader(tekst: String, storrelse: Int): TextBuilder {
		val useFont = hentArialBold()
		contentStream.setFont(useFont, storrelse.toFloat())
		brytAvTekstSomErForBredForSiden(tekst, useFont, storrelse, LINJEAVSTAND_HEADER, false)
		return this
	}

	@Throws(IOException::class)
	fun leggTilTekst(tekst: String, storrelse: Int, linjeavstand: Float): TextBuilder {
		val useFont = hentArial()
		contentStream.setFont(useFont, storrelse.toFloat())
		brytAvTekstSomErForBredForSiden(tekst, useFont, storrelse, linjeavstand, false)
		return this
	}

	@Throws(IOException::class)
	fun leggTilTekstMidtstilt(tekst: String, storrelse: Int, linjeavstand: Float): TextBuilder {
		val useFont = hentArial()
		contentStream.setFont(useFont, storrelse.toFloat())
		brytAvTekstSomErForBredForSiden(tekst, useFont, storrelse, linjeavstand, true)
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
		val konvertertTekst = regex.replace(tekst," ")
		var startIndex = 0
		val height = font.fontDescriptor.fontBoundingBox.height / 1000 * fontSize
		while (startIndex < konvertertTekst.length - 1) {
			val linje = finnLinje(konvertertTekst.substring(startIndex), font, fontSize)
			val sluttIndex = konvertertTekst.indexOf(linje) + linje.length
			if (midstilt) {
				skrivLinjeMidtstilt(linje, font, fontSize)
			} else {
				contentStream.showText(linje)
			}
			flyttNedMed(height * linjeavstand)
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
		} catch (e2: IllegalArgumentException) {
			logger.warn("Feil i forbindelse med av generering av PDF med ${sb.toString()} + $nestOrd")
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
		if (dokumenter.isNotEmpty()) {
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
