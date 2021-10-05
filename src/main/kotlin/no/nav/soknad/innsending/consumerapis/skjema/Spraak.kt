package no.nav.soknad.innsending.consumerapis.skjema

import org.apache.commons.lang3.StringUtils

enum class Spraak(private val enonicKode: String) {
    NB("52"), NN("54"), EN("64"), PL("805380620"), ES("1073745768"), DE("1073745769"), FR("1073745770"), SA("152");

    fun erKode(kode: String): Boolean {
        return enonicKode == kode
    }

    companion object {
        fun fromEnonic(kode: String): Spraak {
            if (StringUtils.isEmpty(kode)) {
                return NB
            }
            for (spraak in values()) {
                if (spraak.erKode(kode)) {
                    return spraak
                }
            }
            return NB
        }
    }
}