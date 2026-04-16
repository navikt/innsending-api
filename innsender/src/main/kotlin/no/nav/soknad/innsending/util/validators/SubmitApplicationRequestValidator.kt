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
	validerIdentifikator(
		feltnavn = "avsender.id",
		verdi = avsender?.id,
		format = avsenderIdRegex,
		formatBeskrivelse = "9 siffer",
	)
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
