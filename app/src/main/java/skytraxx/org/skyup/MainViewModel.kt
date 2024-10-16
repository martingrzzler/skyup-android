package skytraxx.org.skyup

import android.util.AtomicFile
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.sink
import org.kamranzafar.jtar.TarEntry
import org.kamranzafar.jtar.TarInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

const val ESSENTIALS_URL = "https://www.skytraxx.org/skytraxx5mini/skytraxx5mini-essentials.tar"
const val SYSTEM_URL = "https://www.skytraxx.org/skytraxx5mini/skytraxx5mini-system.tar"

class MainViewModel : ViewModel() {
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    private val _hasAllFileAccess = MutableLiveData<Boolean>(false)
    val hasAllFileAccess: LiveData<Boolean> get() = _hasAllFileAccess

    private val _progress = MutableLiveData<Progress>(Progress())
    val progress: LiveData<Progress> get() = _progress

    private val _loading = MutableLiveData<Boolean>(false)
    val loading: LiveData<Boolean> get() = _loading

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
        _error.value = exception.message
        _loading.value = false
    }

    fun setHasAllFileAccess(hasAllFileAccess: Boolean) {
        _hasAllFileAccess.value = hasAllFileAccess
    }

    fun setError(errorMessage: String?) {
        _error.value = errorMessage
    }

    fun setLoading(loading: Boolean) {
        _loading.value = loading
    }

    fun clearState() {
        _error.value = null
        _loading.value = false
        _progress.value = Progress()
    }

    fun updateSkytraxx(moundpoint: File) {
        viewModelScope.launch(coroutineExceptionHandler) {
            val (deviceName, softwareVersion) = getSkytraxxDeviceInfo(
                moundpoint
            ).getOrThrow()
            if (deviceName != "5mini") {
                throw Exception("This device is not a 5mini")
            }

            val essentials = async {
                val essentialsArchive = downloadArchive(ESSENTIALS_URL)
                extractArchive(essentialsArchive, moundpoint, softwareVersion, ESSENTIALS_URL)
            }

            val system = async {
                val systemArchive = downloadArchive(SYSTEM_URL)
                extractArchive(systemArchive, moundpoint, softwareVersion, SYSTEM_URL)
            }

            essentials.await()
            system.await()
            setLoading(false)
        }
    }

    private suspend fun downloadArchive(url: String): ByteArray =
        withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext throw Exception("Unexpected code ${response.code}")
            }
            val body = response.body
                ?: return@withContext throw IOException("Failed to download archive: Empty body")
            val totalBytes = body.contentLength()
            val buffer = ByteArrayOutputStream()
            val byteArray = ByteArray(4096)

            var downloadedBytes = 0L
            var bytesRead: Int

            body.byteStream().use { inputStream ->
                while (inputStream.read(byteArray).also { bytesRead = it } != -1) {
                    buffer.write(byteArray, 0, bytesRead)
                    downloadedBytes += bytesRead
                    withContext(Dispatchers.Main) {
                        if (url == ESSENTIALS_URL) {
                            _progress.value = _progress.value!!.copy(
                                essentialsDownload = downloadedBytes.toFloat() / totalBytes.toFloat()
                            )
                        } else if (url == SYSTEM_URL) {
                            _progress.value = _progress.value!!.copy(
                                systemDownload = downloadedBytes.toFloat() / totalBytes.toFloat()
                            )
                        }
                    }
                }
            }

            return@withContext buffer.toByteArray()
        }

    private suspend fun extractArchive(
        archive: ByteArray,
        moundpoint: File,
        softwareVersion: String,
        url: String
    ) = withContext(Dispatchers.IO) {
        val tis = TarInputStream(archive.inputStream())
        var entry: TarEntry?
        var processedFiles = 0

        // count files in archive
        val totalFiles = TarInputStream(archive.copyOf().inputStream()).use {
            var count = 0
            while (it.nextEntry != null) {
                count++
            }
            count
        }

        while (tis.nextEntry.also { entry = it } != null) {
            val path = entry!!.name
            val deviceFile = AtomicFile(File(moundpoint, "/$path"))
            processedFiles++

            withContext(Dispatchers.Main) {
                if (url == ESSENTIALS_URL) {
                    _progress.value = _progress.value!!.copy(
                        essentialsInstall = processedFiles.toFloat() / totalFiles.toFloat(),
                        essentialsCurrentFile = path
                    )
                } else if (url == SYSTEM_URL) {
                    _progress.value = _progress.value!!.copy(
                        systemInstall = processedFiles.toFloat() / totalFiles.toFloat(),
                        systemCurrentFile = path
                    )
                }
            }

            if (entry!!.isDirectory) {
                deviceFile.baseFile.mkdirs()
                continue
            }

            println("Handling file: ${entry!!.name}")

            val entryFileBuffer = tis.readBytes()
            if (entry!!.name.endsWith(".oab") || entry!!.name.endsWith(".owb") ||
                entry!!.name.endsWith(".otb") || entry!!.name.endsWith(".oob")
            ) {
                if (deviceFile.baseFile.exists() && deviceFile.baseFile.length() >= 12) {
                    val deviceBuffer = ByteArray(12)
                    deviceFile.baseFile.inputStream().use { it ->
                        it.read(deviceBuffer, 0, 12)
                    }

                    if (deviceBuffer.contentEquals(entryFileBuffer.sliceArray(0..11))) {
                        continue
                    }
                }
            } else if (entry!!.name.endsWith(".xlb")) {
                File(moundpoint, "/update").mkdirs()
                val newSoftwareVersion = entryFileBuffer.sliceArray(24..35).toString(Charsets.UTF_8)
                val newDeviceBuiltNum = newSoftwareVersion.toLong()
                val deviceBuiltNum = softwareVersion.toLong()

                if (newDeviceBuiltNum <= deviceBuiltNum) {
                    continue
                }
            }
            val fos = deviceFile.startWrite()
            fos.write(entryFileBuffer)
            deviceFile.finishWrite(fos)
        }
    }

    private suspend fun getSkytraxxDeviceInfo(mountpoint: File): Result<DeviceInfo> =
        withContext(Dispatchers.IO) {
            val sysFile = File(mountpoint, "/.sys/hwsw.info")
            if (!sysFile.exists()) {
                return@withContext Result.failure(Exception(".sys/hwsw.info not found"))
            }

            val reader = sysFile.bufferedReader()
            val dict = parseLines(reader.readText())
            reader.close()

            val name = dict["hw"]
            val softwareVersion = dict["sw"]?.replace("build-", "")

            if (name == null || softwareVersion == null) {
                return@withContext Result.failure(Exception("hw or sw not found in .sys/hwsw.info"))
            }


            return@withContext Result.success(DeviceInfo(name, softwareVersion))
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

data class Progress(
    val systemDownload: Float = 0f,
    val systemCurrentFile: String = "",
    val systemInstall: Float = 0f,
    val essentialsDownload: Float = 0f,
    val essentialsCurrentFile: String = "",
    val essentialsInstall: Float = 0f,
)

