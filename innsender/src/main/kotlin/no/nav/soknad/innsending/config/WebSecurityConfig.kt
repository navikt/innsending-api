package no.nav.soknad.innsending.config

import org.springframework.boot.autoconfigure.security.servlet.PathRequest
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter
import org.springframework.security.config.http.SessionCreationPolicy


@Configuration
@EnableGlobalMethodSecurity(securedEnabled = true, prePostEnabled = true)
class WebSecurityConfig : WebSecurityConfigurerAdapter() {

	override fun configure(http: HttpSecurity) {
		http
			.csrf().disable()
			.cors()
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

}
