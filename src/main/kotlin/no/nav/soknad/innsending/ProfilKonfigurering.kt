package no.nav.soknad.innsending

import org.springframework.context.annotation.Configuration
import org.springframework.web.WebApplicationInitializer
import javax.annotation.Priority
import javax.servlet.ServletContext
import javax.servlet.ServletException

@Configuration
@Priority(-1)
class ProfilKonfigurering: WebApplicationInitializer  {
	@Throws(ServletException::class)
	override fun onStartup(servletContext: ServletContext) {
		val env = System.getenv("SPRING_PROFILES_ACTIVE") ?: "test"
		servletContext.setInitParameter("spring.profiles.active", env)
	}
}
