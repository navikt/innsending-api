package no.nav.soknad.innsending.config

import com.google.cloud.NoCredentials
import com.google.cloud.storage.Blob
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import no.nav.soknad.innsending.embedded.GoogleStorage
import no.nav.soknad.innsending.service.fillager.FillagerNamespace
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Scope

@Configuration
@EnableConfigurationProperties(CloudStorageConfig::class)
class CloudStorageClient {

	private val logger = LoggerFactory.getLogger(javaClass)

	@Bean
	@Profile("!(local | docker | test | endtoend  )")
	@Qualifier("cloudStorageClient")
	@Scope("prototype")
	fun gcpClient(): Storage = StorageOptions.getDefaultInstance().service

	@Bean
	@Profile("docker")
	@Qualifier("cloudStorageClient")
	@Scope("prototype")
	fun dockerClient(cloudStorageConfig: CloudStorageConfig): Storage {
		val host = cloudStorageConfig.host ?: "http://localhost:4443" // From docker-compose
		logger.info("Cloud storage docker client: $host, bucket: ${cloudStorageConfig.fillagerBucketNavn}")
		return buildStorageForTest(host, cloudStorageConfig.fillagerBucketNavn)
	}

	@Bean
	@Profile("test | local")
	@Qualifier("cloudStorageClient")
	@Scope("prototype")
	fun embeddedClient(cloudStorageConfig: CloudStorageConfig, gcsContainer: GoogleStorage.Container): Storage {
		val host = gcsContainer.getUrl() // From testcontainers
		return buildStorageForTest(host, cloudStorageConfig.fillagerBucketNavn)
	}

	fun buildStorageForTest(host: String, bucket: String): Storage {
		return StorageOptions
			.newBuilder()
			.setCredentials(NoCredentials.getInstance())
			.setHost(host)
			.setProjectId("innsendingapi")
			.build()
			.service
			.also { it ->
				if (it.get(bucket) == null) {
					it.create(BucketInfo.of(bucket))
					val files = hentFilBlob(it, bucket, FillagerNamespace.NOLOGIN, "1234", "1234" )
					logger.info("Created bucket '$bucket' in Google Cloud Storage. Files in the bucket ${files?.size ?: 0}")
				}
			}
	}


	private fun hentFilBlob(
		storage: Storage,
		bucket: String,
		namespace: FillagerNamespace,
		innsendingId: String,
		filId: String
	): Blob? {
		val prefix = "${namespace.value}/$innsendingId/"
		val blobs = storage.list(bucket, Storage.BlobListOption.prefix(prefix))
		return blobs.iterateAll().firstOrNull { it.metadata?.get("filId") == filId }
	}

}
