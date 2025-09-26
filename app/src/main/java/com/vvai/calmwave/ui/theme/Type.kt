package com.vvai.calmwave.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vvai.calmwave.R

// Font families
val BalooFamily = FontFamily(Font(R.font.baloo2regular))
val FredokaFamily = FontFamily(Font(R.font.fredokaoneregular))

// Typography configuration: Baloo for regular body text, Fredoka for titles
val Typography = Typography(
    titleLarge = TextStyle(
        fontFamily = FredokaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp
    ),
    
    titleMedium = TextStyle(
        fontFamily = FredokaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FredokaFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = BalooFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = BalooFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    bodySmall = TextStyle(
        fontFamily = BalooFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp
    ),
    labelSmall = TextStyle(
        fontFamily = BalooFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp
    )
)

// Custom style alias. Use MaterialTheme.typography.titleTitle for extra-large page titles.
val androidx.compose.material3.Typography.titleTitle: TextStyle
    get() = TextStyle(
        fontFamily = FredokaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp
    )