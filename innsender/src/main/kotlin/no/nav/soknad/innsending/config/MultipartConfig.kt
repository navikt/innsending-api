package no.nav.soknad.innsending.config

import jakarta.servlet.MultipartConfigElement
import org.springframework.boot.web.servlet.MultipartConfigFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.util.unit.DataSize

@Configuration
class MultipartConfig(
	private val restConfig: RestConfig
) {

	@Bean
	@Profile("!(test | local)")
	fun multipartConfigElement(): MultipartConfigElement? {
		val maxFileSize = restConfig.maxFileSizeSum +1
		val maxRequestSize =  restConfig.maxFileSizeSum +1
		val factory = MultipartConfigFactory()
		factory.setMaxFileSize(DataSize.ofMegabytes(maxFileSize.toLong()))
		factory.setMaxRequestSize(DataSize.ofMegabytes(maxRequestSize.toLong()))
		return factory.createMultipartConfig()
	}

	@Bean
	@Profile("test | local")
	fun multipartConfigElementTest(): MultipartConfigElement? {
		val maxFileSize = 51
		val maxRequestSize = 51
		val factory = MultipartConfigFactory()
		factory.setMaxFileSize(DataSize.ofMegabytes(maxFileSize.toLong()))
		factory.setMaxRequestSize(DataSize.ofMegabytes(maxRequestSize.toLong()))
		return factory.createMultipartConfig()
	}

}
