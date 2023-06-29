package no.nav.soknad.innsending.repository

import jakarta.persistence.*

@Entity
@Table(name = "alive")
data class AliveDbData(
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") val id: Long?,
	@Column(name = "test", columnDefinition = "varchar") val test: String
) {

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as AliveDbData

		return id == other.id
	}

	override fun hashCode() = id.hashCode()

}
