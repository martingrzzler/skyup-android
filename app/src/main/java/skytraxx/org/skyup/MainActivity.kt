package skytraxx.org.skyup

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                val error = viewModel.error.observeAsState()
                val progress = viewModel.progress.observeAsState()
                val loading = viewModel.loading.observeAsState()
                val hasAllFileAccess = viewModel.hasAllFileAccess.observeAsState()
                val done =
                    progress.value!!.essentialsDownload == 1f && progress.value!!.essentialsInstall == 1f
                            && progress.value!!.systemDownload == 1f && progress.value!!.systemInstall == 1f
                if (!hasAllFileAccess.value!!) {
                    AlertDialog(
                        icon = { Icon(Icons.Rounded.Info, "Info") },
                        title = { Text(getString(R.string.filePermissionsTitle)) },
                        text = { Text(getString(R.string.filePermissionsDesc)) },
                        onDismissRequest = {
                        },
                        confirmButton = {
                            Button(onClick = {
                                val uri = Uri.parse("package:$packageName")
                                val intent =
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        uri
                                    )
                                startActivity(intent)
                            }) {
                                Text(getString(R.string.filePermissionsBtn))
                            }
                        })
                }
               error.value?.let {
                    when (it) {
                        is SKYTRAXXNotFound -> {
                            AlertDialog(
                                icon = { Icon(Icons.Rounded.Info, "Info") },
                                title = { Text(getString(R.string.skytraxxNotFound)) },
                                text = { Text(getString(R.string.homeNote)) },
                                onDismissRequest = {
                                    viewModel.clearState()
                                },
                                confirmButton = {})
                        }
                        is WrongDevice -> {
                            AlertDialog(
                                icon = { Icon(Icons.Rounded.Info, "Info") },
                                title = { Text(getString(R.string.wrongDevice)) },
                                onDismissRequest = {
                                    viewModel.clearState()
                                },
                                confirmButton = {})
                        }
                        else -> {
                            AlertDialog(
                                icon = { Icon(Icons.Rounded.Warning, "Warning") },
                                textContentColor = colorResource(R.color.warning),
                                iconContentColor = colorResource(R.color.warning),
                                title = { Text(getString(R.string.genericErrMsg)) },
                                text = { Text(it?.message ?: "") },
                                onDismissRequest = {
                                    viewModel.clearState()
                                },
                                confirmButton = {})
                        }
                    }

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
                    if (loading.value == false && !done) {
                        Text(text = getString(R.string.homeNote))
                    }
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
                            modifier = Modifier.padding(bottom = 25.dp)
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
                            FilledTonalButton(
                                contentPadding = PaddingValues(
                                    horizontal = 50.dp
                                ),
                                enabled = !loading.value!!,
                                onClick = {
                                    viewModel.setLoading(true)
                                    var moundpoint: File? = null
                                    try {
                                        moundpoint = getSkytraxxMountpoint().getOrThrow()
                                    } catch (e: MustReloadException) {
                                        triggerRestart()
                                    } catch (e: Exception) {
                                        viewModel.setError(e)
                                        return@FilledTonalButton
                                    }
                                    viewModel.updateSkytraxx(moundpoint!!)
                                }) {
                                Text(text = "SKYTRAXX " + getString(R.string.update))
                            }
                        }
                    } else {
                        Text(getString(R.string.successMsg))
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.setHasAllFileAccess(Environment.isExternalStorageManager())


        if (Environment.isExternalStorageManager()) {
            val mountpoint = getSkytraxxMountpoint()
            mountpoint.onFailure {
                if (it is MustReloadException) {
                    triggerRestart()
                }
            }
        }


    }

    private fun triggerRestart() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
        Runtime.getRuntime().exit(0)
    }

    private fun getSkytraxxMountpoint(): Result<File> {
        val storageManager = getSystemService(Context.STORAGE_SERVICE) as StorageManager
        val storageVolumes = storageManager.storageVolumes
        val skytraxx = storageVolumes.find {
            it.getDescription(this) == "SKYTRAXX"
        }

        if (!Environment.isExternalStorageManager()) {
            return Result.failure(Exception("no storage permissions"))
        }

        if (skytraxx == null) {
            storageManager.
            return Result.failure(SKYTRAXXNotFound())
        }

        if (skytraxx.directory == null) {
            return Result.failure(Exception("SKYTRAXX directory not found"))
        }
        if (skytraxx.directory?.isDirectory == false && Environment.isExternalStorageManager()) {
            return Result.failure(MustReloadException())
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



