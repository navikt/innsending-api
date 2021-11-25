package no.nav.soknad.innsending.consumerapis.soknadsfillager

import no.nav.soknad.arkivering.soknadsfillager.dto.FilElementDto
import no.nav.soknad.innsending.config.AppConfiguration
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.repository.OpplastingsStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.stream.Collectors

@Service
class FillagerAPI(private val filLagerClient: FilLagerClient) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun lagreFiler(innsendingsId: String, vedleggDtos: List<VedleggDto>) {
		val filElementDtos: List<FilElementDto> = vedleggDtos.stream()
			.filter {f -> f.opplastingsStatus == OpplastingsStatus.LASTET_OPP}
			.map{e -> FilElementDto(e.uuid!!, e.document, e.opprettetdato)}.collect(Collectors.toList())

		filLagerClient.lagreFiler(filElementDtos)
		logger.info("$innsendingsId: Lagret følgende filer ${filElementDtos.map{it.uuid}.toList().joinToString { "," }}")

	}

	fun hentFiler(innsendingsId: String, vedleggDtos: List<VedleggDto>): List<VedleggDto> {
		val filElementDtos: List<FilElementDto> = vedleggDtos.stream()
			.filter {f -> f.opplastingsStatus == OpplastingsStatus.LASTET_OPP}
			.map{e -> FilElementDto(e.uuid!!, e.document, e.opprettetdato)}.collect(Collectors.toList())

		if (filElementDtos.size == 0) return vedleggDtos

		val hentedeFilerMap: Map<String, FilElementDto> = filLagerClient.hentFiler(filElementDtos).map { it.uuid to it }.toMap()
		logger.info("$innsendingsId: Hentet følgende filer ${filElementDtos.map{it.uuid}.toList().joinToString { "," }}")

		return vedleggDtos.map { VedleggDto( it.id, it.vedleggsnr, it.tittel, it.uuid, it.mimetype, hentedeFilerMap.get(it.uuid)?.fil, it.erHoveddokument, it.erVariant, it.erPdfa, null, it.opplastingsStatus, hentedeFilerMap.get(it.uuid)?.opprettet ?: it.opprettetdato) }.toList()

	}

	fun slettFiler(innsendingsId: String, vedleggDtos: List<VedleggDto>) {
		val filElementDtos: List<FilElementDto> = vedleggDtos.stream()
			.filter {f -> f.opplastingsStatus == OpplastingsStatus.LASTET_OPP}
			.map{e -> FilElementDto(e.uuid!!, e.document, e.opprettetdato)}.collect(Collectors.toList())
		filLagerClient.slettFiler(filElementDtos)

		logger.info("$innsendingsId: Slettet følgende filer ${filElementDtos.map{it.uuid}.toList().joinToString { "," }}")

	}


}
