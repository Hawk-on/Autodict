package com.autodict.data.assistant

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Denne regelen kan ikkje uttrykkjast i network_security_config (ingen CIDR-støtte), så han
 * bur i Kotlin – og då skal han vere testa.
 */
class PrivateHostsTest {

    @Test
    fun `vanlege LAN-adresser er private`() {
        assertTrue(PrivateHosts.isPrivate("192.168.1.10"))
        assertTrue(PrivateHosts.isPrivate("10.0.0.5"))
        assertTrue(PrivateHosts.isPrivate("172.16.3.1"))
        assertTrue(PrivateHosts.isPrivate("172.31.255.254"))
    }

    @Test
    fun `tailscale sitt CGNAT-område er privat`() {
        assertTrue(PrivateHosts.isPrivate("100.64.0.1"))
        assertTrue(PrivateHosts.isPrivate("100.101.102.103"))
        assertTrue(PrivateHosts.isPrivate("100.127.255.255"))
        assertTrue(PrivateHosts.isPrivate("jobb-pc.tail1234.ts.net"))
    }

    @Test
    fun `loopback og lokale namn er private`() {
        assertTrue(PrivateHosts.isPrivate("localhost"))
        assertTrue(PrivateHosts.isPrivate("127.0.0.1"))
        assertTrue(PrivateHosts.isPrivate("::1"))
        assertTrue(PrivateHosts.isPrivate("[::1]"))
        assertTrue(PrivateHosts.isPrivate("maskina.local"))
    }

    @Test
    fun `offentlege adresser er ikkje private`() {
        assertFalse(PrivateHosts.isPrivate("api.anthropic.com"))
        assertFalse(PrivateHosts.isPrivate("8.8.8.8"))
        assertFalse(PrivateHosts.isPrivate("1.1.1.1"))
    }

    @Test
    fun `adresser rett utanfor områda er ikkje private`() {
        assertFalse(PrivateHosts.isPrivate("172.15.0.1"))
        assertFalse(PrivateHosts.isPrivate("172.32.0.1"))
        assertFalse(PrivateHosts.isPrivate("192.169.0.1"))
        assertFalse(PrivateHosts.isPrivate("100.63.255.255"))
        assertFalse(PrivateHosts.isPrivate("100.128.0.0"))
    }

    @Test
    fun `oktal-liknande adresser blir ikkje godtekne`() {
        // "010.0.0.1" ville blitt 8.0.0.1 om nokon tolka han oktalt.
        assertFalse(PrivateHosts.isPrivate("010.0.0.1"))
    }

    @Test
    fun `sopal er ikkje private`() {
        assertFalse(PrivateHosts.isPrivate(""))
        assertFalse(PrivateHosts.isPrivate("   "))
        assertFalse(PrivateHosts.isPrivate("192.168.1"))
        assertFalse(PrivateHosts.isPrivate("192.168.1.256"))
        assertFalse(PrivateHosts.isPrivate("ikkje.ei.adresse.i.det.heile"))
    }
}
