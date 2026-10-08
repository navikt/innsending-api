package no.nav.soknad.innsending.service.fillager

import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.google.api.services.storage.model.StorageObject
import com.google.api.gax.paging.Page
import com.google.cloud.NoCredentials
import com.google.cloud.http.HttpTransportOptions
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageException
import com.google.cloud.storage.StorageOptions
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.soknad.innsending.config.CloudStorageConfig
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.junit.jupiter.params.provider.ValueSource
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

class FileStorageImplTest {

	private val config = CloudStorageConfig().apply { fillagerBucketNavn = "test-bucket" }

	private val storage = mockk<Storage>()
	private val innsendingsId = UUID.randomUUID()
	private val attachmentId = "attachment-1"
	private lateinit var fileStorage: FileStorageImpl
	private var persistedBlob: Blob? = null

	@BeforeEach
	fun setUp() {
		fileStorage = FileStorageImpl(config, storage)
		every { storage.get(any<BlobId>()) } answers { persistedBlob }
		every { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) } answers {
			persist(firstArg<BlobInfo>())
		}
		every { storage.update(any<BlobInfo>()) } answers {
			storage.update(firstArg<BlobInfo>(), *emptyArray<Storage.BlobTargetOption>())
		}
	}

	@ParameterizedTest
	@EnumSource(FileStorageNamespace::class)
	fun `concurrent attachment deletion succeeds when the same file is already deleted`(namespace: FileStorageNamespace) {
		val blob = createFile(namespace)
		every { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) } answers {
			persist(firstArg<BlobInfo>())
			throw StorageException(409, "The metadata for object was edited during the operation")
		}

		assertEquals(1, fileStorage.delete(namespace, innsendingsId, attachmentId, null, false))

		assertEquals(FilStatus.SLETTET.value, assertNotNull(persistedBlob).metadata?.get("status"))
		verify(exactly = 1) { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) }
		verify(exactly = 1) { storage.get(blob.blobId) }
		verify(exactly = 0) { storage.delete(any<BlobId>()) }
	}

	@ParameterizedTest
	@EnumSource(FileStorageNamespace::class)
	fun `concurrent single file deletion succeeds when the same file is already deleted`(namespace: FileStorageNamespace) {
		val blob = createFile(namespace)
		val fileId = UUID.fromString(requireNotNull(blob.metadata?.get("filId")))
		every { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) } answers {
			persist(firstArg<BlobInfo>())
			throw StorageException(412, "metadata conflict")
		}

		assertEquals(1, fileStorage.delete(namespace, innsendingsId, attachmentId, fileId, false))

		assertEquals(FilStatus.SLETTET.value, assertNotNull(persistedBlob).metadata?.get("status"))
		verify(exactly = 1) { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) }
		verify(exactly = 1) { storage.get(blob.blobId) }
	}

	@Test
	fun `soft deletion guards both object generation and metadata version`() {
		val blob = createFile()

		assertEquals(1, deleteAttachment())

		assertEquals(FilStatus.SLETTET.value, assertNotNull(persistedBlob).metadata?.get("status"))
		verify(exactly = 1) {
			storage.update(
				match { it.generation == blob.generation && it.metageneration == blob.metageneration },
				Storage.BlobTargetOption.generationMatch(),
				Storage.BlobTargetOption.metagenerationMatch(),
			)
		}
		verify(exactly = 0) { storage.get(any<BlobId>()) }
	}

	@ParameterizedTest
	@ValueSource(ints = [409, 412])
	fun `metadata conflicts retry with refreshed metadata without losing other changes`(code: Int) {
		val blob = createFile()
		val requests = mutableListOf<BlobInfo>()
		every { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) } answers {
			val request = firstArg<BlobInfo>()
			requests.add(request)
			if (requests.size == 1) {
				persist(blob.toBuilder().setMetadata(requireNotNull(blob.metadata) + ("other" to "updated")).build())
				throw StorageException(code, "metadata conflict")
			}
			persist(request)
		}

		assertEquals(1, deleteAttachment())

		val persisted = assertNotNull(persistedBlob)
		assertEquals(FilStatus.SLETTET.value, persisted.metadata?.get("status"))
		assertEquals("updated", persisted.metadata?.get("other"))
		assertEquals(2, requests.size)
		assertEquals("updated", requests.last().metadata?.get("other"))
		assertEquals(blob.generation, requests.last().generation)
		assertEquals(blob.metageneration + 1, requests.last().metageneration)
		verify(exactly = 1) { storage.get(blob.blobId) }
		verify(exactly = 2) {
			storage.update(
				any<BlobInfo>(),
				Storage.BlobTargetOption.generationMatch(),
				Storage.BlobTargetOption.metagenerationMatch(),
			)
		}
	}

	@ParameterizedTest
	@ValueSource(ints = [409, 412])
	fun `persistent conflicts stop after three update attempts and propagate the error`(code: Int) {
		val blob = createFile()
		val conflict = StorageException(code, "metadata conflict")
		every { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) } throws conflict

		assertSame(conflict, assertThrows<StorageException> { deleteAttachment() })

		assertEquals(FilStatus.LASTET_OPP.value, assertNotNull(persistedBlob).metadata?.get("status"))
		verify(exactly = 3) { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) }
		verify(exactly = 3) { storage.get(blob.blobId) }
	}

	@Test
	fun `concurrent deletion on the final attempt still succeeds`() {
		val blob = createFile()
		var attempts = 0
		every { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) } answers {
			if (++attempts == 3) persist(firstArg<BlobInfo>())
			throw StorageException(412, "metadata conflict")
		}

		assertEquals(1, deleteAttachment())

		assertEquals(3, attempts)
		assertEquals(FilStatus.SLETTET.value, assertNotNull(persistedBlob).metadata?.get("status"))
	}

	@ParameterizedTest
	@ValueSource(ints = [401, 403, 404, 500, 503])
	fun `unrelated storage failures propagate without conflict recovery`(code: Int) {
		createFile()
		val failure = StorageException(code, "storage failure")
		every { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) } throws failure

		assertSame(failure, assertThrows<StorageException> { deleteAttachment() })

		verify(exactly = 1) { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) }
		verify(exactly = 0) { storage.get(any<BlobId>()) }
	}

	@Test
	fun `a missing object after a conflict is not reported as successfully soft deleted`() {
		val blob = createFile()
		val conflict = StorageException(409, "metadata conflict")
		every { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) } throws conflict
		every { storage.get(blob.blobId) } returns null

		assertSame(conflict, assertThrows<StorageException> { deleteAttachment() })

		verify(exactly = 1) { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) }
	}

	@Test
	fun `a replaced object after a conflict is not changed or accepted as deleted`() {
		val blob = createFile()
		val replacement = mockk<Blob> {
			every { generation } returns blob.generation + 1
			every { metadata } returns mapOf("status" to FilStatus.SLETTET.value)
		}
		val conflict = StorageException(412, "generation mismatch")
		every { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) } throws conflict
		every { storage.get(blob.blobId) } returns replacement

		assertSame(conflict, assertThrows<StorageException> { deleteAttachment() })

		verify(exactly = 1) { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) }
	}

	@Test
	fun `failure to refresh metadata propagates`() {
		val blob = createFile()
		val failure = StorageException(503, "storage unavailable")
		every { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) } throws
			StorageException(409, "metadata conflict")
		every { storage.get(blob.blobId) } throws failure

		assertSame(failure, assertThrows<StorageException> { deleteAttachment() })
	}

	@Test
	fun `missing metadata after a conflict is not reported as successfully deleted`() {
		val blob = createFile()
		every { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) } throws
			StorageException(409, "metadata conflict")
		every { storage.get(blob.blobId) } returns snapshot(blob.toBuilder().setMetadata(null).build(), 2)

		assertThrows<IllegalArgumentException> { deleteAttachment() }

		verify(exactly = 1) { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) }
	}

	@Test
	fun `permanent deletion does not use soft delete conflict recovery`() {
		val blob = createFile()
		every { storage.delete(blob.blobId) } answers {
			persistedBlob = null
			true
		}

		assertEquals(1, fileStorage.delete(FileStorageNamespace.DIGITAL, innsendingsId, attachmentId, null, true))

		assertNull(persistedBlob)
		verify(exactly = 1) { storage.delete(blob.blobId) }
		verify(exactly = 0) { storage.update(any<BlobInfo>(), *anyVararg<Storage.BlobTargetOption>()) }
		verify(exactly = 0) { storage.get(any<BlobId>()) }
	}

	private fun deleteAttachment() =
		fileStorage.delete(FileStorageNamespace.DIGITAL, innsendingsId, attachmentId, null, false)

	private fun createFile(namespace: FileStorageNamespace = FileStorageNamespace.DIGITAL): Blob {
		val fileId = UUID.randomUUID()
		val blob = snapshot(
			BlobInfo.newBuilder(BlobId.of(config.fillagerBucketNavn, "${namespace.value}/$innsendingsId/$attachmentId/$fileId"))
				.setMetadata(
					mapOf(
						"filId" to fileId.toString(),
						"vedleggId" to attachmentId,
						"status" to FilStatus.LASTET_OPP.value,
					)
				)
				.build(),
		)
		persistedBlob = blob
		val page = mockk<Page<Blob>>()
		every { page.iterateAll() } returns listOf(blob)
		every {
			storage.list(config.fillagerBucketNavn, Storage.BlobListOption.prefix("${namespace.value}/$innsendingsId/"))
		} returns page
		return blob
	}

	private fun persist(request: BlobInfo): Blob =
		snapshot(request, request.metageneration + 1).also { persistedBlob = it }

	private fun snapshot(info: BlobInfo, metageneration: Long = 1): Blob {
		val objectJson = GsonFactory.getDefaultInstance().toString(
			StorageObject()
				.setBucket(info.bucket)
				.setName(info.name)
				.setGeneration(info.generation ?: 1)
				.setMetageneration(metageneration)
				.setMetadata(info.metadata)
		)
		val transport = MockHttpTransport.Builder()
			.setLowLevelHttpResponse(MockLowLevelHttpResponse().setContentType("application/json").setContent(objectJson))
			.build()
		return StorageOptions.newBuilder()
			.setProjectId("test-project")
			.setCredentials(NoCredentials.getInstance())
			.setTransportOptions(HttpTransportOptions.newBuilder().setHttpTransportFactory { transport }.build())
			.build().service.get(BlobId.of(info.bucket, info.name))
	}
}
