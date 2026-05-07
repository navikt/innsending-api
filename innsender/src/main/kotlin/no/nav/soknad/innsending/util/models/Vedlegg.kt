package no.nav.soknad.innsending.util.models

import no.nav.soknad.innsending.model.OpplastingsStatusDto
import no.nav.soknad.innsending.model.VedleggDto
import no.nav.soknad.innsending.repository.domain.enums.OpplastingsStatus
import no.nav.soknad.innsending.repository.domain.models.VedleggDbData
import no.nav.soknad.innsending.util.Constants.KVITTERINGS_NR
import java.time.OffsetDateTime


val List<VedleggDbData>.hovedDokument: VedleggDbData?
	get() {
		return this.find { it.erhoveddokument && !it.ervariant }
	}

val List<VedleggDbData>.hovedDokumentVariant: VedleggDbData?
	get() {
		return this.find { it.erhoveddokument && it.ervariant }
	}

val List<VedleggDbData>.kvittering: VedleggDbData?
	get() {
		return this.find { it.vedleggsnr == KVITTERINGS_NR }
	}

val List<VedleggDto>.hovedDokument: VedleggDto?
	get() {
		return this.find { it.erHoveddokument && !it.erVariant }
	}

val List<VedleggDto>.hovedDokumentVariant: VedleggDto?
	get() {
		return this.find { it.erHoveddokument && it.erVariant }
	}

val List<VedleggDto>.kvittering: VedleggDto?
	get() {
		return this.find { it.vedleggsnr == KVITTERINGS_NR }
	}

val List<VedleggDto>.systemGenererte: List<VedleggDto>
	get() {
		return this.filter {it.erHoveddokument || it.vedleggsnr == KVITTERINGS_NR }
	}

val List<VedleggDto>.ikkeSystemGenererte: List<VedleggDto>
	get() {
		return this.filter {!(it.erHoveddokument || it.vedleggsnr == KVITTERINGS_NR) }
	}


val List<VedleggDto>.sendesIkke: List<VedleggDto>
	get() {
		return this.filter {
			!it.erHoveddokument && (
				it.opplastingsStatus == OpplastingsStatusDto.SendesIkke ||
					it.opplastingsStatus == OpplastingsStatusDto.HarIkkeDokumentasjonen
				)
		}
	}

val List<VedleggDto>.utsattInnsending: List<VedleggDto>
	get() {
		return this.filter {
			!it.erHoveddokument && it.vedleggsnr != KVITTERINGS_NR && (
				it.opplastingsStatus == OpplastingsStatusDto.SendSenere ||
					it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt
				)
		}
	}

val List<VedleggDto>.sokerSenderIkke: List<VedleggDto>
	get() {
		return this.filter {
			!it.erHoveddokument && it.vedleggsnr!=KVITTERINGS_NR && (
				it.opplastingsStatus == OpplastingsStatusDto.SendesIkke ||
				it.opplastingsStatus == OpplastingsStatusDto.HarIkkeDokumentasjonen ||
				it.opplastingsStatus == OpplastingsStatusDto.LevertDokumentasjonTidligere ||
				it.opplastingsStatus == OpplastingsStatusDto.SendesAvAndre ||
				it.opplastingsStatus == OpplastingsStatusDto.LastetOppIkkeRelevantLenger
				)
		}
	}

val List<VedleggDto>.tidligereLevert: List<VedleggDto>
	get() {
		return this.filter {
			!it.erHoveddokument && (
				it.opplastingsStatus == OpplastingsStatusDto.LevertDokumentasjonTidligere
				)
		}
	}

val List<VedleggDto>.navKanInnhente: List<VedleggDto>
	get() {
		return this.filter {
			!it.erHoveddokument && (
				it.opplastingsStatus == OpplastingsStatusDto.NavKanHenteDokumentasjon
				)
		}
	}

val List<VedleggDto>.skalSendesAvAndre: List<VedleggDto>
	get() {
		return this.filter {
			!it.erHoveddokument && (
				it.opplastingsStatus == OpplastingsStatusDto.SendesAvAndre
				)
		}
	}

val List<VedleggDto>.skalEttersendes: List<VedleggDto>
	get() {
		return this.filter {
			!it.erHoveddokument && (
				it.opplastingsStatus == OpplastingsStatusDto.SendSenere
				)
		}
	}

fun innsendteVedlegg(soknadOpprettetDato: OffsetDateTime, vedlegg: List<VedleggDto>): List<VedleggDto> {
	return vedlegg.filter {
		!it.erHoveddokument && it.vedleggsnr != KVITTERINGS_NR && it.opplastingsStatus == OpplastingsStatusDto.Innsendt && (it.innsendtdato
			?: it.opprettetdato).isBefore(soknadOpprettetDato)
	}
}


val List<VedleggDto>.ubehandledeVedlegg: List<VedleggDto>
	get() {
		return this.filter {
			!it.erHoveddokument && it.vedleggsnr != KVITTERINGS_NR
				&& ((it.erPakrevd && it.vedleggsnr == "N6") || it.vedleggsnr != "N6")
				&& !(erBehandlet(it.opplastingsStatus))
		}
	}

fun erBehandlet(opplastingsStatus: OpplastingsStatusDto): Boolean {
	opplastingsStatus.let {
		return it == OpplastingsStatusDto.Innsendt
			|| it == OpplastingsStatusDto.KlarForInnsending
			|| it == OpplastingsStatusDto.SendesAvAndre
			|| it == OpplastingsStatusDto.LastetOpp
			|| it == OpplastingsStatusDto.NavKanHenteDokumentasjon
			|| it == OpplastingsStatusDto.LevertDokumentasjonTidligere
			|| it == OpplastingsStatusDto.HarIkkeDokumentasjonen
	}
}

val List<VedleggDto>.ikkeBesvarteVedlegg: List<VedleggDto>
	get() {
		return this.filter {
			!it.erHoveddokument
				&& ((it.erPakrevd && it.vedleggsnr == "N6") || it.vedleggsnr != "N6")
				&& it.opplastingsStatus == OpplastingsStatusDto.IkkeValgt
		}
	}
