package com.example.planningbgt.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.example.planningbgt.R
import com.example.planningbgt.ui.components.ActiveAccountBanner
import com.example.planningbgt.ui.components.LocalActiveAccount
import com.example.planningbgt.ui.components.NotAvailableWhileManaging
import com.example.planningbgt.ui.components.rememberActiveAccount
import com.example.planningbgt.ui.theme.PrimaryYellow
import com.example.planningbgt.ui.theme.PrimaryYellowDark
import com.example.planningbgt.ui.theme.TextPrimary
import com.google.firebase.auth.FirebaseAuth

data class BottomNavItem(
    val label: String,
    val icon: ImageVector
)

@Composable
fun MainScreen(
    onLogout: () -> Unit
) {
    val ownerUid = FirebaseAuth.getInstance().currentUser?.uid
    if (ownerUid == null) {
        LaunchedEffect(Unit) { onLogout() } // la sesión se cerró por fuera de la app
        return
    }
    val accountController = rememberActiveAccount(ownerUid)
    val account = accountController.active
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    val navItems = listOf(
        BottomNavItem("Mapa", Icons.Filled.Map),
        BottomNavItem("Eventos", Icons.Filled.Event),
        BottomNavItem("Social", Icons.Filled.People),
        BottomNavItem("Perfil", Icons.Filled.Person)
    )

    Scaffold(
        topBar = {
            if (account != null && account.isDelegated) {
                ActiveAccountBanner(
                    account = account,
                    onReturn = { accountController.switchTo(ownerUid) }
                )
            }
        },
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
              if (account == null) {
                  CircularProgressIndicator(
                      color = PrimaryYellowDark,
                      modifier = Modifier.align(Alignment.Center)
                  )
              } else {
                  // key: al cambiar de cuenta, ninguna pantalla conserva datos de la anterior (HU-45)
                  key(account.uid) {
                      CompositionLocalProvider(LocalActiveAccount provides account) {
                          when (selectedTab) {
                              0 -> MapScreen()
                              1 -> EventsScreen()
                              2 -> if (account.isDelegated) {
                                  NotAvailableWhileManaging(
                                      onReturn = { accountController.switchTo(ownerUid) }
                                  )
                              } else {
                                  SocialScreen()
                              }
                              3 -> ProfileScreen(
                                  accountController = accountController,
                                  onLogout = onLogout
                              )
                          }
                      }
                  }
              }
        }
    }

    if (accountController.accessLost) {
        AlertDialog(
            onDismissRequest = accountController::dismissAccessLost,
            title = { Text(stringResource(R.string.accounts_access_lost_title)) },
            text = { Text(stringResource(R.string.accounts_access_lost_message)) },
            confirmButton = {
                TextButton(
                    onClick = accountController::dismissAccessLost,
                    colors = onSurfaceTextButton()
                ) {
                    Text(stringResource(R.string.common_ok), fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}
