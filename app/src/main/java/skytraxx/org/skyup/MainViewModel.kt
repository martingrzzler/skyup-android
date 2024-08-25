package skytraxx.org.skyup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

class MainViewModel : ViewModel() {
    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> get() = _error

    private val _progress = MutableLiveData<Float>(0f)
    val progress: LiveData<Float> get() = _progress

    fun setError(errorMessage: String?) {
        _error.value = errorMessage
    }

    fun clearError() {
        _error.value = null
    }

    suspend fun downloadArchive(url: String): Result<ByteArray> {
        return withContext(Dispatchers.IO) {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Unexpected code ${response.code}"))
            }
            val body = response.body
                ?: return@withContext Result.failure(IOException("Failed to download archive: Empty body"))
            val totalBytes = body.contentLength()
            val buffer = ByteArrayOutputStream()
            val byteArray = ByteArray(4096)

            var downloadedBytes = 0L
            var bytesRead: Int

            body.byteStream().use { inputStream ->
                while (inputStream.read(byteArray).also { bytesRead = it } != -1) {
                    buffer.write(byteArray, 0, bytesRead)
                    downloadedBytes += bytesRead
                    println("Progress ${downloadedBytes.toFloat() / totalBytes.toFloat()}")
                    withContext(Dispatchers.Main) {
                        _progress.value = downloadedBytes.toFloat() / totalBytes.toFloat()
                    }
//                    onUpdate(totalBytes, downloadedBytes)
                }
            }

            return@withContext Result.success(buffer.toByteArray())
        }
    }

}

