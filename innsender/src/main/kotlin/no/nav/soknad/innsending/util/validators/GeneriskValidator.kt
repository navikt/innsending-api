package no.nav.soknad.innsending.util.validators

import no.nav.soknad.innsending.exceptions.IllegalActionException
import kotlin.reflect.KProperty1

inline fun <reified T : Any> validerLikeFelter(
	objekt: T,
	eksisterendeObjekt: T,
	felter: List<KProperty1<T, Any?>>
) {
	val ugyldigeFelter = felter.filter {
		it.get(objekt) != it.get(eksisterendeObjekt)
	}

	if (ugyldigeFelter.isNotEmpty()) {
		val message = "Felter er ikke like for ${T::class.simpleName}: ${ugyldigeFelter.joinToString(", ") { it.name }}"
		throw IllegalActionException(
			"Felter er ikke like for ${T::class.simpleName}",
			message
		)
	}
}
