package no.nav.soknad.innsending.config

import jakarta.servlet.MultipartConfigElement
import org.springframework.boot.web.servlet.MultipartConfigFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize


@Configuration
class MultipartConfig {

	@Bean
	fun multipartConfigElement(): MultipartConfigElement? {
		val factory = MultipartConfigFactory()
		factory.setMaxFileSize(DataSize.ofMegabytes(51))
		factory.setMaxRequestSize(DataSize.ofMegabytes(100))
		return factory.createMultipartConfig()
	}
}
