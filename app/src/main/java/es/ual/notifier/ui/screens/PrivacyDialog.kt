package es.ual.notifier.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.ual.notifier.notification.OpenLinkActivity

private const val PRIVACY_URL = "https://github.com/pablomartinez16/NotifAula/blob/main/PRIVACY.md"

@Composable
fun PrivacyInfoDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Información y Privacidad",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Aplicación no oficial",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "NotifAula es un proyecto estudiantil independiente y de código abierto (Licencia MIT). " +
                        "No está afiliada, patrocinada ni respaldada por la Universidad de Almería (UAL) ni por Anthology Inc. (Blackboard).",
                    style = MaterialTheme.typography.bodySmall
                )

                Text(
                    "Política de Privacidad",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "• Cero servidores externos: NotifAula no recopila, envía ni comparte datos personales con ningún servidor propio ni de terceros.\n" +
                        "• Sin acceso a tu contraseña: Te autenticas directamente en la web oficial (https://aulavirtual.ual.es).\n" +
                        "• Almacenamiento cifrado: La cookie de sesión se guarda únicamente en tu teléfono cifrada con AES-256 y se borra al cerrar sesión.\n" +
                        "• Sin anuncios ni rastreadores.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = {
                OpenLinkActivity.openInBrowser(context, PRIVACY_URL)
            }) {
                Text("Ver en GitHub")
            }
        }
    )
}
