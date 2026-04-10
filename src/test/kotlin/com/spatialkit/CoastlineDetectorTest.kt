package com.spatialkit

import com.spatialkit.detection.CoastlineDetector
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class CoastlineDetectorTest {

    @Nested
    inner class KnownLandInteriors {

        @Test
        fun `Swedish interior is obviously on land`() {
            assertTrue(CoastlineDetector.isObviouslyOnLand(65.0, 16.0))
        }

        @Test
        fun `Arabian Peninsula deep interior is obviously on land`() {
            assertTrue(CoastlineDetector.isObviouslyOnLand(25.0, 48.0))
        }

        @Test
        fun `Australian outback is obviously on land`() {
            assertTrue(CoastlineDetector.isObviouslyOnLand(-30.0, 135.0))
        }

        @Test
        fun `Amazon basin interior is obviously on land`() {
            assertTrue(CoastlineDetector.isObviouslyOnLand(-15.0, -55.0))
        }

        @Test
        fun `Chinese interior is obviously on land`() {
            assertTrue(CoastlineDetector.isObviouslyOnLand(35.0, 113.0))
        }

        @ParameterizedTest(name = "land box [{index}]: lat={0} lon={1}")
        @CsvSource(
            "66.0, 10.0",   // Norwegian mountains
            "64.0, 25.0",   // Finnish interior
            "40.0, -4.0",   // Iberian Peninsula
            "46.0, 5.0",    // French interior
            "42.0, 22.0",   // Balkans
            "40.0, 35.0",   // Turkey deep interior
            "-28.0, 24.0",  // Southern Africa
            "32.0, -80.0",  // North America East
            "44.0, 128.0",  // Korean Peninsula
        )
        fun `known interior land coordinates are flagged`(lat: Double, lon: Double) {
            assertTrue(CoastlineDetector.isObviouslyOnLand(lat, lon))
        }
    }

    @Nested
    inner class KnownOceans {

        @Test
        fun `North Atlantic is not flagged as land`() {
            assertFalse(CoastlineDetector.isObviouslyOnLand(45.0, -35.0))
        }

        @Test
        fun `South Pacific is not flagged as land`() {
            assertFalse(CoastlineDetector.isObviouslyOnLand(-30.0, -150.0))
        }

        @Test
        fun `Indian Ocean is not flagged as land`() {
            assertFalse(CoastlineDetector.isObviouslyOnLand(-20.0, 70.0))
        }

        @Test
        fun `Arctic Ocean is not flagged as land`() {
            assertFalse(CoastlineDetector.isObviouslyOnLand(85.0, 0.0))
        }

        @Test
        fun `Mediterranean Sea is not flagged as land`() {
            assertFalse(CoastlineDetector.isObviouslyOnLand(38.0, 15.0))
        }

        @ParameterizedTest(name = "ocean [{index}]: lat={0} lon={1}")
        @CsvSource(
            "0.0, -25.0",    // Equatorial Atlantic
            "60.0, -20.0",   // North Atlantic
            "10.0, 65.0",    // Arabian Sea
            "-45.0, 150.0",  // Tasman Sea
            "30.0, 140.0",   // Pacific east of Japan
        )
        fun `open ocean coordinates are not flagged as land`(lat: Double, lon: Double) {
            assertFalse(CoastlineDetector.isObviouslyOnLand(lat, lon))
        }
    }

    @Nested
    inner class CanalCrossings {

        @Test
        fun `Suez Canal latitude is not flagged (narrow exclusion)`() {
            // Suez Canal area - not in any bounding box by design
            assertFalse(CoastlineDetector.isObviouslyOnLand(30.5, 32.4))
        }

        @Test
        fun `Panama Canal is not falsely flagged`() {
            // Panama interior box (7–10 lat, -82 to -77 lon) is explicit land - correct
            assertTrue(CoastlineDetector.isObviouslyOnLand(8.5, -80.0))
        }
    }

    @Nested
    inner class BoundingBoxCoverage {

        @Test
        fun `44 bounding boxes are defined`() {
            assertEquals(44, CoastlineDetector.majorLandBoxes.size)
        }

        @Test
        fun `all bounding boxes have valid lat-lon ranges`() {
            for (box in CoastlineDetector.majorLandBoxes) {
                assertTrue(box.minLat < box.maxLat) { "${box.region}: minLat must be < maxLat" }
                assertTrue(box.minLon < box.maxLon) { "${box.region}: minLon must be < maxLon" }
                assertTrue(box.minLat >= -90.0 && box.maxLat <= 90.0) { "${box.region}: latitude out of range" }
                assertTrue(box.minLon >= -180.0 && box.maxLon <= 180.0) { "${box.region}: longitude out of range" }
            }
        }

        @Test
        fun `all bounding boxes have a non-blank region label`() {
            for (box in CoastlineDetector.majorLandBoxes) {
                assertTrue(box.region.isNotBlank()) { "Found bounding box with blank region label" }
            }
        }
    }
}
