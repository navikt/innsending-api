package no.nav.soknad.innsending.repository.domain.models

import jakarta.persistence.*
import no.nav.soknad.innsending.repository.domain.enums.HendelseType
import java.time.LocalDateTime

@Entity
@Table(name = "hendelse")
data class HendelseDbData(
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") val id: Long?,
	@Column(name = "innsendingsid", columnDefinition = "varchar") val innsendingsid: String,
	@Column(name = "hendelsetype", columnDefinition = "varchar") val hendelsetype: HendelseType,
	@Column(name = "tidspunkt", columnDefinition = "TIMESTAMP WITH TIME ZONE") val tidspunkt: LocalDateTime,
	@Column(name = "skjemanr", columnDefinition = "varchar") val skjemanr: String?,
	@Column(name = "tema", columnDefinition = "varchar") val tema: String?,
	@Column(name = "erettersending", columnDefinition = "varchar") val erettersending: Boolean?
)
