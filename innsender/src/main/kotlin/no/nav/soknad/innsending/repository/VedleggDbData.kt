package no.nav.soknad.innsending.repository

import java.time.LocalDateTime
import javax.persistence.*

@Entity
@Table(name = "vedlegg")
data class VedleggDbData(
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") val id: Long?,
	@Column(name = "soknadsid", columnDefinition = "long") val soknadsid: Long,
	@Column(name = "status", columnDefinition = "varchar") val status: OpplastingsStatus,
	@Column(name = "erhoveddokument", columnDefinition = "boolean") val erhoveddokument: Boolean,
	@Column(name = "ervariant", columnDefinition = "boolean") val ervariant: Boolean,
	@Column(name = "erpdfa", columnDefinition = "boolean") val erpdfa: Boolean,
	@Column(name = "erpakrevd", columnDefinition = "boolean") val erpakrevd: Boolean,
	@Column(name = "vedleggsnr", columnDefinition = "varchar") val vedleggsnr: String?,
	@Column(name = "tittel", columnDefinition = "varchar") val tittel: String,
	@Column(name = "mimetype", columnDefinition = "varchar") val mimetype: String?,
	@Column(name = "uuid", columnDefinition = "varchar") val uuid: String?,
	@Column(name = "opprettetdato", columnDefinition = "TIMESTAMP WITH TIME ZONE") val opprettetdato: LocalDateTime,
	@Column(name = "endretdato", columnDefinition = "TIMESTAMP WITH TIME ZONE") val endretdato: LocalDateTime,
	@Column(name = "vedleggsurl", columnDefinition = "varchar") val vedleggsurl: String?
) {

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as VedleggDbData

		if (id != other.id) return false

		return true
	}

	override fun hashCode() = id.hashCode()
}