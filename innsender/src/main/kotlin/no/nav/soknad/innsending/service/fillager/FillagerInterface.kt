package no.nav.soknad.innsending.service.fillager

import org.springframework.core.io.Resource

interface FillagerInterface {

	fun lagreFil(fil: Resource, vedleggId: String, innsendingId: String, namespace: FillagerNamespace, spraak: String? = "nb"): FilMetadata
	fun hentFil(filId: String, innsendingId: String, namespace: FillagerNamespace): Fil?
	fun hentFilinnhold(filId: String, innsendingId: String, namespace: FillagerNamespace): ByteArray?
	fun oppdaterStatusForInnsending(innsendingId: String, namespace: FillagerNamespace, status: FilStatus)
	fun slettFil(filId: String, innsendingId: String, namespace: FillagerNamespace): Boolean
	fun slettFiler(innsendingId: String, vedleggId: String?, namespace: FillagerNamespace): Boolean

}
