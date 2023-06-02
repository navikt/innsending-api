package no.nav.soknad.innsending

import jakarta.annotation.Priority
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.ConfigurableEnvironment

data class ProfileConfig(val profil: String)

@Configuration
@Priority(-1)
class ProfileConfiguration(private val env: ConfigurableEnvironment) {

	@Bean
	fun profileConfig(): ProfileConfig {
		val profil = System.getenv("SPRING_PROFILES_ACTIVE") ?: "test"
		env.setActiveProfiles(profil)

		return ProfileConfig(profil)
	}
}
