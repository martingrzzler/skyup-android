package skytraxx.org.skyup

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
                val errorMessage = viewModel.error.observeAsState()
                val progress = viewModel.progress.observeAsState()
                val loading = viewModel.loading.observeAsState()
                val showCellularWarning = remember {
                    mutableStateOf(false)
                }
                val shownCellularWarning = remember {
                    mutableStateOf(false)
                }
                val done =
                    progress.value!!.essentialsDownload == 1f && progress.value!!.essentialsInstall == 1f
                            && progress.value!!.systemDownload == 1f && progress.value!!.systemInstall == 1f

                if (errorMessage.value != null) {
                    AlertDialog(
                        icon = { Icon(Icons.Rounded.Warning, "Warning") },
                        textContentColor = colorResource(R.color.warning),
                        iconContentColor = colorResource(R.color.warning),
                        title = { Text("Something went wrong...") },
                        text = { Text(errorMessage.value!!) },
                        onDismissRequest = {
                            viewModel.clearState()
                        },
                        confirmButton = {

                        })
                }
                if (showCellularWarning.value) {
                    AlertDialog(
                        icon = { Icon(Icons.Rounded.Warning, "Warning") },
                        title = { Text("Warning") },
                        text = { Text("This will use mobile data and may cost you money.") },
                        onDismissRequest = {
                            showCellularWarning.value = false
                            shownCellularWarning.value = true
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
                    if (loading.value == false && !done) {
                        Text(text = "Make sure to insert your Skytraxx Mini via the USB-C Port on your phone before starting.")
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
                                    if (!Environment.isExternalStorageManager()) {
                                        val intent =
                                            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        startActivity(intent)
                                        return@FilledTonalButton
                                    }
                                    if (!isInternetConnected()) {
                                        viewModel.setError("No internet connection")
                                        return@FilledTonalButton
                                    }
                                    if (!shownCellularWarning.value && isCellularInternetConnection()) {
                                        showCellularWarning.value = true
                                        return@FilledTonalButton
                                    }

                                    viewModel.setLoading(true)
                                    var moundpoint: File? = null
                                    try {
                                        moundpoint =
                                            getSkytraxxMountpoint().getOrThrow()
                                    } catch (e: Exception) {
                                        viewModel.setError(e.message)
                                        return@FilledTonalButton
                                    }
                                    viewModel.updateSkytraxx(moundpoint!!)

                                }) {
                                Text(text = "Update")
                            }
                        }
                    } else {
                        Text("The Update was successful. You can close the application now.")
                    }
                }
            }
        }
    }

    private fun restartApp() {
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
            return Result.failure(Exception("SKYTRAXX not found"))
        }

        if (skytraxx.directory == null ) {
            return Result.failure(Exception("SKYTRAXX directory not found"))
        }

        // first time after permissions are granted - hack
        if (skytraxx.directory?.isDirectory == false && Environment.isExternalStorageManager()) {
            restartApp()
        }

        return Result.success(skytraxx.directory!!)
    }

    private fun isInternetConnected(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }

    private fun isCellularInternetConnection(): Boolean {
        val connectivityManager =
            getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return !activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && activeNetwork.hasTransport(
            NetworkCapabilities.TRANSPORT_CELLULAR
        )
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



