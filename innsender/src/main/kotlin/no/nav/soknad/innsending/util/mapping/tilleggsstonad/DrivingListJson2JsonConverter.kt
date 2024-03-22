package no.nav.soknad.innsending.util.mapping.tilleggsstonad

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.soknad.innsending.exceptions.BackendErrorException
import no.nav.soknad.innsending.model.DokumentSoknadDto

fun convertToJsonDrivingListJson(
	soknadDto: DokumentSoknadDto,
	jsonFile: ByteArray?
): JsonApplication<JsonDrivingListSubmission> {
	if (jsonFile == null || jsonFile.isEmpty())
		throw BackendErrorException("${soknadDto.innsendingsId}: json fil av søknaden mangler")

	val mapper = jacksonObjectMapper()
	mapper.findAndRegisterModules()
	//mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
	val json = mapper.readValue(jsonFile, Root::class.java)

	return JsonApplication(
		timezone = json.data.metadata.timezone,
		language = json.language,
		personInfo = JsonPersonInfo(
			fornavn = json.data.data.fornavnSoker,
			etternavn = json.data.data.etternavnSoker,
			ident = PersonIdent(ident = json.data.data.fodselsnummerDnummerSoker, identType = IdentType.PERSONNR)
		),
		applicationDetails = convertToJsonDrivingListSubmission(json.data.data, soknadDto)
	)
}


fun convertToJsonDrivingListSubmission(
	drivingListSubmission: Application,
	soknadDto: DokumentSoknadDto
): JsonDrivingListSubmission {
	if (drivingListSubmission.drivinglist == null)
		throw BackendErrorException("${soknadDto.innsendingsId}: Søknaden mangler vedtaksId")

	return JsonDrivingListSubmission(

		fornavnSoker = drivingListSubmission.fornavnSoker,
		etternavnSoker = drivingListSubmission.etternavnSoker,
		harDuNorskFodselsnummerEllerDnummer = drivingListSubmission.harDuNorskFodselsnummerEllerDnummer,
		fodselsnummerDnummerSoker = drivingListSubmission.fodselsnummerDnummerSoker,
		tilleggsopplysninger = drivingListSubmission.tilleggsopplysninger,

		maalgruppeinformasjon = convertToJsonMaalgruppeinformasjon(
			drivingListSubmission.aktiviteterOgMaalgruppe,
			null,
			null
		),

		expencePeriodes = JsonDrivingListExpences(
			selectedVedtaksId = drivingListSubmission.drivinglist.selectedVedtaksId,
			dates = drivingListSubmission.drivinglist.dates.map {
				JsonDailyExpences(
					date = it.date,
					parking = if (it.parking != null && it.parking.isNotEmpty()) it.parking.toDouble() else null,
					betalingsplanId = it.betalingsplanId
				)
			}
		)
	)
}
