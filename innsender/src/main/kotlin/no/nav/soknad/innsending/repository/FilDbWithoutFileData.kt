package no.nav.soknad.innsending.repository

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
}
