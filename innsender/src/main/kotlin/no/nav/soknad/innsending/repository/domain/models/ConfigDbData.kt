package no.nav.soknad.innsending.repository.domain.models

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "config")
data class ConfigDbData(
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id")
	val id: Long?,

	@Column(name = "key", columnDefinition = "varchar")
	val key: String,

	@Column(name = "value", columnDefinition = "varchar", nullable = true)
	val value: String?,

	@Column(name = "created_at", columnDefinition = "TIMESTAMP WITH TIME ZONE")
	val createdAt: LocalDateTime,

	@Column(name = "modified_at", columnDefinition = "TIMESTAMP WITH TIME ZONE", nullable = true)
	val modifiedAt: LocalDateTime?,

	@Column(name = "modified_by", columnDefinition = "varchar", nullable = true)
	val modifiedBy: String?,
)
