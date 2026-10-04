package com.example.planningbgt.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.planningbgt.ui.theme.PrimaryYellow
import com.example.planningbgt.ui.theme.PrimaryYellowDark
import com.example.planningbgt.ui.theme.TextPrimary

data class BottomNavItem(
    val label: String,
    val icon: ImageVector
)

@Composable
fun MainScreen(
    onLogout: () -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val navItems = listOf(
        BottomNavItem("Mapa", Icons.Filled.Map),
        BottomNavItem("Social", Icons.Filled.People),
        BottomNavItem("Perfil", Icons.Filled.Person)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = PrimaryYellow
            ) {
                navItems.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        icon = {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = PrimaryYellowDark,
                            selectedTextColor = PrimaryYellowDark,
                            unselectedIconColor = TextPrimary,
                            unselectedTextColor = TextPrimary,
                            indicatorColor = PrimaryYellow.copy(alpha = 0.3f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (selectedTab) {
                0 -> MapScreen()
                1 -> SocialScreen()
                2 -> ProfileScreen(onLogout = onLogout)
            }
        }
    }
}
