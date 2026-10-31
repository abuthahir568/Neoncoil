package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val NeonCoilBrightColorScheme = lightColorScheme(
    primary = LeafMid,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD7F9CA),
    onPrimaryContainer = CartoonOutline,
    secondary = SunnyOrange,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFECC2),
    onSecondaryContainer = CartoonOutline,
    tertiary = HeartRedBright,
    onTertiary = Color.White,
    background = SkyMidBlue,
    onBackground = CartoonOutline,
    surface = StoneLight,
    onSurface = Color.White,
    surfaceVariant = StoneMid,
    onSurfaceVariant = CartoonOutline,
    outline = StoneBorder,
    error = HeartRedDeep,
    onError = Color.White
)

private val CartoonShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun NeonCoilTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = NeonCoilBrightColorScheme,
        typography = Typography,
        shapes = CartoonShapes,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    NeonCoilTheme(content = content)
}
