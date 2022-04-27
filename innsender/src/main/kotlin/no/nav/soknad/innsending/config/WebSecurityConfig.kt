package no.nav.soknad.innsending.config

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.security.servlet.PathRequest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import java.util.List


@Configuration
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
class WebSecurityConfig(private val config: RestConfig) : WebSecurityConfigurerAdapter() {

	private val logger = LoggerFactory.getLogger(javaClass)

	override fun configure(http: HttpSecurity) {
		val corsConfiguration = CorsConfiguration()
		corsConfiguration.allowedHeaders = List.of("Authorization", "Cache-Control", "Content-Type")
		corsConfiguration.allowedOrigins = List.of("*")
		corsConfiguration.allowedMethods =
			List.of(RequestMethod.GET.name, RequestMethod.POST.name, RequestMethod.DELETE.name, RequestMethod.OPTIONS.name, RequestMethod.PATCH.name)
		corsConfiguration.allowCredentials = true
		corsConfiguration.exposedHeaders = List.of("Authorization")

		http
			.csrf().disable()
			.cors().configurationSource { request -> corsConfiguration }
			.and()
			.authorizeRequests()
			.requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
			.antMatchers(HttpMethod.GET, "/swagger-ui**").permitAll()
			.antMatchers(HttpMethod.POST, "/login", "/register").permitAll()
			.antMatchers("/frontend").hasAnyRole("USER", "ADMIN")
			.antMatchers("/frontend").authenticated()
			.antMatchers("/innsendt").hasAnyRole("USER", "ADMIN")
			.antMatchers("/innsendt").authenticated()
			.antMatchers("/innsending").hasAnyRole("USER", "ADMIN")
			.antMatchers("/innsending").authenticated()
			.and()
			.httpBasic()
			.and()
			.sessionManagement()
			.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
	}

/*
	@Autowired
	fun configureGlobal(auth: AuthenticationManagerBuilder) {
		auth.inMemoryAuthentication()
			.withUser(config.restConfig.fileWriter)
			.password("{noop}${config.restConfig.fileWriterPassword}")
			.roles("USER")
			.and()
			.withUser(config.restConfig.fileUser)
			.password("{noop}${config.restConfig.fileUserPassword}")
			.roles("USER")

		logger.info("Konfigurert authenticationManager")
	}
*/
}
