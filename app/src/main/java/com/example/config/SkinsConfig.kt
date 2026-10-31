package com.example.config

import androidx.compose.ui.graphics.Color

enum class PatternType {
    RAINBOW_BANDS,
    STRIPED,
    SOLID,
    PEARL,
    GRADIENT
}

data class SnakeSkin(
    val id: String,
    val name: String,
    val priceCoins: Int,
    val pattern: PatternType,
    val headColor: Color,
    val colors: List<Color>
)

object SkinsConfig {
    val allSkins: List<SnakeSkin> = listOf(
        // 1. Hero Rainbow Striped Snake (Yellow head + cyan, green, yellow, purple, pink stripes)
        SnakeSkin(
            id = "rainbow_coil",
            name = "Rainbow Coil",
            priceCoins = 0,
            pattern = PatternType.RAINBOW_BANDS,
            headColor = Color(0xFFFFDE38),
            colors = listOf(
                Color(0xFF2FE6DE),
                Color(0xFF56E84D),
                Color(0xFFFFDE38),
                Color(0xFFA87BFF),
                Color(0xFFFF7AD9)
            )
        ),
        // 2. Pearl White
        SnakeSkin(
            id = "pearl_white",
            name = "Pearl Cloud",
            priceCoins = 60,
            pattern = PatternType.PEARL,
            headColor = Color(0xFFFFFFFF),
            colors = listOf(Color(0xFFFFFFFF), Color(0xFFE3F4FF), Color(0xFFFFEEF8))
        ),
        // 3. Tangerine Stripes
        SnakeSkin(
            id = "sunny_tiger",
            name = "Tangerine Pop",
            priceCoins = 80,
            pattern = PatternType.STRIPED,
            headColor = Color(0xFFFF8A3D),
            colors = listOf(Color(0xFFFF8A3D), Color(0xFFFFBD73), Color(0xFFFFF0D6))
        ),
        // 4. Minty Emerald Stripes
        SnakeSkin(
            id = "mint_clover",
            name = "Minty Clover",
            priceCoins = 90,
            pattern = PatternType.STRIPED,
            headColor = Color(0xFF22C984),
            colors = listOf(Color(0xFF22C984), Color(0xFF6EF2B6), Color(0xFFD6FFF0))
        ),
        // 5. Berry Grape Stripes
        SnakeSkin(
            id = "grape_swirl",
            name = "Grape Jelly",
            priceCoins = 100,
            pattern = PatternType.STRIPED,
            headColor = Color(0xFF8C5EFF),
            colors = listOf(Color(0xFF8C5EFF), Color(0xFFC2A6FF), Color(0xFFF1E8FF))
        ),
        // 6. Solid Bubblegum Pink
        SnakeSkin(
            id = "bubblegum_pink",
            name = "Bubblegum",
            priceCoins = 110,
            pattern = PatternType.SOLID,
            headColor = Color(0xFFFF66A3),
            colors = listOf(Color(0xFFFF66A3), Color(0xFFFF85B8))
        ),
        // 7. Solid Sky Splash
        SnakeSkin(
            id = "sky_splash",
            name = "Sky Splash",
            priceCoins = 110,
            pattern = PatternType.SOLID,
            headColor = Color(0xFF33B8FF),
            colors = listOf(Color(0xFF33B8FF), Color(0xFF6BD0FF))
        ),
        // 8. Solid Lemon Drop
        SnakeSkin(
            id = "lemon_drop",
            name = "Lemon Drop",
            priceCoins = 120,
            pattern = PatternType.SOLID,
            headColor = Color(0xFFFFDF38),
            colors = listOf(Color(0xFFFFDF38), Color(0xFFFFEA75))
        ),
        // 9. Cotton Candy Gradient
        SnakeSkin(
            id = "cotton_candy",
            name = "Cotton Candy",
            priceCoins = 160,
            pattern = PatternType.GRADIENT,
            headColor = Color(0xFFFF80BF),
            colors = listOf(Color(0xFFFF80BF), Color(0xFF6EE2FF))
        ),
        // 10. Tropical Sunset Gradient
        SnakeSkin(
            id = "sunset_sherbet",
            name = "Sunset Sherbet",
            priceCoins = 180,
            pattern = PatternType.GRADIENT,
            headColor = Color(0xFFFFCE3B),
            colors = listOf(Color(0xFFFFCE3B), Color(0xFFFF5C77))
        ),
        // 11. Ocean Lagoon Gradient
        SnakeSkin(
            id = "ocean_lagoon",
            name = "Ocean Lagoon",
            priceCoins = 200,
            pattern = PatternType.GRADIENT,
            headColor = Color(0xFF2CE6B8),
            colors = listOf(Color(0xFF2CE6B8), Color(0xFF3D8BFF))
        ),
        // 12. Strawberry Cream Stripes
        SnakeSkin(
            id = "berry_cream",
            name = "Berry Cream",
            priceCoins = 220,
            pattern = PatternType.STRIPED,
            headColor = Color(0xFFFF4D6D),
            colors = listOf(Color(0xFFFF4D6D), Color(0xFFFFF5F7), Color(0xFFFFB3C1))
        ),
        // 13. Purple Teal Cream Stripes
        SnakeSkin(
            id = "carnival_twist",
            name = "Carnival Twist",
            priceCoins = 250,
            pattern = PatternType.STRIPED,
            headColor = Color(0xFF9B5DE5),
            colors = listOf(Color(0xFF9B5DE5), Color(0xFF00BBF9), Color(0xFFFEE440))
        ),
        // 14. Golden Starlight Rainbow
        SnakeSkin(
            id = "party_confetti",
            name = "Party Prism",
            priceCoins = 320,
            pattern = PatternType.RAINBOW_BANDS,
            headColor = Color(0xFFFFBE0B),
            colors = listOf(
                Color(0xFFFFBE0B),
                Color(0xFFFB5607),
                Color(0xFFFF006E),
                Color(0xFF8338EC),
                Color(0xFF3A86FF)
            )
        )
    )

    fun getById(id: String): SnakeSkin {
        val clean = id.trim()
        return allSkins.firstOrNull { it.id == clean } ?: allSkins.first()
    }
}
