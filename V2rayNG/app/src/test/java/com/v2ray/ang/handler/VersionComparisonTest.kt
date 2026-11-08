package com.v2ray.ang.handler

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * How the in-app updater decides that a release is newer than the installed build.
 *
 * The comparison used to call `toInt()` on each dot-separated segment. A version carrying a
 * suffix, such as the fork's own `0.9.0rc`, therefore threw `NumberFormatException` and every
 * update check failed instead of reporting "no update". These cases pin the suffix handling so a
 * pre-release version stays comparable with the tags it will be matched against.
 */
class VersionComparisonTest {

    private fun newer(candidate: String, installed: String) =
        UpdateCheckerManager.compareVersions(candidate, installed)

    @Test
    fun aNumericVersionAboveTheInstalledOneIsAnUpdate() {
        assertTrue(newer("0.9.1", "0.9.0") > 0)
        assertTrue(newer("2.3.15", "2.3.14") > 0)
    }

    @Test
    fun theInstalledNumericVersionIsNotAnUpdate() {
        assertEquals(0, newer("0.9.0", "0.9.0"))
        assertTrue(newer("0.9.0", "0.9.1") < 0)
    }

    /** The regression: a suffixed version must not throw and must still compare. */
    @Test
    fun aSuffixedVersionIsComparableWithItsNumericNeighbours() {
        assertTrue(newer("0.9.0rc", "0.8.9") > 0)
        assertTrue(newer("0.9.1", "0.9.0rc") > 0)
        assertEquals(0, newer("0.9.0rc", "0.9.0rc"))
    }

    /** A plain release outranks its own pre-release, so rc users are offered the final build. */
    @Test
    fun aFinalReleaseOutranksItsOwnPreRelease() {
        assertTrue(newer("0.9.0", "0.9.0rc") > 0)
        assertTrue(newer("0.9.0rc", "0.9.0") < 0)
    }

    @Test
    fun aLaterPreReleaseOutranksAnEarlierOne() {
        assertTrue(newer("0.9.0rc2", "0.9.0rc1") > 0)
        assertTrue(newer("0.9.0-rc.2", "0.9.0-rc.1") > 0)
        assertEquals(0, newer("0.9.0rc1", "0.9.0-rc1"))
    }

    /** Tags may carry a `v` prefix and different segment counts. */
    @Test
    fun tagPrefixesAndMissingSegmentsAreTolerated() {
        assertEquals(0, newer("v0.9.0", "0.9.0"))
        assertEquals(0, newer("0.9", "0.9.0"))
        assertTrue(newer("1.0", "0.9.9") > 0)
    }

    @Test
    fun theSuffixIsParsedOutOfTheNumericPart() {
        val parsed = UpdateCheckerManager.parseVersion("v0.9.0rc1")
        assertEquals(listOf(0, 9, 0), parsed.numbers)
        assertEquals("rc1", parsed.preRelease)
    }
}
