package com.example

import com.example.data.local.PhotoCategory
import com.example.data.local.PhotoEntity
import com.example.data.local.ScreenshotSubType
import com.example.domain.rules.NaturalLanguageRuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun naturalLanguageRule_parsesAgeAndExclusionsAndProtectsReceipts() {
        val prompt =
            "Screenshots older than 90 days can be suggested for deletion unless they contain receipts, passwords, addresses, important conversations, or confirmation numbers."
        val rule = NaturalLanguageRuleEngine.parseNaturalLanguageToRule(prompt)

        assertEquals(90, rule.minAgeDays)
        assertTrue(rule.targetCategoriesCsv.contains("SCREENSHOT"))
        assertTrue(rule.excludedKeywordsCsv.contains("receipt"))
        assertTrue(rule.excludedKeywordsCsv.contains("password"))
        assertTrue(rule.excludedKeywordsCsv.contains("confirmation"))

        val now = System.currentTimeMillis()
        val dayMs = 1000L * 60 * 60 * 24

        val oldOtpScreenshot = PhotoEntity(
            id = 1,
            uriString = "file:///tmp/otp.png",
            title = "Screenshot_OTP.png",
            folderName = "Screenshots",
            dateTakenEpochMs = now - 180 * dayMs,
            fileSizeBytes = 2_000_000L,
            width = 1080,
            height = 2400,
            dHash = "8f8f0f0f8f8f0f0f",
            pHash = "8f8f0f0f8f8f0f0f",
            sharpnessScore = 80,
            laplacianVariance = 120f,
            exposureScore = 85,
            framingScore = 75,
            overallQualityScore = 80,
            category = PhotoCategory.SCREENSHOT.name,
            screenshotSubType = ScreenshotSubType.TEMPORARY_OTP.name,
            ocrText = "Temporary login code 491022",
            junkConfidence = 0.95f
        )

        val oldFlightConfirmation = oldOtpScreenshot.copy(
            id = 2,
            title = "Screenshot_Flight_Confirmation.png",
            screenshotSubType = ScreenshotSubType.CONFIRMATION_NUMBER.name,
            ocrText = "Booking Confirmation Number #DL-7749K"
        )

        val preview = NaturalLanguageRuleEngine.evaluateRule(
            rule = rule,
            allPhotos = listOf(oldOtpScreenshot, oldFlightConfirmation)
        )

        assertEquals(1, preview.matchedForCleanup.size)
        assertEquals(1L, preview.matchedForCleanup.first().id)
        assertEquals(1, preview.protectedByExclusions.size)
        assertEquals(2L, preview.protectedByExclusions.first().first.id)
    }

    @Test
    fun semanticSearch_matchesNaturalLanguageQueries() {
        val dogPhoto = PhotoEntity(
            id = 10,
            uriString = "file:///tmp/dog.jpg",
            title = "IMG_DOG_PARK.jpg",
            folderName = "Camera",
            dateTakenEpochMs = System.currentTimeMillis(),
            fileSizeBytes = 4_000_000L,
            width = 4000,
            height = 3000,
            dHash = "1122334455667788",
            pHash = "1122334455667788",
            sharpnessScore = 94,
            laplacianVariance = 240f,
            exposureScore = 90,
            framingScore = 88,
            overallQualityScore = 92,
            category = PhotoCategory.PET.name,
            aiDescription = "Golden retriever dog playing outside on sunlit grass",
            semanticTags = "dog,outside,park,pet"
        )

        val results = NaturalLanguageRuleEngine.semanticSearch(
            query = "pictures of my dog outside",
            allPhotos = listOf(dogPhoto)
        )
        assertEquals(1, results.size)
        assertEquals(10L, results.first().id)
    }
}
