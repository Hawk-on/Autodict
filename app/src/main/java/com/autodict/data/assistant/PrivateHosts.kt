package com.autodict.data.assistant

/**
 * Avgjer om ei adresse ligg på eit privat nett, og difor er forsvarleg å nå over vanleg HTTP.
 *
 * **Kvifor dette ligg i Kotlin og ikkje i network_security_config.xml:** `<domain>`-elementet
 * der tek berre vertsnamn og enkeltadresser – **ikkje** CIDR. Skriv du `192.168.0.0/16` blir
 * det tolka som eit vertsnamn som aldri matchar, så regelen ser rett ut og gjer ingenting.
 * Manifestet opnar difor for klartekst generelt, og den verkelege avgrensinga skjer her, der
 * ho kan uttrykkjast rett og testast.
 */
object PrivateHosts {

    /** Suffiks som berre finst på lokale nett. `.ts.net` er Tailscale sine MagicDNS-namn. */
    private val LOCAL_SUFFIXES = listOf(".local", ".internal", ".home.arpa", ".ts.net")

    private val PRIVATE_RANGES = listOf(
        cidr("10.0.0.0", 8),
        cidr("172.16.0.0", 12),
        cidr("192.168.0.0", 16),
        // Tailscale og anna CGNAT. Trafikken er alt kryptert av WireGuard, så klartekst
        // HTTP inni tunnelen er om noko betre verna enn på eit vanleg LAN.
        cidr("100.64.0.0", 10),
        cidr("127.0.0.0", 8),
        cidr("169.254.0.0", 16),
    )

    fun isPrivate(host: String): Boolean {
        val cleaned = host.trim().lowercase().removeSurrounding("[", "]")
        if (cleaned.isEmpty()) return false

        if (cleaned == "localhost" || cleaned == "::1") return true
        if (LOCAL_SUFFIXES.any { cleaned.endsWith(it) }) return true

        val address = parseIpv4(cleaned) ?: return false
        return PRIVATE_RANGES.any { range -> (address and range.mask) == range.network }
    }
}

private class Ipv4Range(val network: Long, val mask: Long)

private fun cidr(base: String, prefixBits: Int): Ipv4Range {
    val mask = if (prefixBits == 0) 0L else (-1L shl (32 - prefixBits)) and 0xFFFFFFFFL
    val network = requireNotNull(parseIpv4(base)) { "Ugyldig basisadresse: $base" } and mask
    return Ipv4Range(network, mask)
}

/** IPv4 som eit usignert 32-bits tal i ein Long, eller null om det ikkje er ei adresse. */
private fun parseIpv4(host: String): Long? {
    val parts = host.split(".")
    if (parts.size != 4) return null

    var value = 0L
    for (part in parts) {
        val octet = part.toIntOrNull() ?: return null
        if (octet !in 0..255) return null
        // Leiande nullar er ikkje gyldige her – dei blir tolka oktalt andre stader, og
        // "010.0.0.1" skal ikkje kunne snike seg forbi som 8.0.0.1.
        if (part.length > 1 && part[0] == '0') return null
        value = (value shl 8) or octet.toLong()
    }
    return value
}
