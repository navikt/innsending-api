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
		val factory = MultipartConfigFactory()
		factory.setMaxFileSize(DataSize.ofMegabytes(restConfig.maxFileSize.toLong()+1))
		factory.setMaxRequestSize(DataSize.ofMegabytes(restConfig.maxFileSizeSum.toLong()+1))
		return factory.createMultipartConfig()
	}
}
