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
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("dev | prod")
@Qualifier("notifikasjon")
class SendTilPublisher(restConfig: RestConfig): PublisherInterface, HealthRequestInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	private val notificationPublisherApi: NewNotificationApi
	private val cancelNotificationPublisherApi: CancelNotificationApi
	private val healthApi: HealthApi

	init {
		jacksonObjectMapper.registerModule(JavaTimeModule())
		ApiClient.username = restConfig.sharedUsername
		ApiClient.password = restConfig.sharedPassword
		notificationPublisherApi = NewNotificationApi(restConfig.soknadsMottakerHost)
		cancelNotificationPublisherApi = CancelNotificationApi(restConfig.soknadsMottakerHost)
		healthApi = HealthApi(restConfig.soknadsMottakerHost)
	}

	override fun avsluttBrukernotifikasjon(soknadRef: SoknadRef) {
		cancelNotificationPublisherApi.cancelNotification(soknadRef)
	}

	override fun opprettBrukernotifikasjon(nyNotifikasjon: AddNotification) {
		notificationPublisherApi.newNotification(nyNotifikasjon)
	}

	override fun isReady(): String {
		logger.info("Publisher isReady start")
		//TODO healthApi.isReady()
		logger.info("Publisher isReady ok")
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