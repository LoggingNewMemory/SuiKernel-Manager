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
