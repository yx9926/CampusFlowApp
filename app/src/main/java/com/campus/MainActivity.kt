package com.campus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.ViewModelProvider
import com.campus.admin.AdminViewModel
import com.campus.auth.AuthPage
import com.campus.auth.AuthRoute
import com.campus.auth.AuthViewModel
import com.campus.spaces.CampusRoute
import com.campus.spaces.CampusViewModel
import com.campus.ui.theme.CampusFlowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val auth = ViewModelProvider(this)[AuthViewModel::class.java]
        val campus = ViewModelProvider(this)[CampusViewModel::class.java]
        val admin = ViewModelProvider(this)[AdminViewModel::class.java]
        setContent {
            CampusFlowTheme {
                LaunchedEffect(auth.state.page) {
                    if (auth.state.page != AuthPage.SignedIn) {
                        campus.clear()
                        admin.clear()
                    }
                }
                if (auth.state.page == AuthPage.SignedIn && auth.state.account != null) {
                    CampusRoute(auth, campus, admin, lifecycle)
                } else {
                    AuthRoute(auth)
                }
            }
        }
    }
}
