package no.nav.soknad.pdfutilities

data class TextToPdfModel(
    val sprak: String = "nb-NO",
    val beskrivelse: String,
    val side: String,
    val av: String,
    val tittel: String,
    val dato: String,
    val opplastetTidspunkt: String,
    val textInput: String
)
