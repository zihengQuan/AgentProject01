package com.zq.topnews

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.zq.topnews.data.MockAuthRepository
import com.zq.topnews.data.SharedPreferencesAuthStore
import com.zq.topnews.ui.TopNewsApp
import com.zq.topnews.ui.theme.TopNewsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val authRepository = MockAuthRepository(
            SharedPreferencesAuthStore(applicationContext),
        )
        setContent {
            TopNewsTheme {
                TopNewsApp(authRepository = authRepository)
            }
        }
    }
}
