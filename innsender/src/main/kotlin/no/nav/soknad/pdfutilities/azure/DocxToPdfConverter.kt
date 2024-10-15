package no.nav.soknad.pdfutilities.azure

import com.microsoft.graph.core.models.IProgressCallback
import com.microsoft.graph.core.tasks.LargeFileUploadTask
import com.microsoft.graph.drives.item.items.item.content.ContentRequestBuilder
import com.microsoft.graph.drives.item.items.item.createuploadsession.CreateUploadSessionPostRequestBody
import com.microsoft.graph.models.DriveItem
import com.microsoft.graph.models.DriveItemUploadableProperties
import com.microsoft.graph.serviceclient.GraphServiceClient
import com.microsoft.kiota.serialization.ParseNode
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.util.Utilities.Companion.lagUlidId
import no.nav.soknad.pdfutilities.DocxToPdfInterface
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.util.concurrent.CancellationException


@Component
@Profile("prod | dev")
class DocxToPdfConverter(
	private val graphClient: GraphServiceClient
) : DocxToPdfInterface {
	private val logger = LoggerFactory.getLogger(javaClass)

	override fun convertDocxToPdf(fileContent: ByteArray): ByteArray {
		logger.debug("Klar for å konvertere docx til PDF. Docx størrelse = ${fileContent.size}")
		var driveId: String? = null
		var docxItemId: String? = null
		try {
			driveId = graphClient.drives().get().value.get(0).id
			docxItemId = uploadFile(driveId, fileContent)

			val result = graphClient.drives().byDriveId(driveId).items().byDriveItemId(docxItemId)
				.content()[{ requestConfiguration: ContentRequestBuilder.GetRequestConfiguration ->
				requestConfiguration.queryParameters.format = "pdf"
			}].readAllBytes()
			logger.debug("Konvertert docx til PDF. PDF størrelse = ${result.size}")
			return result
		} catch (e: Exception) {
			logger.error("Konvertering av docx til PDF feilet", e)
			throw BackendErrorException("Error converting docx file to pdf", e)
		} finally {
			// delete uploaded docx
			if (driveId != null && docxItemId != null) {
				graphClient.drives().byDriveId(driveId).items().byDriveItemId(docxItemId).delete()
			}
		}
	}

	private fun uploadFile(driveId: String, fileContent: ByteArray): String {
		logger.debug("Upload docx file")

		// Get an input stream for the file
		val fileStream = fileContent.inputStream()
		val streamSize = fileContent.size

		// Set body of the upload session request
		val uploadSessionRequest = CreateUploadSessionPostRequestBody()
		val properties = DriveItemUploadableProperties()
		properties.additionalData["@microsoft.graph.conflictBehavior"] = "replace"
		uploadSessionRequest.item = properties

		// Create an upload session
		// ItemPath does not need to be a path to an existing item
		val itemPath = lagUlidId()
		val uploadSession = graphClient.drives()
			.byDriveId(driveId)
			.items()
			.byDriveItemId(("root:/$itemPath").toString() + ":")
			.createUploadSession()
			.post(uploadSessionRequest)

		// Create the upload task
		val maxSliceSize = 320 * 10
		val largeFileUploadTask = LargeFileUploadTask(
			graphClient.requestAdapter,
			uploadSession,
			fileStream,
			streamSize.toLong(),
			maxSliceSize.toLong()
		) { parseNode: ParseNode? -> DriveItem.createFromDiscriminatorValue(parseNode) }

		val maxAttempts = 5

		// Create a callback used by the upload provider
		val callback = IProgressCallback { current: Long, max: Long ->
			logger.debug(
				String.format("Uploaded %d bytes of %d total bytes", current, max)
			)
		}

		// Do the upload
		try {
			val uploadResult = largeFileUploadTask.upload(maxAttempts, callback)
			if (uploadResult.isUploadSuccessful) {
				logger.debug("Upload complete")
				logger.debug("Item ID: " + uploadResult.itemResponse.id)
				return uploadResult.itemResponse.id
			} else {
				logger.error("UploadResult is false")
				throw BackendErrorException("Upload failed")
			}
		} catch (ex: CancellationException) {
			logger.error("Error uploading: " + ex.message)
			throw BackendErrorException("Upload failed", ex)
		}
	}

}
