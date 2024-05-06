package no.nav.soknad.innsending.repository.domain.models

import jakarta.persistence.*
import no.nav.soknad.innsending.model.Opplastingsvalg

@Entity
@Table(name = "vedleggvisningsregel")
data class VedleggVisningsRegelDbData(
	@Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "id") val id: Long?,
	@Column(name = "vedleggsid", columnDefinition = "long") val vedleggsid: Long,
	@Column(name = "radiovalg", columnDefinition = "varchar") val radiovalg: Opplastingsvalg,
	@Column(name = "kommentarledetekst", columnDefinition = "varchar") val kommentarledetekst: String?,
	@Column(name = "kommentarbeskivelsestekst", columnDefinition = "varchar") val kommentarbeskivelsestekst: String?,
	@Column(name = "notifikasjonstekst", columnDefinition = "varchar") val notifikasjonstekst: String?,
){
	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (javaClass != other?.javaClass) return false

		other as VedleggVisningsRegelDbData

		return id == other.id
	}

	override fun hashCode() = id.hashCode()

}

