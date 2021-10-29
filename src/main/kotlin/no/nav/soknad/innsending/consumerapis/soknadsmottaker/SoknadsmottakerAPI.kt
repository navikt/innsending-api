package no.nav.soknad.innsending.consumerapis.soknadsmottaker

import no.nav.soknad.arkivering.soknadsMottaker.dto.InnsendtDokumentDto
import no.nav.soknad.arkivering.soknadsMottaker.dto.InnsendtVariantDto
import no.nav.soknad.arkivering.soknadsMottaker.dto.SoknadInnsendtDto
import no.nav.soknad.innsending.dto.DokumentSoknadDto
import no.nav.soknad.innsending.dto.VedleggDto
import no.nav.soknad.innsending.repository.OpplastingsStatus
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SoknadsmottakerAPI(private val mottakerClient: MottakerClient) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun sendInnSoknad(soknadDto: DokumentSoknadDto) {

		logger.info("${soknadDto.innsendingsId}: transformering før innsending")
		val soknad = translate(soknadDto)
		logger.info("${soknadDto.innsendingsId}: klar til å sende inn}")
		mottakerClient.sendInn(soknad)
		logger.info("${soknadDto.innsendingsId}: sendt inn}")
	}

	private fun translate(soknadDto: DokumentSoknadDto): SoknadInnsendtDto {
		return SoknadInnsendtDto(soknadDto.innsendingsId!!, soknadDto.ettersendingsId != null, soknadDto.brukerId
			, soknadDto.tema, soknadDto.innsendtDato!!,translate(soknadDto.vedleggsListe))
	}

	private fun translate(dokumentDtos: List<VedleggDto>): Array<InnsendtDokumentDto> {
		val varianter: List<InnsendtVariantDto> =
		dokumentDtos.filter{ it.erHoveddokument && it.opplastingsStatus == OpplastingsStatus.LASTET_OPP }.map { translate(it)}

		val hovedDokument: List<InnsendtDokumentDto> = dokumentDtos.filter{ it.erHoveddokument && it.opplastingsStatus == OpplastingsStatus.LASTET_OPP  && !it.erVariant}
			.map { InnsendtDokumentDto(it.vedleggsnr!!, it.erHoveddokument, it.tittel, varianter.toTypedArray())}

		// Merk: at det  er antatt at vedlegg ikke har varianter. Hvis vi skal støtte dette må varianter av samme vedlegg linkes sammen
		val vedlegg = dokumentDtos.filter { !it.erHoveddokument && it.opplastingsStatus == OpplastingsStatus.LASTET_OPP }
			.map { InnsendtDokumentDto(it.vedleggsnr!!, it.erHoveddokument, it.tittel, listOf(translate(it)).toTypedArray())}

		return (hovedDokument + vedlegg).toTypedArray()
	}

	private fun translate(dokumentDto: VedleggDto): InnsendtVariantDto {
		return InnsendtVariantDto(dokumentDto.uuid!!, dokumentDto.mimetype, dokumentDto.vedleggsnr,
			dokumentDto.document!!.size.toString(), translateToArkivFormat(dokumentDto), translateToFiltype(dokumentDto) )
	}

	private fun translateToArkivFormat(dokumentDto: VedleggDto): String? {
		return if ("application/pdf-fullversjon".equals(dokumentDto.mimetype, true))
			"FULLVERSJON"
		else if ("application/pdf".equals(dokumentDto.mimetype, true) )
			"ARKIV"
		else if ("application/json".equals(dokumentDto.mimetype, ignoreCase = true) || "application/xml".equals(dokumentDto.mimetype, ignoreCase = true))
			"ORIGINAL"
		else
			null // Er det riktig å bruke null som fallback?
	}

	private fun translateToFiltype(dokumentDto: VedleggDto): String? {
		return if ("application/pdf-fullversjon".equals(dokumentDto.mimetype, true))
			if (dokumentDto.erPdfa) "pdfa" else "pdf"
		else if ("application/pdf".equals(dokumentDto.mimetype, true) )
			if (dokumentDto.erPdfa) "pdfa" else "pdf"
		else if ("application/json".equals(dokumentDto.mimetype, ignoreCase = true))
			"json"
		else if ("application/xml".equals(dokumentDto.mimetype, ignoreCase = true))
			"xml"
		else
			null
	}

}
