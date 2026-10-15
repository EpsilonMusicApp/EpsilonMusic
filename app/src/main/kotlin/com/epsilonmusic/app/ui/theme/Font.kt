package com.epsilonmusic.app.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.epsilonmusic.app.R
import com.epsilonmusic.app.constants.FontChoice
import java.io.File

val bbhBartle = FontFamily(
    Font(R.font.bbh_bartle_regular, FontWeight.Normal)
)

/**
 * Inter — a modern premium grotesque, the closest free equivalent to the
 * Söhne typeface used by ChatGPT/OpenAI. Used for the app title to give
 * it a clean, premium, tech-brand look.
 */
val InterFontFamily = FontFamily(
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold)
)

@OptIn(ExperimentalTextApi::class)
val GoogleSansFontFamily = FontFamily(
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        resId = R.font.google_sans_flex,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    )
)

@OptIn(ExperimentalTextApi::class)
val SansFlexFontFamily = FontFamily(
    Font(
        resId = R.font.sans_flex,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        resId = R.font.sans_flex,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(500),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    ),
    Font(
        resId = R.font.sans_flex,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(700),
            FontVariation.width(100f),
            FontVariation.Setting("ROND", 100f)
        )
    )
)

@OptIn(ExperimentalTextApi::class)
val OutfitFontFamily = FontFamily(
    Font(
        resId = R.font.outfit,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = R.font.outfit,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        resId = R.font.outfit,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

@OptIn(ExperimentalTextApi::class)
val PlusJakartaSansFontFamily = FontFamily(
    Font(
        resId = R.font.plus_jakarta_sans,
        weight = FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400))
    ),
    Font(
        resId = R.font.plus_jakarta_sans,
        weight = FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500))
    ),
    Font(
        resId = R.font.plus_jakarta_sans,
        weight = FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700))
    )
)

fun resolveFontFamily(selectedFont: String, customFontPath: String): FontFamily {
    return when (FontChoice.fromValue(selectedFont)) {
        FontChoice.SYSTEM -> FontFamily.Default
        FontChoice.GOOGLE_SANS -> GoogleSansFontFamily
        FontChoice.SANS_FLEX -> SansFlexFontFamily
        FontChoice.OUTFIT -> OutfitFontFamily
        FontChoice.PLUS_JAKARTA_SANS -> PlusJakartaSansFontFamily
        FontChoice.CUSTOM -> {
            try {
                if (customFontPath.isNotEmpty() && File(customFontPath).exists()) {
                    val typeface = Typeface.createFromFile(customFontPath)
                    FontFamily(androidx.compose.ui.text.font.Typeface(typeface))
                } else {
                    FontFamily.Default
                }
            } catch (e: Exception) {
                FontFamily.Default
            }
        }
    }
}
