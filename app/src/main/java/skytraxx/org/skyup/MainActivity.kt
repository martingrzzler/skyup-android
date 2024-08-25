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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
                val loading = viewModel.loading.observeAsState()
                val done =
                    progress.value!!.essentialsDownload == 1f && progress.value!!.essentialsInstall == 1f
                            && progress.value!!.systemDownload == 1f && progress.value!!.systemInstall == 1f

                if (errorMessage.value != null) {
                    AlertDialog(
                        title = { Text("Oops something went wrong...") },
                        text = { Text(errorMessage.value!!) },
                        onDismissRequest = {
                            viewModel.clearState()
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
                    if (progress.value!!.essentialsDownload != 0f) {
                        ProgressBar(
                            progress = progress.value!!.essentialsDownload,
                            label = "Downloading Essentials...",
                            modifier = Modifier.padding(bottom = 25.dp)
                        )
                    }
                    if (progress.value!!.essentialsInstall != 0f) {
                        ProgressBar(
                            progress = progress.value!!.essentialsInstall,
                            label = progress.value!!.essentialsCurrentFile,
                            modifier =Modifier.padding(bottom = 25.dp)
                        )
                    }
                    if (progress.value!!.systemDownload != 0f) {
                        ProgressBar(
                            progress = progress.value!!.systemDownload,
                            label = "Downloading System files...",
                            modifier = Modifier.padding(bottom = 25.dp)
                        )
                    }
                    if (progress.value!!.systemInstall != 0f) {
                        ProgressBar(
                            progress = progress.value!!.systemInstall,
                            label = progress.value!!.systemCurrentFile,
                            modifier = null
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (!done) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                enabled = !loading.value!!,
                                onClick = {
                                    if (!Environment.isExternalStorageManager()) {
                                        val intent =
                                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        startActivity(intent)
                                    }
                                    viewModel.setLoading(true)
                                    lifecycleScope.launch {
                                        try {
                                            val moundpoint = getSkytraxxMountpoint().getOrThrow()
                                            val (deviceName, softwareVersion) = getSkytraxxDeviceInfo(
                                                moundpoint
                                            ).getOrThrow()
                                            if (deviceName != "5mini") {
                                                throw Exception("This device is not a 5mini")
                                            }

                                            val essentials = async {
                                                val archive =
                                                    viewModel.downloadArchive(ESSENTIALS_URL)
                                                        .getOrThrow()

                                                viewModel.extractArchive(
                                                    archive,
                                                    moundpoint,
                                                    softwareVersion,
                                                    ESSENTIALS_URL
                                                )
                                            }

                                            val system = async {
                                                val archive =
                                                    viewModel.downloadArchive(SYSTEM_URL)
                                                        .getOrThrow()

                                                viewModel.extractArchive(
                                                    archive,
                                                    moundpoint,
                                                    softwareVersion,
                                                    SYSTEM_URL
                                                )
                                            }

                                            listOf(essentials, system).awaitAll()

                                        } catch (e: Exception) {
                                            viewModel.setError(e.message)
                                        } finally {
                                            viewModel.setLoading(false)
                                        }
                                    }
                                }) {
                                Text(text = "Update")
                            }
                        }
                    } else {
                        Text("Done")
                    }
                }
            }
        }
    }

    fun getSkytraxxMountpoint(): Result<File> {
        val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumes = storageManager.storageVolumes
        val skytraxx = storageVolumes.find {
            it.getDescription(this) == "SKYTRAXX"
        }

        if (skytraxx == null) {
            return Result.failure(Exception("SKYTRAXX not found"))
        }

        if (skytraxx.directory == null || skytraxx.directory?.isDirectory == false) {
            return Result.failure(Exception("SKYTRAXX directory not found"))
        }

        return Result.success(skytraxx.directory!!)
    }


}

@Composable
fun ProgressBar(progress: Float, label: String, modifier: Modifier?) {
    Column(modifier = modifier?.fillMaxWidth() ?: Modifier.fillMaxWidth()) {
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



