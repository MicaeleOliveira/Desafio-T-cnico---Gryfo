package com.project.test

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.Retrofit
import retrofit2.Callback
import retrofit2.Response
import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.gson.annotations.SerializedName
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val cODE = 100
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var viewFinder: PreviewView
    private lateinit var imageCapture: ImageCapture

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        viewFinder = findViewById(R.id.previewView)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA),
                cODE
            )
        } else {
            // Permissão da câmera já concedida
            iniciarCamera()
        }

        findViewById<Button>(R.id.captureButton).setOnClickListener {
            tirarFoto()
        }
    }

    private fun iniciarCamera() {
        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewFinder.surfaceProvider)
                }

            imageCapture = ImageCapture.Builder()
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

                Toast.makeText(this, "Câmera iniciada.", Toast.LENGTH_SHORT).show()

            } catch (exc: Exception) {
                Toast.makeText(this, "Erro ao iniciar a câmera.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun tirarFoto() {
        val imageCapture = imageCapture

        val photoFile1 = File(
            getOutputDirectory(),
            SimpleDateFormat(FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + "1.jpg")

        val photoFile2 = File(
            getOutputDirectory(),
            SimpleDateFormat(FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + "2.jpg")

        val outputOptions1 = ImageCapture.OutputFileOptions.Builder(photoFile1).build()
        val outputOptions2 = ImageCapture.OutputFileOptions.Builder(photoFile2).build()

        imageCapture.takePicture(
            outputOptions1, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Toast.makeText(applicationContext, "Erro ao salvar a primeira imagem", Toast.LENGTH_SHORT).show()
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    imageCapture.takePicture(
                        outputOptions2, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
                            override fun onError(exc: ImageCaptureException) {
                                Toast.makeText(applicationContext, "Erro ao salvar a segunda imagem", Toast.LENGTH_SHORT).show()
                            }

                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                enviarFotos(photoFile1, photoFile2)
                            }
                        })
                }
            })
    }

    private fun enviarFotos(photoFile1: File, photoFile2: File) {
        val fotoBase641 = encodeImageToBase64(photoFile1)
        val fotoBase642 = encodeImageToBase64(photoFile2)

        val request = FotoRequest(
            documentImg = fotoBase641,
            faceImg = fotoBase642,
            externalId = "optional_external_id_here",
            enableLiveness = true, // or false based on your requirement
            livenessThreshold = 0.7f // or any other threshold value between 0 and 1
        )

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.gryfo.com.br/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiClient = retrofit.create(ApiClient::class.java)

        apiClient.enviarFotos(request).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                if (response.isSuccessful) {
                    val apiResponse = response.body()
                    if (apiResponse != null && apiResponse.success) {
                        runOnUiThread {
                            Toast.makeText(applicationContext, "Fotos enviadas com sucesso", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        runOnUiThread {
                            Toast.makeText(applicationContext, "Erro ao enviar as fotos: ${apiResponse?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    runOnUiThread {
                        Toast.makeText(applicationContext, "Erro ao enviar as fotos", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                runOnUiThread {
                    Toast.makeText(applicationContext, "Erro ao enviar as fotos: ${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun encodeImageToBase64(photoFile: File): String {
        val bytes = photoFile.readBytes()
        return Base64.encodeToString(bytes, Base64.DEFAULT)
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, Environment.DIRECTORY_PICTURES).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == cODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                iniciarCamera()
            } else {
                Toast.makeText(this, "Permissão da câmera negada.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}

data class ApiResponse(
    val success: Boolean,
    val message: String
)

interface ApiClient {

    @POST("face_match")
    fun enviarFotos(@Body request: FotoRequest): Call<ApiResponse>
}

data class FotoRequest(
    @SerializedName("document_img") val documentImg: String,
    @SerializedName("face_img") val faceImg: String,
    @SerializedName("external_id") val externalId: String? = null,
    @SerializedName("enable_liveness") val enableLiveness: Boolean = false,
    @SerializedName("liveness_threshold") val livenessThreshold: Float = 0.5f
)