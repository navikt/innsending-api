package no.nav.soknad.innsending.util.validators

import no.nav.soknad.innsending.exceptions.ErrorCode
import no.nav.soknad.innsending.exceptions.IllegalActionException
import no.nav.soknad.innsending.model.SubmitApplicationRequest

private val whitespaceRegex = Regex("""\s""")
private val avsenderIdRegex = Regex("""\d{9}""")
private val brukerIdRegex = Regex("""\d{11}""")

fun SubmitApplicationRequest.validerBrukerOgAvsender() {
	validerIdentifikator(
		feltnavn = "bruker",
		verdi = bruker,
		format = brukerIdRegex,
		formatBeskrivelse = "11 siffer",
	)
	validerAvsender()
	validerIdentifikator(
		feltnavn = "avsender.id",
		verdi = avsender?.id,
		format = avsenderIdRegex,
		formatBeskrivelse = "9 siffer",
	)
}

private fun SubmitApplicationRequest.validerAvsender() {
	val avsender = avsender ?: return
	val harAvsenderId = !avsender.id.isNullOrBlank()
	val harAvsenderIdType = avsender.idType != null

	if (harAvsenderId.xor(harAvsenderIdType)) {
		throw IllegalActionException(
			message = "avsender.id og avsender.idType må begge oppgis hvis en av dem er satt",
			errorCode = ErrorCode.ILLEGAL_ARGUMENT,
		)
	}

	if (!harAvsenderId && !harAvsenderIdType && avsender.navn.isNullOrBlank()) {
		throw IllegalActionException(
			message = "avsender.navn må oppgis når avsender.id og avsender.idType ikke er satt",
			errorCode = ErrorCode.ILLEGAL_ARGUMENT,
		)
	}
}

private fun validerIdentifikator(
	feltnavn: String,
	verdi: String?,
	format: Regex,
	formatBeskrivelse: String,
) {
	if (verdi == null) {
		return
	}

	if (whitespaceRegex.containsMatchIn(verdi)) {
		throw IllegalActionException(
			message = "$feltnavn kan ikke inneholde mellomrom",
			errorCode = ErrorCode.ILLEGAL_ARGUMENT,
		)
	}

	if (!format.matches(verdi)) {
		throw IllegalActionException(
			message = "$feltnavn må være $formatBeskrivelse",
			errorCode = ErrorCode.ILLEGAL_ARGUMENT,
		)
	}
}
