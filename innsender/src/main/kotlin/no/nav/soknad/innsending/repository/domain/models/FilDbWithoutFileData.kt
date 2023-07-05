package no.nav.soknad.innsending.repository.domain.models

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "fil")
data class FilDbWithoutFileData(
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") val id: Long?,
	@Column(name = "vedleggsid", columnDefinition = "long") val vedleggsid: Long,
	@Column(name = "filnavn", columnDefinition = "varchar") val filnavn: String,
	@Column(name = "mimetype", columnDefinition = "varchar") val mimetype: String,
	@Column(name = "storrelse", columnDefinition = "long") val storrelse: Int?,
	@Column(name = "opprettetdato", columnDefinition = "TIMESTAMP WITH TIME ZONE") val opprettetdato: LocalDateTime
) {

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as FilDbWithoutFileData

		return id == other.id
	}

	override fun hashCode(): Int {
		var result = id?.hashCode() ?: 0
		result = 31 * result + vedleggsid.hashCode()
		result = 31 * result + filnavn.hashCode()
		result = 31 * result + mimetype.hashCode()
		result = 31 * result + (storrelse ?: 0)
		result = 31 * result + opprettetdato.hashCode()
		return result
	}
}
