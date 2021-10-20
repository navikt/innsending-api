package no.nav.soknad.innsending.repository

import java.time.LocalDateTime
import javax.persistence.*

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
	@Column(name = "skjemaurl", columnDefinition = "varchar") val skjemaurl: String?
) {

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as SoknadDbData

		if (id != other.id) return false

		return true
	}

	override fun hashCode() = id.hashCode()
}
