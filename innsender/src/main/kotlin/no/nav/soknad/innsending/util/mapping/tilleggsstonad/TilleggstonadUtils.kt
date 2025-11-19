package no.nav.soknad.innsending.util.mapping.tilleggsstonad


const val stotteTilPassAvBarn  = "NAV 11-12.15B"
const val stotteTilLaeremidler = "NAV 11-12.16B"
const val stotteTilBolig = "NAV 11-12.19B"
const val stotteTilFlytting = "NAV 11-12.23B"
const val reiseDaglig = "NAV 11-12.21B"
const val reiseSamling = "NAV 11-12.17B"
const val reiseOppstartSlutt = "NAV 11-12.18B"
const val reiseArbeid = "NAV 11-12.22B"
const val kjoreliste = "NAV 11-12.24B"

const val ungdomsprogram_reiseDaglig = "NAV 76-05.01"
const val ungdomsprogram_reiseSamling = "NAV 76-05.02"
const val ungdomsprogram_reiseOppstartSlutt = "NAV 76-05.03"

val reisestotteskjemaer = listOf(reiseDaglig, reiseSamling, reiseOppstartSlutt, reiseArbeid,
	ungdomsprogram_reiseDaglig, ungdomsprogram_reiseSamling, ungdomsprogram_reiseOppstartSlutt)
