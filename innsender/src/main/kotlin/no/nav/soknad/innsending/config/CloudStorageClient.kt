package no.nav.soknad.innsending.config

import com.google.cloud.NoCredentials
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import no.nav.soknad.innsending.embedded.GoogleStorage
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Scope

@Configuration
@EnableConfigurationProperties(CloudStorageConfig::class)
class CloudStorageClient {

	@Bean
	@Profile("!(local | docker | test)")
	@Qualifier("cloudStorageClient")
	@Scope("prototype")
	fun gcpClient(): Storage = StorageOptions.getDefaultInstance().service

	@Bean
	@Profile("docker")
	@Qualifier("cloudStorageClient")
	@Scope("prototype")
	fun dockerClient(cloudStorageConfig: CloudStorageConfig): Storage {
		val host = "http://localhost:4443" // From docker-compose
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
				}
			}
	}

}
