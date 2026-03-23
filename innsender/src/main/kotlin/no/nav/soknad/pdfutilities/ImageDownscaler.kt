package no.nav.soknad.pdfutilities

import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.pdfutilities.FiltypeSjekker.Companion.imageFileTypes
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.imageio.ImageIO

class ImageDownscaler {

	private companion object {
		const val MAX_IMAGE_DIMENSION = 8_000
		const val MAX_IMAGE_PIXELS = 40_000_000L
	}

	private val logger = LoggerFactory.getLogger(ImageDownscaler::class.java)

	fun downscaleForPdfIfNeeded(fileName: String, fileType: String, imageBytes: ByteArray): ByteArray {
		try {
			ByteArrayInputStream(imageBytes).use { input ->
				ImageIO.createImageInputStream(input)?.use { imageInputStream ->
					val readers = ImageIO.getImageReaders(imageInputStream)
					if (!readers.hasNext()) {
						throw IllegalActionException(
							message = "Fant ingen bildeleser for fil som skal konverteres til PDF",
							errorCode = ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR
						)
					}

					val imageReader = readers.next()
					try {
						imageReader.input = imageInputStream
						val width = imageReader.getWidth(0)
						val height = imageReader.getHeight(0)
						val subsampling = calculateSubsampling(width, height)

						if (subsampling == 1) {
							return imageBytes
						}

						logger.info(
							"Skalerer ned bilde=$fileName fra ${width}x$height med subsampling=$subsampling før PDF-konvertering"
						)

						val readParam = imageReader.defaultReadParam.apply {
							setSourceSubsampling(subsampling, subsampling, 0, 0)
						}
						val bufferedImage = imageReader.read(0, readParam)
							?: throw IllegalActionException(
								message = "Klarte ikke å lese bildet som skal skaleres ned før PDF-konvertering",
								errorCode = ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR
							)

						return writeScaledImage(fileType, bufferedImage)
					} finally {
						imageReader.dispose()
					}
				}
			}
		} catch (e: IOException) {
			logger.warn("Feil ved nedskalering av bilde før PDF-konvertering", e)
			throw IllegalActionException(
				message = "Klarte ikke å skalere ned bilde før PDF-konvertering",
				cause = e,
				errorCode = ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR
			)
		}

		throw IllegalActionException(
			message = "Klarte ikke å lese bilde før PDF-konvertering",
			errorCode = ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR
		)
	}

	private fun calculateSubsampling(width: Int, height: Int): Int {
		val maxDimensionFactor = maxOf(
			width.toDouble() / MAX_IMAGE_DIMENSION.toDouble(),
			height.toDouble() / MAX_IMAGE_DIMENSION.toDouble()
		)
		val maxPixelsFactor = kotlin.math.sqrt((width.toLong() * height.toLong()).toDouble() / MAX_IMAGE_PIXELS.toDouble())
		return maxOf(1, kotlin.math.ceil(maxOf(maxDimensionFactor, maxPixelsFactor)).toInt())
	}

	private fun writeScaledImage(fileType: String, bufferedImage: BufferedImage): ByteArray {
		val format = imageFormat(fileType)
		val imageToWrite = if (format == "jpeg" && bufferedImage.colorModel.hasAlpha()) {
			removeAlphaChannel(bufferedImage)
		} else {
			bufferedImage
		}

		ByteArrayOutputStream().use { output ->
			if (!ImageIO.write(imageToWrite, format, output)) {
				throw IllegalActionException(
					message = "Klarte ikke å skrive nedskalert bilde før PDF-konvertering",
					errorCode = ErrorCode.TYPE_DETECTION_OR_CONVERSION_ERROR
				)
			}
			return output.toByteArray()
		}
	}

	private fun imageFormat(fileType: String): String {
		val mimeType = imageFileTypes[fileType.lowercase()]
			?: throw IllegalActionException(
				message = "Ukjent bildeformat. Kan ikke skalere ned bilde før PDF-konvertering",
				errorCode = ErrorCode.NOT_SUPPORTED_FILE_FORMAT
			)
		return mimeType.substringAfter("/")
	}

	private fun removeAlphaChannel(bufferedImage: BufferedImage): BufferedImage {
		val imageWithoutAlpha = BufferedImage(bufferedImage.width, bufferedImage.height, BufferedImage.TYPE_INT_RGB)
		val graphics = imageWithoutAlpha.createGraphics()
		try {
			graphics.color = Color.WHITE
			graphics.fillRect(0, 0, bufferedImage.width, bufferedImage.height)
			graphics.drawImage(bufferedImage, 0, 0, null)
		} finally {
			graphics.dispose()
		}
		return imageWithoutAlpha
	}
}
