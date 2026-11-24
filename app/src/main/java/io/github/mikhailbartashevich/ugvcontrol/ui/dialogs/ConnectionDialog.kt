package io.github.mikhailbartashevich.ugvcontrol.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import io.github.mikhailbartashevich.ugvcontrol.ui.theme.RoverColors
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mikhailbartashevich.roverkit.protocol.RoverAddress
import io.github.mikhailbartashevich.ugvcontrol.control.RoverViewModel

@Composable
fun ConnectionDialog(vm: RoverViewModel, fill: Boolean, onFillChange: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val rover = vm.rover
    val address by rover.address.collectAsStateWithLifecycle()
    val searching by rover.searching.collectAsStateWithLifecycle()
    val error by rover.lastError.collectAsStateWithLifecycle()
    var text by remember { mutableStateOf(address.toString()) }
    var quality by remember { mutableFloatStateOf(rover.videoQuality.toFloat()) }
    val parsed = RoverAddress.parse(text)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RoverColors.DialogGlass,
        shape = RoundedCornerShape(30.dp),
        title = { Text("Rover connection") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text, onValueChange = { text = it },
                    label = { Text("Rover address") },
                    placeholder = { Text("192.168.50.5 or host:port") },
                    supportingText = { Text(if (parsed == null && text.isNotBlank()) "Not a valid address" else "Port 5000 unless you add :port") },
                    isError = parsed == null && text.isNotBlank(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { parsed?.let { vm.connect(it); onDismiss() } }),
                    modifier = Modifier.semantics { contentDescription = "Rover address, for example 192.168.50.5" },
                )
                error?.let { Text("Last error: $it", fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { parsed?.let { vm.connect(it); onDismiss() } }, enabled = parsed != null,
                        modifier = Modifier.semantics { contentDescription = "Connect to this address and save it" },
                    ) { Text("Connect") }
                    OutlinedButton(
                        onClick = { vm.connectOrDiscover(); onDismiss() }, enabled = !searching,
                        modifier = Modifier.semantics { contentDescription = "Find the rover: saved address, then 192.168.50.5, then scan this network on port ${address.port}" },
                    ) { Text(if (searching) "Searching…" else "Find rover") }
                    TextButton(
                        onClick = { text = RoverAddress.HOTSPOT_HOST },
                        modifier = Modifier.semantics { contentDescription = "Fill in the rover hotspot address 192.168.50.5" },
                    ) { Text("Hotspot") }
                }
                Text("Video quality: ${quality.toInt()}", fontSize = 13.sp)
                Slider(
                    value = quality, onValueChange = { quality = it }, valueRange = 10f..100f,
                    onValueChangeFinished = { vm.setVideoQuality(quality.toInt()) },
                    modifier = Modifier.semantics { contentDescription = "Video JPEG quality. Higher is sharper but needs more Wi‑Fi bandwidth." },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Fill screen (crop video)", fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Switch(fill, onFillChange,
                        modifier = Modifier.semantics { contentDescription = "Fill the screen with video, cropping the edges. Off shows the whole frame." })
                }
                Text(
                    "Tips: the rover's own Wi‑Fi hotspot is at 192.168.50.5. From the Android emulator, a mock rover on the Mac is 10.0.2.2:5055.",
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
