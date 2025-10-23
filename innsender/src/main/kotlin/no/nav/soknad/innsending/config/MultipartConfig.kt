package no.nav.soknad.innsending.config

import jakarta.servlet.MultipartConfigElement
import org.springframework.boot.web.servlet.MultipartConfigFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize

@Configuration
class MultipartConfig(
	private val restConfig: RestConfig
) {

	@Bean
	fun multipartConfigElement(): MultipartConfigElement? {
		val maxFileSize = if (restConfig.maxFileSizeSum <= 50) 51 else restConfig.maxFileSizeSum +1
		val maxRequestSize = if (restConfig.maxFileSizeSum <= 50) 51 else restConfig.maxFileSizeSum +1
		val factory = MultipartConfigFactory()
		factory.setMaxFileSize(DataSize.ofMegabytes(maxFileSize.toLong()))
		factory.setMaxRequestSize(DataSize.ofMegabytes(maxRequestSize.toLong()))
		return factory.createMultipartConfig()
	}
}
