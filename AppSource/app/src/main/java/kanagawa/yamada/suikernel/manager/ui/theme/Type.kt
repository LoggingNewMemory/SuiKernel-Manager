/*
 * SuiKernel Manager
 * Copyright (C) 2026 Kanagawa Yamada
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package kanagawa.yamada.suikernel.manager.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kanagawa.yamada.suikernel.manager.R

val GoogleSansFlex = FontFamily(
    Font(R.font.googlesansflex_24pt_light, FontWeight.Light),
    Font(R.font.googlesansflex_24pt_regular, FontWeight.Normal),
    Font(R.font.googlesansflex_24pt_medium, FontWeight.Medium),
    // Map the 36pt font to Bold for convenience, even though it's Medium weight
    Font(R.font.googlesansflex_36pt_medium, FontWeight.Bold)
)

val HarmonyOS = FontFamily(
    Font(R.font.harmonyos_sans_regular, FontWeight.Normal)
)

val SFCompactRounded = FontFamily(
    Font(R.font.sf_compact_rounded_regular, FontWeight.Normal)
)

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
)
