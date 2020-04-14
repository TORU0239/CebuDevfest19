package sg.toru.cebudevfest19.ui.fragment

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sg.toru.cebudevfest19.R
import sg.toru.cebudevfest19.ui.core.ImageClassfier
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraFragment : Fragment() {
    private val TAG = "CameraFragment"

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var displayId: Int = -1
    private var preview:Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera:Camera? = null

    private lateinit var container:ConstraintLayout
    private lateinit var viewFinder:PreviewView

    private val imageClassifier:ImageClassfier by lazy {
        ImageClassfier()
    }

    private val executor:ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    private val displayManager:DisplayManager by lazy {
        requireContext().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }

    private val displayListener = object:DisplayManager.DisplayListener {
        override fun onDisplayChanged(displayId: Int) = view?.let { view ->
            if (displayId == this@CameraFragment.displayId) {
                imageCapture?.targetRotation = view.display.rotation
                imageAnalyzer?.targetRotation = view.display.rotation
            }
        } ?: Unit

        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view as ConstraintLayout
        viewFinder = view.findViewById(R.id.view_finder)
        updateCameraUi()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateCameraUi()
    }
    private var isTorchOpen = false

    private var result = ""

    private fun updateCameraUi() {
        viewFinder.post {
            displayId = viewFinder.display.displayId
            bindUseCases()
        }

        container.findViewById<ImageButton>(R.id.btn_capture).setOnClickListener {
            takePicture()
        }

        container.findViewById<Button>(R.id.btnTorch).setOnClickListener {
            isTorchOpen = if(isTorchOpen) {
                camera?.cameraControl?.enableTorch(true)
                false
            } else {
                camera?.cameraControl?.enableTorch(false)
                true
            }

        }
    }

    private fun takePicture() {
        imageCapture?.let { imageCapture ->
            val file = createFile(getOutputDirectory(context!!), FILENAME, PHOTO_EXTENSION)
            val metaData = ImageCapture.Metadata().apply {
                isReversedHorizontal = (lensFacing == CameraSelector.LENS_FACING_FRONT)
            }
            val outputOptions = ImageCapture.OutputFileOptions.Builder(file)
                .setMetadata(metaData)
                .build()

            // setup image capture
            imageCapture.takePicture(
                outputOptions,
                executor,
                object:ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val bitmap = decodeBitmap(file)
                            imageClassifier.analyze(bitmap){
                                Log.e(TAG, "result is $it")
                                result = it
                                CoroutineScope(Dispatchers.Main).launch {
                                    ResultFragment.show(childFragmentManager, result)
                                }
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        when (exception.imageCaptureError) {
                            ImageCapture.ERROR_UNKNOWN -> {}
                            ImageCapture.ERROR_FILE_IO -> {}
                            ImageCapture.ERROR_CAPTURE_FAILED -> {}
                            ImageCapture.ERROR_CAMERA_CLOSED-> {}
                            ImageCapture.ERROR_INVALID_CAMERA-> {}
                        }
                        exception.printStackTrace()
                    }
            })
        }
    }

    override fun onResume() {
        super.onResume()
        fragmentManager?.let {
            ResultFragment.dismiss(it)
        }
    }

    override fun onDestroyView() {
        executor.shutdown()
        super.onDestroyView()
    }

    private suspend fun decodeBitmap(file: File): Bitmap {
        // First, decode EXIF data and retrieve transformation matrix
        return withContext(CoroutineScope(Dispatchers.IO).coroutineContext) {
            generateBitmap(file)
        }
    }

    private fun generateBitmap(file:File):Bitmap{
        val exif = ExifInterface(file.absolutePath)
        val transformation = decodeExifOrientation(
            exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_ROTATE_90
            )
        )

        // Read bitmap using factory methods, and transform it using EXIF data
        val bitmapOptions = BitmapFactory.Options().apply {
            inSampleSize = 1
        }

        val bitmap = BitmapFactory.decodeFile(file.absolutePath, bitmapOptions)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, transformation, true)
    }

    private fun decodeExifOrientation(orientation: Int): Matrix {
        val matrix = Matrix()

        // Apply transformation corresponding to declared EXIF orientation
        when (orientation) {
            ExifInterface.ORIENTATION_NORMAL -> Unit
            ExifInterface.ORIENTATION_UNDEFINED -> Unit
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90F)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180F)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270F)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1F, 1F)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1F, -1F)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postScale(-1F, 1F)
                matrix.postRotate(270F)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postScale(-1F, 1F)
                matrix.postRotate(90F)
            }

            // Error out if the EXIF orientation is invalid
            else -> throw IllegalArgumentException("Invalid orientation: $orientation")
        }

        // Return the resulting matrix
        return matrix
    }

//    private fun transformImageProxyToBitmap(image:ImageProxy): Bitmap {
//        image.close()
//        val buffer = image.planes[0].buffer
//        val bytes = ByteArray(buffer.remaining())
//        buffer.get(bytes)
//        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
//    }
//    private fun updateTransform() {
//        val matrix = Matrix()
//
//        // Compute the center of the view finder
//        val centerX = viewFinder.width / 2f
//        val centerY = viewFinder.height / 2f
//
//        // Correct preview output to account for display rotation
//        val rotationDegrees = when(viewFinder.display.rotation) {
//            Surface.ROTATION_0 -> 0
//            Surface.ROTATION_90 -> 90
//            Surface.ROTATION_180 -> 180
//            Surface.ROTATION_270 -> 270
//            else -> return
//        }
//        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)
//
//        // Finally, apply transformations to our TextureView
//        viewFinder.setTransform(matrix)
//    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    private fun bindUseCases(){
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")
        val rotation = viewFinder.display.rotation

        // CameraProvider to the LifecycleOwner
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener(Runnable {
            // Initializing Camera Provider
            val cameraProvider:ProcessCameraProvider = cameraProviderFuture.get()

            // New Preview
            preview = Preview.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build()
            preview?.setSurfaceProvider(viewFinder.previewSurfaceProvider)

            // Old Preview
//            val previewConfig = PreviewConfig.Builder().apply {
//            setLensFacing(CameraX.LensFacing.BACK)
//            setTargetResolution(Size(metrics.widthPixels,metrics.heightPixels))
//            setTargetRotation(viewFinder.display.rotation)
//        }.build()
//
//        val preview = Preview(previewConfig)
//        preview.setOnPreviewOutputUpdateListener {
//            val parent = viewFinder.parent as ViewGroup
//            parent.removeView(viewFinder)
//            parent.addView(viewFinder, 0)
//            viewFinder.surfaceTexture = it.surfaceTexture
//            updateTransform()
//        }


            // Image Analysis
            imageAnalyzer = ImageAnalysis.Builder()
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build()


            // Old Image Analysis
//            val imageAnalysisConfig = ImageAnalysisConfig.Builder().apply {
//            setLensFacing(CameraX.LensFacing.BACK)
//            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
//            setTargetRotation(viewFinder.display.rotation)
//            }.build()
//            val executor = Executors.newSingleThreadExecutor()
//            imageAnalyzer = ImageAnalysis(imageAnalysisConfig).apply {
//            setAnalyzer(executor, ImageAnalysis.Analyzer { image, rotationDegrees -> })
//            }

            // This is changed API at Alpha06. you were only supposed to declare Analyzer itself.
            // but from alpha, we have to generate and put executor into setAnalyzer.
            // What is ImageProxy?
            // What is Analyzer?

            // Image Capture
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetAspectRatio(screenAspectRatio)
                .setTargetRotation(rotation)
                .build()

            // Old Image Capture
//        val imageCaptureConfig = ImageCaptureConfig.Builder().apply {
//            setLensFacing(CameraX.LensFacing.BACK) // CameraX.LensFacing.BACK or FRONT
//            setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY) // MIN_LATENCY or MAX_QUALITY
//            setTargetResolution(Size(metrics.widthPixels, metrics.heightPixels)) // setTargetResolution or setTargetAspectRatio. it is changed at this version
//            setTargetRotation(viewFinder.display.rotation)
//        }.build()
//        imageCapture = ImageCapture(imageCaptureConfig)

            // Must unbind the use-cases before rebinding them
            cameraProvider.unbindAll()

            try {
                camera = cameraProvider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageCapture, imageAnalyzer)
            } catch (e:Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    companion object {
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        /** Milliseconds used for UI animations */
        private const val ANIMATION_FAST_MILLIS = 50L
        private const val ANIMATION_SLOW_MILLIS = 100L

        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0

        /** Helper function used to create a timestamped file */
        private fun createFile(baseFolder: File, format: String, extension: String) =
            File(baseFolder, SimpleDateFormat(format, Locale.US)
                .format(System.currentTimeMillis()) + extension)

        /** Use external media if it is available, our app's file directory otherwise */
        fun getOutputDirectory(context: Context): File {
            val appContext = context.applicationContext
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() } }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else appContext.filesDir
        }
    }
}