package no.nav.soknad.innsending.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.security.servlet.PathRequest
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy

@Configuration
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
class WebSecurityConfig(private val config: AppConfiguration) : WebSecurityConfigurerAdapter() {

	private val logger = LoggerFactory.getLogger(javaClass)

	override fun configure(http: HttpSecurity) {
		http
			.csrf().disable()
			.authorizeRequests()
			.requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
			.antMatchers(HttpMethod.POST, "/login", "/register", "/swagger-ui").permitAll()
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
