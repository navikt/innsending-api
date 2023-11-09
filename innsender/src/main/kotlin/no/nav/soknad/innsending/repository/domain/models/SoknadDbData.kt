package no.nav.soknad.innsending.repository.domain.models

import jakarta.persistence.*
import no.nav.soknad.innsending.model.VisningsType
import no.nav.soknad.innsending.repository.domain.enums.ArkiveringsStatus
import no.nav.soknad.innsending.repository.domain.enums.SoknadsStatus
import no.nav.soknad.innsending.util.Constants
import java.time.LocalDateTime

@Entity
@Table(name = "soknad")
data class SoknadDbData(
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") val id: Long?,
	@Column(name = "innsendingsid") val innsendingsid: String,
	@Column(name = "tittel", columnDefinition = "varchar") val tittel: String,
	@Column(name = "skjemanr", columnDefinition = "varchar") val skjemanr: String,
	@Column(name = "tema", columnDefinition = "varchar") val tema: String,
	@Column(name = "spraak", columnDefinition = "varchar") val spraak: String?,
	@Column(name = "status", columnDefinition = "varchar") val status: SoknadsStatus,
	@Column(name = "brukerid", columnDefinition = "varchar") val brukerid: String,
	@Column(name = "ettersendingsid", columnDefinition = "varchar") val ettersendingsid: String?,
	@Column(name = "opprettetdato", columnDefinition = "TIMESTAMP WITH TIME ZONE") val opprettetdato: LocalDateTime,
	@Column(name = "endretdato", columnDefinition = "TIMESTAMP WITH TIME ZONE") val endretdato: LocalDateTime?,
	@Column(name = "innsendtdato", columnDefinition = "TIMESTAMP WITH TIME ZONE") val innsendtdato: LocalDateTime?,
	@Column(name = "visningssteg", columnDefinition = "long") val visningssteg: Long? = 0,
	@Column(
		name = "visningstype",
		columnDefinition = "varchar"
	) val visningstype: VisningsType? = if (ettersendingsid != null) VisningsType.ettersending else VisningsType.dokumentinnsending,
	@Column(name = "kanlasteoppannet", columnDefinition = "boolean") val kanlasteoppannet: Boolean? = true,
	@Column(
		name = "forsteinnsendingsdato",
		columnDefinition = "TIMESTAMP WITH TIME ZONE"
	) val forsteinnsendingsdato: LocalDateTime?,
	@Column(
		name = "ettersendingsfrist",
		columnDefinition = "int"
	) val ettersendingsfrist: Long? = Constants.DEFAULT_FRIST_FOR_ETTERSENDELSE,
	@Column(name = "arkiveringsstatus", columnDefinition = "varchar") val arkiveringsstatus: ArkiveringsStatus,
	@Column(name = "skjemapath", columnDefinition = "varchar") val skjemapath: String?
)
