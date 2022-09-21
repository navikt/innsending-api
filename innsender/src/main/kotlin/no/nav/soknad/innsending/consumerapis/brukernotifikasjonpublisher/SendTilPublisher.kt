package no.nav.soknad.innsending.consumerapis.brukernotifikasjonpublisher

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.soknad.arkivering.soknadsmottaker.api.CancelNotificationApi
import no.nav.soknad.arkivering.soknadsmottaker.api.HealthApi
import no.nav.soknad.arkivering.soknadsmottaker.api.NewNotificationApi
import no.nav.soknad.arkivering.soknadsmottaker.infrastructure.ApiClient
import no.nav.soknad.arkivering.soknadsmottaker.infrastructure.Serializer.jacksonObjectMapper
import no.nav.soknad.arkivering.soknadsmottaker.model.AddNotification
import no.nav.soknad.arkivering.soknadsmottaker.model.SoknadRef
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("dev | prod")
@Qualifier("notifikasjon")
class SendTilPublisher(
	private val restConfig: RestConfig,
	soknadsmottakerClient: OkHttpClient
): PublisherInterface, HealthRequestInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	private val notificationPublisherApi: NewNotificationApi
	private val cancelNotificationPublisherApi: CancelNotificationApi
	private val healthApi: HealthApi

	init {
		jacksonObjectMapper.registerModule(JavaTimeModule())
		ApiClient.username = restConfig.sharedUsername
		ApiClient.password = restConfig.sharedPassword
		notificationPublisherApi = NewNotificationApi(restConfig.soknadsMottakerHost, soknadsmottakerClient)
		cancelNotificationPublisherApi = CancelNotificationApi(restConfig.soknadsMottakerHost, soknadsmottakerClient)
		healthApi = HealthApi(restConfig.soknadsMottakerHost)
	}

	override fun avsluttBrukernotifikasjon(soknadRef: SoknadRef) {
		cancelNotificationPublisherApi.cancelNotification(soknadRef)
	}

	override fun opprettBrukernotifikasjon(nyNotifikasjon: AddNotification) {
		logger.info("Send melding til ${restConfig.soknadsMottakerHost} for publisering av Brukernotifikasjon for ${nyNotifikasjon.soknadRef.innsendingId}")
		notificationPublisherApi.newNotification(nyNotifikasjon)
	}

	override fun isReady(): String {
		try {
			healthApi.isReady()
		} catch (e: Exception) {
			logger.warn("Kall mot ${restConfig.soknadsMottakerHost} for å sjekke om publisher for brukernotifikasjoner er oppe, feiler", e)
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
}
