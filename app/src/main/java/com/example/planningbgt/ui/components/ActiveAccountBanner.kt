package com.example.planningbgt.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SupervisorAccount
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.planningbgt.R
import com.example.planningbgt.model.ActiveAccount
import com.example.planningbgt.ui.theme.PrimaryYellow
import com.example.planningbgt.ui.theme.TextPrimary

/** Aviso fijo mientras se gestiona otra cuenta, con salida a la propia. */
@Composable
fun ActiveAccountBanner(account: ActiveAccount, onReturn: () -> Unit) {
    Surface(
        color = PrimaryYellow,
        contentColor = TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_account_banner")
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.SupervisorAccount,
                contentDescription = null, // decorativo: el texto ya lo dice
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.accounts_banner_managing, account.name),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .weight(1f)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            )
            TextButton(
                onClick = onReturn,
                modifier = Modifier.testTag("return_to_own_button"),
                colors = ButtonDefaults.textButtonColors(contentColor = TextPrimary)
            ) {
                Text(stringResource(R.string.accounts_banner_return), fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Reemplaza una pantalla que no se puede usar en nombre de otra cuenta. */
@Composable
fun NotAvailableWhileManaging(onReturn: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.accounts_social_unavailable_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.accounts_social_unavailable_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(
                onClick = onReturn,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Text(stringResource(R.string.accounts_banner_return), fontWeight = FontWeight.Bold)
            }
        }
    }
}
