package no.nav.soknad.innsending.consumerapis.soknadsmottaker

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.soknad.arkivering.soknadsmottaker.api.HealthApi
import no.nav.soknad.arkivering.soknadsmottaker.api.SoknadApi
import no.nav.soknad.arkivering.soknadsmottaker.infrastructure.ApiClient
import no.nav.soknad.arkivering.soknadsmottaker.infrastructure.Serializer
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.service.maskerFnr
import no.nav.soknad.innsending.service.translate
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("dev | prod")
@Qualifier("mottaker")
class MottakerAPI(
	private val restConfig: RestConfig,
	soknadsmottakerClient: OkHttpClient
) : MottakerInterface, HealthRequestInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	private val mottakerClient: SoknadApi
	private val healthApi: HealthApi

	init {
		Serializer.jacksonObjectMapper.registerModule(JavaTimeModule())
		ApiClient.username = restConfig.sharedUsername
		ApiClient.password = restConfig.sharedPassword

		mottakerClient = SoknadApi(restConfig.soknadsMottakerHost, soknadsmottakerClient)
		healthApi = HealthApi(restConfig.soknadsMottakerHost)
	}


	override fun isReady(): String {
		try {
			healthApi.isReady()
		} catch (e: Exception) {
			logger.warn("Kall for å sjekke om soknadsmottaker er oppe feiler med ${e.message}")
		}
		return "ok"
	}

	override fun isAlive(): String {
		healthApi.isAlive()
		return "ok"
	}

	override fun ping(): String {
		healthApi.isAlive()
		return "pong"
	}

	override fun sendInnSoknad(soknadDto: DokumentSoknadDto, vedleggsListe: List<VedleggDto>) {
		val soknad = translate(soknadDto, vedleggsListe)
		logger.info("${soknadDto.innsendingsId}: klar til å sende inn\n${maskerFnr(soknad)}\ntil ${restConfig.soknadsMottakerHost}")
		mottakerClient.receive(soknad)
		logger.info("${soknadDto.innsendingsId}: sendt inn")
	}

}
