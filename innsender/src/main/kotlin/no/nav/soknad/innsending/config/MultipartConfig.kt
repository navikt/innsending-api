package no.nav.soknad.innsending.config

import org.springframework.boot.web.servlet.MultipartConfigFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize
import javax.servlet.MultipartConfigElement


@Configuration
class MultipartConfig {

	@Bean
	fun multipartConfigElement(): MultipartConfigElement? {
		val factory = MultipartConfigFactory()
		factory.setMaxFileSize(DataSize.ofMegabytes(100))
		factory.setMaxRequestSize(DataSize.ofMegabytes(100))
		return factory.createMultipartConfig()
	}
}
