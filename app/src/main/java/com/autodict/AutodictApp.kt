package com.autodict

import android.app.Application

/**
 * Application-klasse for Autodict.
 *
 * Hald denne tynn. Manuell DI / oppsett av repositories kan koplast til her etter kvart
 * (sjå plan: data/-laget). Per M0 gjer den ingenting utover å eksistere som [Application].
 */
class AutodictApp : Application()
