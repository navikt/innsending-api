package no.nav.soknad.innsending.config

import com.google.cloud.NoCredentials
import com.google.cloud.storage.BucketInfo
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.context.annotation.Scope

@Configuration
@EnableConfigurationProperties(CloudStorageConfig::class)
class CloudStorageClient(
	private val cloudStorageConfig: CloudStorageConfig,
) {

	@Bean
	@Profile("!(local | docker)")
	@Qualifier("cloudStorageClient")
	@Scope("prototype")
	fun gcpClient(): Storage = StorageOptions.getDefaultInstance().service

	@Bean
	@Profile("local | docker")
	@Qualifier("cloudStorageClient")
	@Scope("prototype")
	fun localClient(): Storage {
		return StorageOptions
			.newBuilder()
			.setCredentials(NoCredentials.getInstance())
			.setHost("http://localhost:4443") // From docker-compose
			.setProjectId("innsendingapi")
			.build()
			.service
			.also { it ->
				if (it.get(cloudStorageConfig.fillagerBucketNavn) == null) {
					it.create(BucketInfo.of(cloudStorageConfig.fillagerBucketNavn))
				}
			}
	}

}
