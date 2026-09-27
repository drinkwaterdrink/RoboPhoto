package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.example.data.local.LuminaDatabase
import com.example.data.repository.LuminaRepository
import com.example.data.security.EncryptedMediaCache
import com.example.domain.ai.ByokAiAdapterLayer
import com.example.domain.scanner.MediaScannerEngine
import com.example.ui.LuminaApp
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.LuminaViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContext = applicationContext
        val database = LuminaDatabase.getInstance(appContext)
        val encryptedCache = EncryptedMediaCache(appContext)
        val scannerEngine = MediaScannerEngine(encryptedCache)
        val aiAdapterLayer = ByokAiAdapterLayer(appContext, encryptedCache)
        val repository = LuminaRepository(
            appContext = appContext,
            dao = database.luminaDao(),
            encryptedCache = encryptedCache,
            scannerEngine = scannerEngine,
            aiAdapterLayer = aiAdapterLayer
        )

        val viewModel = ViewModelProvider(
            this,
            LuminaViewModel.provideFactory(repository)
        )[LuminaViewModel::class.java]

        setContent {
            MyApplicationTheme {
                LuminaApp(viewModel = viewModel)
            }
        }
    }
}
