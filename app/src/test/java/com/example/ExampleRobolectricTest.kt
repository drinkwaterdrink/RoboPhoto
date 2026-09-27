package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.data.security.EncryptedMediaCache
import com.example.domain.ai.AiAdapterConfig
import com.example.domain.ai.AiProviderType
import com.example.domain.ai.ByokAiAdapterLayer
import com.example.domain.ai.ByokConnectionProfile
import com.example.domain.scanner.MediaScannerEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun appNameAndByokProfilesAndScannerForensicsWork() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("RoboPhoto", appName)

        // Verify AES-256-GCM EncryptedMediaCache round-trip
        val cache = EncryptedMediaCache(context)
        cache.put("test_dhash_key", "verified_encrypted_payload")
        assertEquals("verified_encrypted_payload", cache.get("test_dhash_key"))

        // Verify BYOK Connection Profiles & Custom OpenAI-Compatible Adapter persistence
        val adapterLayer = ByokAiAdapterLayer(context, cache)
        val initialCfg = adapterLayer.loadConfig()
        assertEquals("Custom", initialCfg.activeProfileName)

        assertEquals(
            "https://custom-provider.example.com/api/v1/chat/completions",
            adapterLayer.normalizeOpenAiEndpoint("https://custom-provider.example.com/api/v1")
        )
        assertEquals(
            "https://custom-provider.example.com/api/v1/chat/completions",
            adapterLayer.normalizeOpenAiEndpoint("https://custom-provider.example.com/api/v1/chat/completions")
        )

        val renamedCustomProfile = ByokConnectionProfile(
            id = "profile_custom_default",
            profileName = "My Renamed Provider",
            providerType = AiProviderType.CUSTOM_OPENAI,
            endpointUrl = "https://custom-provider.example.com/api/v1",
            modelId = "custom-vision-model-v1",
            apiKey = "sk-custom-test-12345"
        )
        adapterLayer.saveConfig(
            AiAdapterConfig(
                activeProfileId = renamedCustomProfile.id,
                activeProfileName = renamedCustomProfile.profileName,
                profiles = listOf(renamedCustomProfile),
                activeProvider = AiProviderType.CUSTOM_OPENAI,
                selectedModel = renamedCustomProfile.modelId,
                customEndpointUrl = renamedCustomProfile.endpointUrl,
                customApiKey = renamedCustomProfile.apiKey
            )
        )
        val loadedCfg = adapterLayer.loadConfig()
        assertEquals("My Renamed Provider", loadedCfg.activeProfileName)
        assertEquals("My Renamed Provider", loadedCfg.profiles.first().profileName)
        assertEquals("custom-vision-model-v1", loadedCfg.selectedModel)
        assertEquals("https://custom-provider.example.com/api/v1", loadedCfg.customEndpointUrl)
        assertEquals("sk-custom-test-12345", adapterLayer.resolveEffectiveApiKey(AiProviderType.CUSTOM_OPENAI))

        // Verify Perceptual Hash & Laplacian Sharpness
        val scanner = MediaScannerEngine(cache)
        val bmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        for (y in 0 until 64) {
            for (x in 0 until 64) {
                bmp.setPixel(x, y, if ((x + y) % 2 == 0) Color.WHITE else Color.BLACK)
            }
        }
        val dHash = scanner.computeDHash(bmp)
        val pHash = scanner.computePHash(bmp)
        assertNotNull(dHash)
        assertEquals(16, dHash.length)
        assertEquals(0, scanner.computeHammingDistance(dHash, dHash))
        assertEquals(16, pHash.length)

        val (_, sharpness) = scanner.computeLaplacianSharpness(bmp)
        assertTrue(sharpness > 50)

        // Verify Video PhotoEntity formatting & NaturalLanguageRuleEngine video rules
        val videoEntity = com.example.data.local.PhotoEntity(
            uriString = "content://media/external/video/media/42",
            title = "Screen_Recording_Clip.mp4",
            folderName = "Movies",
            mimeType = "video/mp4",
            durationMs = 125_000L,
            fileSizeBytes = 28_500_000L,
            width = 1920,
            height = 1080,
            dateTakenEpochMs = System.currentTimeMillis(),
            dHash = dHash,
            pHash = pHash,
            laplacianVariance = 420.0f,
            sharpnessScore = 65,
            exposureScore = 78,
            framingScore = 80,
            overallQualityScore = 72,
            category = com.example.data.local.PhotoCategory.VIDEO.name,
            screenshotSubType = com.example.data.local.ScreenshotSubType.NONE.name,
            ocrText = "",
            aiDescription = "Video clip (2:05) • 1920x1080",
            semanticTags = "video,clip",
            junkConfidence = 0.62f,
            junkReason = "Large video clip"
        )
        assertTrue(videoEntity.isVideo)
        assertEquals("2:05", videoEntity.formattedDuration)

        val videoRule = com.example.domain.rules.NaturalLanguageRuleEngine.parseNaturalLanguageToRule(
            "Videos older than 30 days can be suggested for deletion"
        )
        assertTrue(videoRule.targetCategoriesCsv.contains("VIDEO"))

        // Verify NanoGPT /models endpoint resolution & Vision-Only model filtering
        assertEquals(
            "https://nano-gpt.com/api/v1/models?detailed=true",
            adapterLayer.resolveModelsEndpointUrl("https://nano-gpt.com/api/v1/chat/completions")
        )
        assertEquals(
            "https://nano-gpt.com/api/v1/models?detailed=true",
            adapterLayer.resolveModelsEndpointUrl("https://nano-gpt.com")
        )
        assertEquals(
            "https://openrouter.ai/api/v1/models",
            adapterLayer.resolveModelsEndpointUrl("https://openrouter.ai/api/v1/chat/completions")
        )

        val sampleModelsJson = """
            {
              "data": [
                {
                  "id": "gpt-4o-mini",
                  "name": "GPT-4o Mini",
                  "capabilities": { "vision": true }
                },
                {
                  "id": "qwen/qwen2.5-vl-72b-instruct",
                  "name": "Qwen 2.5 VL 72B",
                  "input_modalities": ["text", "image"],
                  "output_modalities": ["text"]
                },
                {
                  "id": "deepseek-r1",
                  "name": "DeepSeek R1",
                  "capabilities": { "vision": false }
                },
                {
                  "id": "flux-pro",
                  "name": "Flux Pro Image Generator",
                  "input_modalities": ["text"],
                  "output_modalities": ["image"]
                }
              ]
            }
        """.trimIndent()

        val parsedModels = adapterLayer.parseModelsJsonAndDetectVision(sampleModelsJson)
        assertEquals(4, parsedModels.size)
        val visionOnly = parsedModels.filter { it.supportsVision }
        assertEquals(2, visionOnly.size)
        assertTrue(visionOnly.any { it.id == "gpt-4o-mini" })
        assertTrue(visionOnly.any { it.id == "qwen/qwen2.5-vl-72b-instruct" })
    }
}
