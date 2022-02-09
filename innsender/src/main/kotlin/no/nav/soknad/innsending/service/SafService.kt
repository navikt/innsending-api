package no.nav.soknad.innsending.service

import no.nav.soknad.innsending.consumerapis.saf.SafInterface
import no.nav.soknad.innsending.consumerapis.saf.dto.Dokument
import no.nav.soknad.innsending.dto.AktivSakDto
import no.nav.soknad.innsending.dto.InnsendtVedleggDto
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class SafService(val safApi: SafInterface) {

	fun hentInnsendteSoknader(brukerId: String): List<AktivSakDto> {
		val innsendte = safApi.hentBrukersSakerIArkivet(brukerId)
		if (innsendte == null) return emptyList()

		return innsendte.map { AktivSakDto(it.eksternReferanseId, finnBrevKode(it.dokumenter), it.tittel, it.tema,
				konverterTilDateTime(it.datoMottatt), erEttersending(it.dokumenter), konverterTilVedleggsliste(it.dokumenter) ) }.toList()
	}

	private fun konverterTilDateTime(dateString: String): LocalDateTime {
		val formatter: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
		return LocalDateTime.parse(dateString, formatter)
	}

	private fun erEttersending(dokumenter: List<Dokument>): Boolean {
		val hoveddokumenter = dokumenter.filter { it.k_tilkn_jp_som == "HOVEDDOKUMENT"}.toList()
		return hoveddokumenter.map { it.brevkode }.toList().contains("NAVe")
	}

	private fun finnBrevKode(dokumenter: List<Dokument>): String {
		val hoveddokumenter = dokumenter.filter { it.k_tilkn_jp_som == "HOVEDDOKUMENT"}.toList()
		return hoveddokumenter.map { it.brevkode }.first()!!.replace("NAVe", "NAV")
	}

	private fun konverterTilVedleggsliste(dokumenter: List<Dokument>): List<InnsendtVedleggDto> {
		return dokumenter.map {InnsendtVedleggDto(it.k_tilkn_jp_som, it.tittel)}.toList()
	}
}
