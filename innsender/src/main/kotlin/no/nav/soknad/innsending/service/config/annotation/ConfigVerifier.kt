package no.nav.soknad.innsending.service.config.annotation

import no.nav.soknad.innsending.service.config.ConfigService
import no.nav.soknad.innsending.service.config.verifyValue
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

@Aspect
@Component
@Order(20)
class ConfigVerifier(
	private val configService: ConfigService,
) {
	@Before("@annotation(verify)")
	@Throws(Throwable::class)
	fun verify(verify: VerifyConfigValue) {
		configService.getConfig(verify.config)
			.verifyValue(verify.value) { ConfigVerificationException(verify.message, verify.config, httpStatus = verify.httpStatus) }
	}
}
