package com.spatialkit.detection

object CoastlineDetector {

    data class BoundingBox(
        val region: String,
        val minLat: Double,
        val maxLat: Double,
        val minLon: Double,
        val maxLon: Double
    )

    val majorLandBoxes: List<BoundingBox> = listOf(
        // Scandinavia interior
        BoundingBox("Norwegian mountains", 62.0, 71.0, 8.0, 14.0),
        BoundingBox("Swedish interior", 62.0, 68.0, 14.0, 18.0),
        BoundingBox("Finnish interior", 63.0, 68.0, 24.0, 28.0),

        // Western Europe
        BoundingBox("British Isles", 50.0, 59.0, -6.0, -1.0),
        BoundingBox("Iberian Peninsula interior", 37.0, 44.0, -8.0, -1.0),
        BoundingBox("French interior", 43.0, 49.0, 0.0, 8.0),
        BoundingBox("Italian Peninsula", 38.0, 46.0, 9.0, 14.0),

        // Eastern Europe / Middle East
        BoundingBox("Balkans interior", 38.0, 45.0, 20.0, 24.0),
        BoundingBox("Turkey deep interior", 38.0, 42.0, 31.0, 40.0),
        BoundingBox("Arabian Peninsula interior", 20.0, 30.0, 40.0, 56.0),

        // Asia
        BoundingBox("Indian subcontinent interior", 10.0, 24.0, 76.0, 80.0),
        BoundingBox("Malay Peninsula interior", 3.0, 13.0, 99.0, 104.0),
        BoundingBox("China deep interior", 25.0, 40.0, 110.0, 116.0),
        BoundingBox("Japan west Honshu/Hokkaido", 34.0, 42.0, 132.0, 138.0),
        BoundingBox("Japan Honshu east", 34.0, 38.0, 136.0, 140.0),
        BoundingBox("Vietnam interior", 10.0, 18.0, 105.0, 108.0),

        // Africa
        BoundingBox("West Africa interior", -5.0, 15.0, -3.0, 8.0),
        BoundingBox("Southern Africa interior", -34.0, -22.0, 20.0, 28.0),

        // Oceania
        BoundingBox("Australia interior", -38.0, -20.0, 120.0, 145.0),

        // Americas
        BoundingBox("North America East interior", 30.0, 50.0, -90.0, -75.0),
        BoundingBox("Gulf/Central America land", 15.0, 30.0, -100.0, -85.0),
        BoundingBox("South America interior", -30.0, 0.0, -60.0, -45.0),

        // Middle East / Africa secondary
        BoundingBox("Sinai Peninsula", 28.0, 30.0, 33.0, 35.0),
        BoundingBox("Horn of Africa", 0.0, 12.0, 42.0, 50.0),

        // East Asia secondary
        BoundingBox("China East coast interior", 27.0, 32.0, 116.0, 120.0),
        BoundingBox("Korean Peninsula interior", 35.0, 42.0, 126.0, 128.0),
        BoundingBox("Manchuria / NE China", 42.0, 48.0, 122.0, 132.0),

        // Americas secondary
        BoundingBox("Florida", 25.0, 31.0, -85.0, -80.0),
        BoundingBox("Baja California", 23.0, 32.0, -117.0, -109.0),

        // Islands / Peninsulas
        BoundingBox("Sumatra", -6.0, 6.0, 97.0, 106.0),
        BoundingBox("New Zealand North Island", -42.0, -34.0, 172.0, 178.0),
        BoundingBox("Madagascar", -26.0, -12.0, 43.0, 50.0),

        // Baltic / Black Sea
        BoundingBox("Gotland / Oland", 56.5, 58.0, 16.5, 18.5),
        BoundingBox("Estonian islands", 58.0, 59.0, 21.5, 23.0),
        BoundingBox("Crimean Peninsula", 44.5, 46.0, 33.0, 36.0),

        // European coastal detail
        BoundingBox("Scottish Highlands", 56.0, 59.0, -6.0, -3.0),
        BoundingBox("Norwegian fjord interior", 58.0, 62.0, 5.0, 8.0),
        BoundingBox("Greek Peloponnese", 36.5, 38.5, 21.5, 23.0),
        BoundingBox("Jutland Peninsula", 55.0, 57.5, 8.0, 10.0),
        BoundingBox("Dalmatian hinterland", 42.0, 45.0, 16.0, 18.0),

        // Asian / Caribbean islands
        BoundingBox("Sri Lanka interior", 6.0, 10.0, 79.5, 81.5),
        BoundingBox("Taiwan interior", 22.5, 25.5, 120.0, 121.5),
        BoundingBox("Cuba interior", 21.0, 23.5, -84.0, -75.0),
        BoundingBox("Panama interior", 7.0, 10.0, -82.0, -77.0)
    )

    fun isObviouslyOnLand(lat: Double, lon: Double): Boolean {
        for (box in majorLandBoxes) {
            if (lat >= box.minLat && lat <= box.maxLat &&
                lon >= box.minLon && lon <= box.maxLon
            ) {
                return true
            }
        }
        return false
    }
}
