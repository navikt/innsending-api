package no.nav.soknad.pdfutilities

import org.apache.commons.lang3.ArrayUtils
import org.apache.tika.Tika
import java.util.function.Predicate


class FiltypeSjekker {
	val IS_PNG =
		Predicate { bytes: ByteArray ->
			Tika().detect(
				ArrayUtils.subarray(
					bytes.clone(),
					0,
					2048
				)
			).equals("image/png", true)
		}
	val IS_PDF =
		Predicate { bytes: ByteArray? ->
			Tika().detect(bytes).equals("application/pdf", true)
		}
	val IS_JPG =
		Predicate { bytes: ByteArray? ->
			Tika().detect(bytes).equals("image/jpeg", true)
		}
	val IS_IMAGE =
		Predicate { bytes: ByteArray -> IS_PNG.test(bytes) || IS_JPG.test(bytes) }

	fun isPng(bytes: ByteArray): Boolean {
		return IS_PNG.test(bytes)
	}

	fun isPdf(bytes: ByteArray): Boolean {
		return IS_PDF.test(bytes)
	}

	fun isJpg(bytes: ByteArray): Boolean {
		return IS_JPG.test(bytes)
	}

	fun isImage(bytes: ByteArray): Boolean {
		return IS_IMAGE.test(bytes)
	}

}
