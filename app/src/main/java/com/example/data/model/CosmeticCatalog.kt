package com.example.data.model

import androidx.compose.ui.graphics.Color

enum class CosmeticCategory(val labelFr: String) {
    BIKE("Vélos"),
    HELMET("Casques"),
    TRAIL("Tracés Carte"),
    BADGE("Badges"),
}

enum class CosmeticRarity(val labelFr: String) {
    CLASSIC("Classique"),
    RARE("Sport"),
    EPIC("Élite"),
    LEGENDARY("Légende"),
}

data class CosmeticItem(
    val id: String,
    val name: String,
    val description: String,
    val category: CosmeticCategory,
    val costPoints: Long,
    val primaryColor: Color,
    val accentColor: Color,
    val rarity: CosmeticRarity,
)

object CosmeticCatalog {
    val items: List<CosmeticItem> = listOf(
        // Vélos
        CosmeticItem(
            id = CosmeticDefaults.BIKE_MINT,
            name = "Urbain Menthe",
            description = "Cadre léger aluminium aux reflets menthe fraîche pour les trajets quotidiens.",
            category = CosmeticCategory.BIKE,
            costPoints = 0L,
            primaryColor = Color(0xFF00B86B),
            accentColor = Color(0xFFB9F6CA),
            rarity = CosmeticRarity.CLASSIC,
        ),
        CosmeticItem(
            id = "bike_fixie_volt",
            name = "Fixie Volt",
            description = "Pignon fixe agile peint en jaune-citron haute visibilité.",
            category = CosmeticCategory.BIKE,
            costPoints = 20L,
            primaryColor = Color(0xFFAEEA00),
            accentColor = Color(0xFF1B5E20),
            rarity = CosmeticRarity.RARE,
        ),
        CosmeticItem(
            id = "bike_gravel_copper",
            name = "Gravel Cuivre",
            description = "Monture tout-chemin taillée pour explorer parcs et sentiers.",
            category = CosmeticCategory.BIKE,
            costPoints = 50L,
            primaryColor = Color(0xFFE65100),
            accentColor = Color(0xFFFFCC80),
            rarity = CosmeticRarity.RARE,
        ),
        CosmeticItem(
            id = "bike_carbon_pro",
            name = "Carbone Aéro Pro",
            description = "Cadre profilé noir et cyan conçu pour fendre le vent.",
            category = CosmeticCategory.BIKE,
            costPoints = 100L,
            primaryColor = Color(0xFF00B8D4),
            accentColor = Color(0xFF102027),
            rarity = CosmeticRarity.EPIC,
        ),
        CosmeticItem(
            id = "bike_cyber_plasma",
            name = "Cyber-Vélo Plasma",
            description = "Roues lumineuses néon et géométrie futuriste de grand champion.",
            category = CosmeticCategory.BIKE,
            costPoints = 200L,
            primaryColor = Color(0xFF7C4DFF),
            accentColor = Color(0xFF00E5FF),
            rarity = CosmeticRarity.LEGENDARY,
        ),

        // Casques
        CosmeticItem(
            id = CosmeticDefaults.HELMET_CLASSIC,
            name = "Casque Classique",
            description = "Protection ventilée sobre et confortable.",
            category = CosmeticCategory.HELMET,
            costPoints = 0L,
            primaryColor = Color(0xFF546E7A),
            accentColor = Color(0xFFCFD8DC),
            rarity = CosmeticRarity.CLASSIC,
        ),
        CosmeticItem(
            id = "helmet_sun_visor",
            name = "Visière Soleil",
            description = "Casque estival avec visière ambrée anti-éblouissement.",
            category = CosmeticCategory.HELMET,
            costPoints = 15L,
            primaryColor = Color(0xFFFFB300),
            accentColor = Color(0xFFFFF8E1),
            rarity = CosmeticRarity.RARE,
        ),
        CosmeticItem(
            id = "helmet_polka_climber",
            name = "Grimpeur à Pois",
            description = "Inspiré des maillots mythiques des cols de montagne.",
            category = CosmeticCategory.HELMET,
            costPoints = 40L,
            primaryColor = Color(0xFFE53935),
            accentColor = Color(0xFFFFFFFF),
            rarity = CosmeticRarity.EPIC,
        ),
        CosmeticItem(
            id = "helmet_aero_chrono",
            name = "Aéro Chrono",
            description = "Coque fuselée de contre-la-montre avec teinte cobalt.",
            category = CosmeticCategory.HELMET,
            costPoints = 80L,
            primaryColor = Color(0xFF2962FF),
            accentColor = Color(0xFF82B1FF),
            rarity = CosmeticRarity.EPIC,
        ),
        CosmeticItem(
            id = "helmet_gold_crown",
            name = "Couronne du Bitume",
            description = "Finition or poli réservée aux vétérans de la route.",
            category = CosmeticCategory.HELMET,
            costPoints = 150L,
            primaryColor = Color(0xFFFFD600),
            accentColor = Color(0xFFFF6D00),
            rarity = CosmeticRarity.LEGENDARY,
        ),

        // Tracés OpenStreetMap
        CosmeticItem(
            id = CosmeticDefaults.TRAIL_EMERALD,
            name = "Tracé Émeraude",
            description = "Ruban vert sportif affiché sur OpenStreetMap pendant vos balades.",
            category = CosmeticCategory.TRAIL,
            costPoints = 0L,
            primaryColor = Color(0xFF00C853),
            accentColor = Color(0xFF69F0AE),
            rarity = CosmeticRarity.CLASSIC,
        ),
        CosmeticItem(
            id = "trail_coral",
            name = "Sillage Corail Turbo",
            description = "Trace votre itinéraire en rouge-corail ardent sur la carte.",
            category = CosmeticCategory.TRAIL,
            costPoints = 15L,
            primaryColor = Color(0xFFFF5252),
            accentColor = Color(0xFFFF8A80),
            rarity = CosmeticRarity.RARE,
        ),
        CosmeticItem(
            id = "trail_comet_gold",
            name = "Comète Dorée",
            description = "Un fil d'or lumineux qui suit chacun de vos coups de pédale.",
            category = CosmeticCategory.TRAIL,
            costPoints = 35L,
            primaryColor = Color(0xFFFFAB00),
            accentColor = Color(0xFFFFE57F),
            rarity = CosmeticRarity.RARE,
        ),
        CosmeticItem(
            id = "trail_cyan_laser",
            name = "Onde Cyan Laser",
            description = "Tracé bleu électrique ultra-net sur la carte.",
            category = CosmeticCategory.TRAIL,
            costPoints = 60L,
            primaryColor = Color(0xFF00E5FF),
            accentColor = Color(0xFF84FFFF),
            rarity = CosmeticRarity.EPIC,
        ),
        CosmeticItem(
            id = "trail_nebula",
            name = "Nébuleuse Royale",
            description = "Ruban violet cosmique prestigieux pour vos grandes randonnées.",
            category = CosmeticCategory.TRAIL,
            costPoints = 120L,
            primaryColor = Color(0xFF7C4DFF),
            accentColor = Color(0xFFB388FF),
            rarity = CosmeticRarity.LEGENDARY,
        ),

        // Badges
        CosmeticItem(
            id = CosmeticDefaults.BADGE_FREEWHEEL,
            name = "Roue Libre",
            description = "Emblème officiel de départ pour tout nouveau cycliste.",
            category = CosmeticCategory.BADGE,
            costPoints = 0L,
            primaryColor = Color(0xFF26A69A),
            accentColor = Color(0xFFE0F2F1),
            rarity = CosmeticRarity.CLASSIC,
        ),
        CosmeticItem(
            id = "badge_urban_spark",
            name = "Éclair Urbain",
            description = "Pour ceux qui préfèrent la selle aux embouteillages.",
            category = CosmeticCategory.BADGE,
            costPoints = 30L,
            primaryColor = Color(0xFFFFC107),
            accentColor = Color(0xFF212121),
            rarity = CosmeticRarity.RARE,
        ),
        CosmeticItem(
            id = "badge_king_slope",
            name = "Roi de la Pente",
            description = "Insigne d'honneur des avaleurs de kilomètres.",
            category = CosmeticCategory.BADGE,
            costPoints = 75L,
            primaryColor = Color(0xFFD500F9),
            accentColor = Color(0xFFFFD600),
            rarity = CosmeticRarity.LEGENDARY,
        ),
    )

    fun findById(id: String): CosmeticItem? = items.find { it.id == id }

    fun trailColorFor(trailId: String): Color =
        findById(trailId)?.primaryColor ?: Color(0xFF00C853)
}
