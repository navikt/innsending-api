package no.nav.soknad.innsending.utils.builders

import no.nav.soknad.innsending.safselvbetjening.generated.enums.AvsenderMottakerIdType
import no.nav.soknad.innsending.safselvbetjening.generated.enums.Datotype
import no.nav.soknad.innsending.safselvbetjening.generated.enums.Journalposttype
import no.nav.soknad.innsending.safselvbetjening.generated.enums.Journalstatus
import no.nav.soknad.innsending.safselvbetjening.generated.enums.Kanal
import no.nav.soknad.innsending.safselvbetjening.generated.hentdokumentoversikt.AvsenderMottaker
import no.nav.soknad.innsending.safselvbetjening.generated.hentdokumentoversikt.DokumentInfo
import no.nav.soknad.innsending.safselvbetjening.generated.hentdokumentoversikt.Journalpost
import no.nav.soknad.innsending.safselvbetjening.generated.hentdokumentoversikt.RelevantDato


data class JournalPostTestBuilder(
	var journalpostId: String = "123",
	var tittel: String? = "Tittel",
	var eksternReferanseId: String? = "12345678",
	var journalstatus: Journalstatus? = Journalstatus.JOURNALFOERT,
	var journalposttype: Journalposttype = Journalposttype.I,
	var tema: String? = "AAP",
	var kanal: Kanal? = Kanal.NAV_NO,
	var relevanteDatoer: List<RelevantDato?> = listOf(RelevantDato("2022-05-24T11:02:30", Datotype.DATO_OPPRETTET)),
	var avsender: AvsenderMottaker? = AvsenderMottaker("12345678901", type = AvsenderMottakerIdType.FNR, navn = "Test Bruker"),
	var dokumenter: List<DokumentInfo?>? = listOf(
		DokumentInfo("NAV 08-09.06", "NAV 08-09.06"),
		DokumentInfo("N6", "Et vedleggEgenerklæring og sykmelding")
	),
) {

	fun build(): Journalpost {
		return Journalpost(
			journalpostId = journalpostId,
			tittel = tittel,
			eksternReferanseId = eksternReferanseId,
			journalstatus = journalstatus,
			journalposttype = journalposttype,
			tema = tema,
			kanal = kanal,
			relevanteDatoer = relevanteDatoer,
			avsender = avsender,
			dokumenter = dokumenter,
		)
	}
}

