package no.nav.soknad.pdfutilities

import com.github.jknack.handlebars.Options

class TextFormattingMethods {

	fun split(p0: String?, p1: Options?): Any {
		if (p0 != null) {
			return p0.replace("\t", "&#160;&#160;").split("\n")
		}
		return ""
	}

	fun tab(p0: String?, p1: Options?): Any {
		if (p0 != null) {
			return p0.replace("\t", "&#160;&#160;")
		}
		return ""
	}

}
