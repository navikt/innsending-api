package no.nav.soknad.innsending.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.SimpleAsyncTaskExecutor
import org.springframework.core.task.TaskExecutor

@Configuration
class AsyncConfig {

	@Bean("antivirusTaskExecutor")
	fun antivirusTaskExecutor(): TaskExecutor = SimpleAsyncTaskExecutor("antivirus-")
}
