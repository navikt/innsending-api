package no.nav.soknad.pdfutilities

import org.apache.pdfbox.cos.COSObject
import org.apache.pdfbox.io.MemoryUsageSetting
import org.apache.pdfbox.pdmodel.DefaultResourceCache
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.graphics.PDXObject
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.tools.imageio.ImageIOUtil
import org.imgscalr.Scalr
import org.slf4j.LoggerFactory
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.IOException


class KonverterTilPng {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun konverterTilPng(fil: ByteArray): List<ByteArray> {
		val antallSider = AntallSider().finnAntallSider(fil)
		var imageList = mutableListOf<ByteArray>()
		for (i in 1..antallSider) imageList.add(konverterTilPng(fil, i))
		return imageList
	}

	fun konverterTilPng(input: ByteArray?, sideNr: Int): ByteArray {
		if (input == null || input.size == 0) {
			logger.error("Kan ikke konvertere en tom fil til PNG")
			throw RuntimeException("Kan ikke konvertere en tom fil til PNG")
		}
		val png = fraPDFTilPng(input, sideNr)
		logger.info("Konvertert filstørrelse=" + png.size)
		return png
	}

	/**
	 * Konverterer en PDF til en liste av PNG filer
	 *
	 * @param in byte array av PDF
	 * @return Liste av byte array av PNG
	 */
	private fun fraPDFTilPng(input: ByteArray, side: Int): ByteArray {
		try {
			PDDocument.load(input, "", null, null, MemoryUsageSetting.setupMainMemoryOnly((500 * 1024 * 1024).toLong()))
				.use { pd ->
					ByteArrayOutputStream().use { baos ->
						pd.resourceCache = MyResourceCache()
						val pdfRenderer = PDFRenderer(pd)
						val pageIndex =
							if (pd.numberOfPages - 1 < side) pd.numberOfPages - 1 else Math.max(side, 0)
						var bim =
							pdfRenderer.renderImageWithDPI(pageIndex, 100f, ImageType.RGB)
						bim = scaleImage(bim, Dimension(600, 800), true)
						ImageIOUtil.writeImage(bim, "PNG", baos, 300, 1.0F)
						bim.flush()
						return baos.toByteArray()
					}
				}
		} catch (e: IOException) {
			logger.error("Klarte ikke å konvertere pdf til png", e)
			throw RuntimeException("Klarte ikke å konvertere pdf til png")
		} catch (t: Throwable) {
			logger.error("Klarte ikke å konvertere pdf til png", t)
			throw RuntimeException("Klarte ikke å konvertere pdf til png")
		}
	}

	private class MyResourceCache : DefaultResourceCache() {
		override fun put(indirect: COSObject, xobject: PDXObject) {
			// Hindrer caching ved å kommenterer ut default kall til super sin cache
			//super.put(indirect, xobject);
		}
	}

	private fun scaleImage(image: BufferedImage, boundingBox: Dimension, fitInsideBox: Boolean): BufferedImage {
		val scaleFactorWidth = boundingBox.getWidth() / image.width
		val scaleFactorHeight = boundingBox.getHeight() / image.height
		val scalingFactor: Double
		scalingFactor = if (fitInsideBox) {
			java.lang.Double.min(scaleFactorWidth, scaleFactorHeight)
		} else {
			java.lang.Double.max(scaleFactorWidth, scaleFactorHeight)
		}
		val scaledImage: BufferedImage = Scalr.resize(
			image, (scalingFactor * image.width).toInt(),
			(scalingFactor * image.height).toInt()
		)
		return if (!fitInsideBox) {
			cropImage(scaledImage, boundingBox)
		} else {
			scaledImage
		}
	}

	private fun cropImage(image: BufferedImage, boundingBox: Dimension): BufferedImage {
		require(!(boundingBox.getWidth() > image.width || boundingBox.getHeight() > image.height)) { "Bildet må være minst like stort som boksen." }
		val newWidth = Math.round(boundingBox.getWidth()).toInt()
		val newHeight = Math.round(boundingBox.getHeight()).toInt()
		val widthDelta = image.width - newWidth
		val heightDelta = image.height - newHeight
		return image.getSubimage(widthDelta / 2, heightDelta / 2, newWidth, newHeight)
	}

}