package com.example.planningbgt.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.planningbgt.R
import com.example.planningbgt.model.AccountLink
import com.example.planningbgt.ui.theme.PrimaryYellow
import com.example.planningbgt.ui.theme.PrimaryYellowDark
import com.example.planningbgt.ui.theme.TextPrimary
import com.example.planningbgt.util.AccountLinkError
import com.example.planningbgt.util.AccountLinkRules

internal data class AddAccountForm(
    val createNew: Boolean,
    val email: String,
    val role: String,
    val label: String,
    val name: String,
    val password: String
)

/** Asociar una cuenta: invitar a una que ya existe o crear una nueva. */
@Composable
internal fun AddAccountDialog(
    isWorking: Boolean,
    serverError: AccountLinkError?,
    onDismiss: () -> Unit,
    onSubmit: (AddAccountForm) -> Unit
) {
    var createNew by rememberSaveable { mutableStateOf(false) }
    var email by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var role by rememberSaveable { mutableStateOf(AccountLink.ROLE_MANAGE) }
    var name by rememberSaveable { mutableStateOf("") }
    // Sin rememberSaveable a propósito: la contraseña no debe quedar en el estado guardado
    var password by remember { mutableStateOf("") }
    var showErrors by rememberSaveable { mutableStateOf(false) }

    val formError = if (createNew) {
        AccountLinkRules.checkNewAccount(name, email, password, role, label)
    } else {
        AccountLinkRules.checkForm(email, role, label)
    }
    val visibleError = if (showErrors && formError != null) formError else serverError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.accounts_add)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val modes = listOf(
                    false to R.string.accounts_mode_invite,
                    true to R.string.accounts_mode_create
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, (mode, textRes) ->
                        SegmentedButton(
                            selected = createNew == mode,
                            onClick = {
                                createNew = mode
                                showErrors = false
                            },
                            enabled = !isWorking,
                            modifier = Modifier.testTag(if (mode) "mode_create" else "mode_invite"),
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = modes.size
                            ),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = PrimaryYellow,
                                activeContentColor = TextPrimary
                            )
                        ) {
                            Text(stringResource(textRes))
                        }
                    }
                }
                Text(
                    text = stringResource(
                        if (createNew) R.string.accounts_create_hint else R.string.accounts_invite_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (createNew) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.accounts_field_name)) },
                        isError = showErrors && formError == AccountLinkError.NAME_REQUIRED,
                        enabled = !isWorking,
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_name")
                    )
                }
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.accounts_field_email)) },
                    isError = showErrors && formError == AccountLinkError.INVALID_EMAIL,
                    enabled = !isWorking,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("field_email")
                )
                if (createNew) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.accounts_field_password)) },
                        isError = showErrors && formError == AccountLinkError.WEAK_PASSWORD,
                        enabled = !isWorking,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field_password")
                    )
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(AccountLinkRules.LABEL_MAX_LENGTH) },
                    label = { Text(stringResource(R.string.accounts_field_label)) },
                    enabled = !isWorking,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.accounts_field_role),
                    style = MaterialTheme.typography.titleSmall
                )
                Column(modifier = Modifier.selectableGroup()) {
                    RoleOption(
                        role = AccountLink.ROLE_MANAGE,
                        hintRes = R.string.accounts_role_manage_hint,
                        selected = role == AccountLink.ROLE_MANAGE,
                        enabled = !isWorking,
                        onSelect = { role = AccountLink.ROLE_MANAGE }
                    )
                    RoleOption(
                        role = AccountLink.ROLE_ACT,
                        hintRes = R.string.accounts_role_act_hint,
                        selected = role == AccountLink.ROLE_ACT,
                        enabled = !isWorking,
                        onSelect = { role = AccountLink.ROLE_ACT }
                    )
                }

                visibleError?.let {
                    Text(
                        text = accountErrorText(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .semantics { liveRegion = LiveRegionMode.Polite }
                            .testTag("add_account_error")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    showErrors = true
                    if (formError == null) {
                        onSubmit(AddAccountForm(createNew, email, role, label, name, password))
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.testTag("submit_account_button"),
                colors = onSurfaceTextButton()
            ) {
                Text(
                    text = stringResource(
                        when {
                            isWorking -> R.string.accounts_saving
                            createNew -> R.string.accounts_submit_create
                            else -> R.string.accounts_submit_invite
                        }
                    ),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isWorking,
                colors = onSurfaceTextButton()
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

@Composable
private fun RoleOption(
    role: String,
    hintRes: Int,
    selected: Boolean,
    enabled: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect
            )
            .padding(vertical = 8.dp)
            .testTag("role_$role"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null, // el Row maneja el toque (accesibilidad)
            enabled = enabled,
            colors = RadioButtonDefaults.colors(selectedColor = PrimaryYellowDark)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = roleLabel(role),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = stringResource(hintRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
