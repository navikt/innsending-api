package no.nav.soknad.innsending.config

import jakarta.servlet.MultipartConfigElement
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.MultipartConfigFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.util.unit.DataSize


@Configuration
class MultipartConfig {

	@Value("\${multipart-file-size}")
	private val maxMultipartFileSize: Long = 151

	@Value("\${multipart-request-size}")
	private val maxMultipartRequestSize: Long = 301

	@Bean
	fun multipartConfigElement(): MultipartConfigElement? {
		val factory = MultipartConfigFactory()
		factory.setMaxFileSize(DataSize.ofMegabytes(maxMultipartFileSize))
		factory.setMaxRequestSize(DataSize.ofMegabytes(maxMultipartRequestSize))
		return factory.createMultipartConfig()
	}
}
