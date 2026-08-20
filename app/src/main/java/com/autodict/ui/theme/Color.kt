package com.autodict.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Fargepalett for Autodict.
 *
 * Mørkt tema er hovudsaka her: ei taledagbok blir lesen om kvelden, og lange transkript
 * skal vere behagelege å lese. To val styrer det:
 *
 * - **Ingen ekte svart.** Bakgrunnen er ein djup blågrå (#15161C), ikkje #000000. På OLED
 *   gir svart bak lys tekst eit stort luminansprang som får teksten til å "flyte" (halation),
 *   og auget må stille om for kvar sakkade. Ein løfta bakgrunn dempar det.
 * - **Ingen rein kvit tekst.** `onSurface` ligg på ~88 % kvit. Full kvit på mørk botn er
 *   den vanlegaste kjelda til at mørke tema kjennest harde.
 *
 * Aksentane er dempa med vilje: sterkt metta fargar på mørk botn gir kromatisk aberrasjon
 * i randsonene og ser "neon" ut. Lavendel er hovudfargen, med ein varm terrakotta som
 * motvekt så paletten ikkje blir kald.
 */

// --- Mørkt ---

val DarkPrimary = Color(0xFFB9AEF0)
val DarkOnPrimary = Color(0xFF2A2350)
val DarkPrimaryContainer = Color(0xFF403768)
val DarkOnPrimaryContainer = Color(0xFFE3DCFF)

val DarkSecondary = Color(0xFFC6C1D8)
val DarkOnSecondary = Color(0xFF2E2C3B)
val DarkSecondaryContainer = Color(0xFF454252)
val DarkOnSecondaryContainer = Color(0xFFE2DDF0)

val DarkTertiary = Color(0xFFE8B6A4)
val DarkOnTertiary = Color(0xFF46251A)
val DarkTertiaryContainer = Color(0xFF613A2C)
val DarkOnTertiaryContainer = Color(0xFFFFDBCF)

val DarkError = Color(0xFFF2B8B5)
val DarkOnError = Color(0xFF601410)
val DarkErrorContainer = Color(0xFF8C1D18)
val DarkOnErrorContainer = Color(0xFFF9DEDC)

val DarkBackground = Color(0xFF15161C)
val DarkOnBackground = Color(0xFFE1E0E8)
val DarkSurface = Color(0xFF15161C)
val DarkOnSurface = Color(0xFFE1E0E8)
val DarkSurfaceVariant = Color(0xFF454351)
val DarkOnSurfaceVariant = Color(0xFFB4B1C0)

// Overflatenivåa stig i små steg. Store sprang les som harde kantar i mørkt tema.
val DarkSurfaceContainerLowest = Color(0xFF0F1015)
val DarkSurfaceContainerLow = Color(0xFF1A1B22)
val DarkSurfaceContainer = Color(0xFF1E1F27)
val DarkSurfaceContainerHigh = Color(0xFF25262F)
val DarkSurfaceContainerHighest = Color(0xFF2F303A)

val DarkOutline = Color(0xFF6F6D7C)
val DarkOutlineVariant = Color(0xFF3B3A46)
val DarkInverseSurface = Color(0xFFE1E0E8)
val DarkInverseOnSurface = Color(0xFF2F303A)
val DarkInversePrimary = Color(0xFF57509A)

// --- Lyst ---

val LightPrimary = Color(0xFF5B51A0)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE5DEFF)
val LightOnPrimaryContainer = Color(0xFF170F52)

val LightSecondary = Color(0xFF5D5B70)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFE3E0F8)
val LightOnSecondaryContainer = Color(0xFF1A1930)

val LightTertiary = Color(0xFF7A5245)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFFDBCF)
val LightOnTertiaryContainer = Color(0xFF2E150C)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

// Ikkje rein kvit heller – ein anelse varm/lilla botn er mildare i dagslys.
val LightBackground = Color(0xFFFCFAFF)
val LightOnBackground = Color(0xFF1B1B21)
val LightSurface = Color(0xFFFCFAFF)
val LightOnSurface = Color(0xFF1B1B21)
val LightSurfaceVariant = Color(0xFFE5E1F0)
val LightOnSurfaceVariant = Color(0xFF484654)

val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF6F3FC)
val LightSurfaceContainer = Color(0xFFF0EDF7)
val LightSurfaceContainerHigh = Color(0xFFEAE7F2)
val LightSurfaceContainerHighest = Color(0xFFE4E1EC)

val LightOutline = Color(0xFF797785)
val LightOutlineVariant = Color(0xFFCAC7D6)
val LightInverseSurface = Color(0xFF303038)
val LightInverseOnSurface = Color(0xFFF3F0F9)
val LightInversePrimary = Color(0xFFC7BFFF)
