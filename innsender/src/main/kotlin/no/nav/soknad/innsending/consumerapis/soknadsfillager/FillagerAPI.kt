package no.nav.soknad.innsending.consumerapis.soknadsfillager

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import no.nav.soknad.arkivering.soknadsfillager.api.FilesApi
import no.nav.soknad.arkivering.soknadsfillager.api.HealthApi
//import no.nav.soknad.arkivering.soknadsfillager.api.HealthApi
import no.nav.soknad.arkivering.soknadsfillager.infrastructure.ApiClient
import no.nav.soknad.arkivering.soknadsfillager.infrastructure.Serializer.jacksonObjectMapper
import no.nav.soknad.arkivering.soknadsfillager.model.FileData
import no.nav.soknad.innsending.config.RestConfig
import no.nav.soknad.innsending.consumerapis.HealthRequestInterface
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.repository.OpplastingsStatus
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import java.time.ZoneOffset
import kotlin.streams.toList

@Service
@Profile("dev | prod")
@Qualifier("fillager")
class FillagerAPI(private val restConfig: RestConfig): FillagerInterface, HealthRequestInterface {

	private val logger = LoggerFactory.getLogger(javaClass)

	private val filesApi: FilesApi
	private val healthApi: HealthApi

	init {
		jacksonObjectMapper.registerModule(JavaTimeModule())
		ApiClient.username = restConfig.sharedUsername
		ApiClient.password = restConfig.sharedPassword
		filesApi = FilesApi(restConfig.filestorageHost)
		healthApi = HealthApi(restConfig.filestorageHost)
	}

	override fun ping(): String {
		healthApi.ping()
		return "pong"
	}
	override fun isReady(): String {
		logger.info("Fillager isReady start")
		//TODO healthApi.isReady()
		logger.info("Fillager isReady ok")
		return "ok"
	}
	override fun isAlive(): String {
		healthApi.isReady()
		return "ok"
	}


	override fun lagreFiler(innsendingsId: String, vedleggDtos: List<VedleggDto>) {
		val fileData: List<FileData> = vedleggDtos.stream()
			.filter {it.opplastingsStatus == OpplastingsStatus.LASTET_OPP}
			.map { FileData(it.uuid!!, it.document, it.opprettetdato.atOffset(ZoneOffset.UTC)) }.toList()

		filesApi.addFiles(fileData, innsendingsId)
		logger.info("$innsendingsId: Lagret følgende filer ${fileData.map { it.id }.toList().joinToString { "," }}")
	}

	override fun hentFiler(innsendingsId: String, vedleggDtos: List<VedleggDto>): List<VedleggDto> {
		val fileData: List<FileData> = vedleggDtos.stream()
			.filter {it.opplastingsStatus == OpplastingsStatus.LASTET_OPP}
			.map { FileData(it.uuid!!, it.document, it.opprettetdato.atOffset(ZoneOffset.UTC)) }.toList()

		if (fileData.isEmpty()) return vedleggDtos

		val hentedeFilerMap: Map<String, FileData> = hentFiler(fileData, innsendingsId).associateBy { it.id }
		logger.info("$innsendingsId: Hentet følgende filer ${hentedeFilerMap.map{it.key}.toList().joinToString { "," }}")

		return vedleggDtos.map { VedleggDto( it.id, it.vedleggsnr, it.tittel, it.uuid, it.mimetype,
			hentedeFilerMap.get(it.uuid)?.content ?: it.document, it.erHoveddokument, it.erVariant, it.erPdfa, it.erPakrevd,
			it.skjemaurl, it.opplastingsStatus, hentedeFilerMap.get(it.uuid)?.createdAt?.toLocalDateTime() ?: it.opprettetdato) }
			.toList()

	}

	private fun hentFiler(filData: List<FileData>, innsendingsId: String): List<FileData> {

		val idChunks = filData.stream().map{ it.id}.toList()
			.chunked(restConfig.filesInOneRequestToFilestorage)

		return idChunks
			.map { performGetCall(innsendingsId, it) }
			.flatten()
	}

	private fun performGetCall(innsendingsId: String, fileIds: List<String>): List<FileData> {
		return filesApi.findFilesByIds(fileIds.map{it}.toList().joinToString { "," }, innsendingsId)
	}

	override fun slettFiler(innsendingsId: String, vedleggDtos: List<VedleggDto>) {
		val fileids: String = vedleggDtos.stream()
			.filter {it.opplastingsStatus == OpplastingsStatus.LASTET_OPP}
			.map { it.uuid!! }.toList().joinToString { "," }

		filesApi.deleteFiles(fileids, innsendingsId)

		logger.info("$innsendingsId: Slettet følgende filer $fileids")

	}

}