package no.nav.soknad.pdfutilities.utils

import java.util.regex.Pattern

class PdfUtils {
	companion object {
		//    http://www.unicode.org/reports/tr18/
		//    \p{L} – for å støtte alle bokstaver fra alle språk
		//    \p{N} – for å støtte alle tall
		//    \p{P} – for å støtte tegnsetting
		//    \p{Z} – for å støtte whitespace separatorer
		//    \n – for å støtte linjeskift
		fun fjernSpesielleKarakterer(text: String?): String? {
			if (text == null) return null

			val regex = "[^\\p{L}\\p{N}\\p{P}\\p{Z}\\n]"
			val pattern: Pattern = Pattern.compile(
				regex,
				Pattern.UNICODE_CHARACTER_CLASS
			)
			val matcher = pattern.matcher(text)
			return matcher.replaceAll("").trim()
		}
	}

}
