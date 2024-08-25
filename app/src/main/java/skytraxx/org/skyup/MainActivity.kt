package skytraxx.org.skyup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import skytraxx.org.skyup.ui.theme.SkytraxxFont
import skytraxx.org.skyup.ui.theme.SkyupTheme
import java.io.File
import kotlin.math.roundToInt


class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkyupTheme {
                val errorMessage = viewModel.error.observeAsState()
                val progress = viewModel.progress.observeAsState()
                if (errorMessage.value != null) {
                    AlertDialog(
                        title = { Text("Oops something went wrong...") },
                        text = { Text(errorMessage.value!!) },
                        onDismissRequest = {
                            viewModel.clearError()
                        },
                        confirmButton = {

                        })
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(30.dp)
                        .systemBarsPadding()
                ) {
                    Text(
                        "SKYTRAXX",
                        fontFamily = SkytraxxFont,
                        fontSize = 24.sp,
                        modifier = Modifier.padding(bottom = 30.dp)
                    )
                    ProgressBar(progress = progress.value?: 0f, label = "someFile.txt", modifier = null)
                    Spacer(modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Button(onClick = {
                            if (!Environment.isExternalStorageManager()) {
                                val intent =
                                    Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                startActivity(intent)
                            }

                            try {
                                if (getSkytraxxDeviceInfo().getOrThrow().name != "5mini") {
                                    throw Exception("This device is not a 5mini")
                                }

                                lifecycleScope.launch {
                                    viewModel.downloadArchive("https://www.skytraxx.org/skytraxx5mini/skytraxx5mini-essentials.tar")
                                }
                            } catch (e:Exception) {
                                viewModel.setError(e.message)
                            }
                        }) {
                            Text(text = "Update")
                        }
                    }
                }
            }
        }
    }

    fun getSkytraxxDeviceInfo(): Result<DeviceInfo> {
        val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumes = storageManager.storageVolumes
        val skytraxx = storageVolumes.find {
            it.getDescription(this) == "SKYTRAXX"
        }

        val sysFile = File(skytraxx?.directory, "/.sys/hwsw.info")
        if (!sysFile.exists()) {
            return Result.failure(Exception(".sys/hwsw.info not found"))
        }

        val reader = sysFile.bufferedReader()
        val dict = parseLines(reader.readText())
        reader.close()

        val name = dict["hw"]
        val softwareVersion = dict["sw"]

        if (name == null || softwareVersion == null) {
            return Result.failure(Exception("hw or sw not found in .sys/hwsw.info"))
        }

        return Result.success(DeviceInfo(name, softwareVersion))
    }
}

@Composable
fun ProgressBar(progress: Float, label: String, modifier: Modifier?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 5.dp),
            horizontalArrangement = Arrangement.Absolute.SpaceBetween

        ) {
            Text(text = label)
            Text(text = "${(progress * 100).roundToInt()}%")
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

fun parseLines(fileContent: String): HashMap<String, String> {
    val dict = HashMap<String, String>()

    fileContent.lines().forEach { line ->
        val parts = line.split("=")
        if (parts.size == 2) {
            val key = parts[0]
            val value = parts[1].replace("\"", "")
            dict[key] = value
        }
    }

    return dict
}

data class DeviceInfo(
    val name: String,
    val softwareVersion: String,
)



