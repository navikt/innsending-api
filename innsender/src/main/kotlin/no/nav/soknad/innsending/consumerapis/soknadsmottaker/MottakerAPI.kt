package no.nav.soknad.innsending.consumerapis.soknadsmottaker

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.soknad.arkivering.soknadsmottaker.api.HealthApi
import no.nav.soknad.arkivering.soknadsmottaker.model.Soknad
import no.nav.soknad.arkivering.soknadsmottaker.model.DocumentData
import no.nav.soknad.arkivering.soknadsmottaker.model.Varianter
import no.nav.soknad.arkivering.soknadsmottaker.api.SoknadApi
import no.nav.soknad.arkivering.soknadsmottaker.infrastructure.ApiClient
import no.nav.soknad.arkivering.soknadsmottaker.infrastructure.Serializer
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.model.DokumentSoknadDto
import no.nav.soknad.innsending.model.Mimetype
import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.repository.OpplastingsStatus
//import no.nav.soknad.innsending.supervision.HealthCheker
//import no.nav.soknad.innsending.supervision.HealthCheker.Ping
//import no.nav.soknad.innsending.supervision.HealthCheker.PingMetadata
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service

@Service
@Profile("dev | prod")
@Qualifier("mottaker")
class MottakerAPI(private val restConfig: RestConfig): MottakerInterface, HealthRequestInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	private val mottakerClient: SoknadApi
	private val healthApi: HealthApi

	init {
		Serializer.jacksonObjectMapper.registerModule(JavaTimeModule())
		ApiClient.username = restConfig.sharedUsername
		ApiClient.password = restConfig.sharedPassword
		mottakerClient = SoknadApi(restConfig.soknadsMottakerHost)
		healthApi = HealthApi(restConfig.soknadsMottakerHost)
	}

/*
	override fun ping(): Ping {
		val start = System.currentTimeMillis()
		try {
			healthApi.ping()
			return Ping(PingMetadata(restConfig.soknadsMottakerHost, "Endepunkt for innsending av søknad", true )
				, null, null, System.currentTimeMillis()-start)
		} catch (e: Exception) {
			return Ping(PingMetadata(restConfig.soknadsMottakerHost, "Endepunkt for innsending av søknad", true )
				, e.message, e, System.currentTimeMillis()-start)
		}
	}
*/

	override fun isReady(): String {
		logger.debug("Soknadsmottaker isReady start")
		healthApi.isReady()
		logger.debug("Soknadsmottaker isReady ok")
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

	override fun sendInnSoknad(soknadDto: DokumentSoknadDto) {

		logger.info("${soknadDto.innsendingsId}: transformering før innsending")
		val soknad = translate(soknadDto)
		logger.info("${soknadDto.innsendingsId}: klar til å sende inn til ${restConfig.soknadsMottakerHost}")
		mottakerClient.receive(soknad)
		logger.info("${soknadDto.innsendingsId}: sendt inn}")
	}

	private fun translate(soknadDto: DokumentSoknadDto): Soknad {
		return Soknad(soknadDto.innsendingsId!!, soknadDto.ettersendingsId != null, soknadDto.brukerId
			, soknadDto.tema, translate(soknadDto.vedleggsListe))
	}

	private fun translate(vedleggDtos: List<VedleggDto>): List<DocumentData> {
		/*
		Mappe fra liste av vedleggdto til en liste av dokument inneholdene liste av varianter.
		Det er antatt at det kun er hoveddokumentet som vil ha varianter.
		Vedleggdto inneholder både dokument- og vedlegginfo
		 */
		// Lag documentdata for hoveddokumentet (finn alle vedleggdto markert som hoveddokument)
		val hoveddokumentVedlegg: List<Varianter> =
			vedleggDtos.filter{ it.erHoveddokument && it.opplastingsStatus.equals(OpplastingsStatusDto.lastetOpp) }.map { translate(it)}

		val hovedDokument: DocumentData = vedleggDtos
			.filter{ it.erHoveddokument && it.opplastingsStatus.equals(OpplastingsStatusDto.lastetOpp)  && !it.erVariant}
			.map { DocumentData(it.vedleggsnr!!, it.erHoveddokument, it.tittel, hoveddokumentVedlegg)}
			.first()

		// Merk: at det  er antatt at vedlegg ikke har varianter. Hvis vi skal støtte dette må varianter av samme vedlegg linkes sammen
		val vedlegg: List<DocumentData> = vedleggDtos
			.filter { !it.erHoveddokument && it.opplastingsStatus.equals(OpplastingsStatusDto.lastetOpp) }
			.map { DocumentData(it.vedleggsnr!!, it.erHoveddokument, it.tittel, listOf(translate(it)))}

		return listOf(hovedDokument) + vedlegg
	}

	private fun translate(dokumentDto: VedleggDto): Varianter {
		return Varianter(dokumentDto.uuid!!, dokumentDto.mimetype.toString(),
			(dokumentDto.vedleggsnr ?: "N6") +"."+filExtention(dokumentDto),
			filExtention(dokumentDto) )
	}

	private fun filExtention(dokumentDto: VedleggDto): String =
		when (dokumentDto.mimetype) {
			Mimetype.imageSlashPng -> "png"
			Mimetype.imageSlashJpeg -> "jpeg"
			Mimetype.applicationSlashJson -> "json"
			Mimetype.applicationSlashPdf -> if (dokumentDto.erPdfa) "pdfa" else "pdf"
			else -> ""
		}

}
