package sg.toru.cebudevfest19.ui.fragment

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.*
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.camera.core.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.lifecycleScope
import com.google.firebase.ml.vision.common.FirebaseVisionImage
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

/**
 * A simple [Fragment] subclass.
 */
class CameraFragment : Fragment() {
    private val TAG = "CameraFragment"

    private var preview:Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalyzer: ImageAnalysis? = null

    private lateinit var container:ConstraintLayout
    private lateinit var viewFinder:TextureView

    // test code
    private lateinit var imageTest: ImageView

    private val imageClassifier:ImageClassfier by lazy {
        ImageClassfier()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    private val executor:ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view as ConstraintLayout
        imageTest = view.findViewById(R.id.img_test)
        viewFinder = view.findViewById(R.id.view_finder)
        viewFinder.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTransform()
        }
        viewFinder.post {
            bindPreviewUseCase()
            bindImageAnalysisUseCase()
            bindImageCaptureUseCase()

            // That's all!! by doing this, CameraX can follow UI Lifecycle.
            CameraX.bindToLifecycle(activity, preview, imageCapture, imageAnalyzer)
        }

        var result = ""
        view.findViewById<ImageButton>(R.id.btn_capture).setOnClickListener {
            imageCapture?.let { capture ->
                val file = createFile(getOutputDirectory(context!!), FILENAME, PHOTO_EXTENSION)
                capture.takePicture(file, ImageCapture.Metadata(), executor, object: ImageCapture.OnImageSavedListener {
                    override fun onImageSaved(file: File) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            val bitmap = decodeBitmap(file)
                            imageClassifier.analyze(bitmap){
                                Log.e(TAG, "result is $it")
                                result = it
                                CoroutineScope(Dispatchers.Main).launch {
                                    Toast.makeText(context, "result is $result !!!", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }

                    override fun onError(
                        imageCaptureError: ImageCapture.ImageCaptureError,
                        message: String,
                        cause: Throwable?
                    ) {
                        cause?.printStackTrace()
                    }
                })

                container.postDelayed({
                    container.foreground = ColorDrawable(Color.WHITE)
                    container.postDelayed(
                        { container.foreground = null }, ANIMATION_FAST_MILLIS)
                }, ANIMATION_SLOW_MILLIS)


                /**/
//                capture.takePicture(executor, object:ImageCapture.OnImageCapturedListener(){
//                    override fun onCaptureSuccess(
//                        image: ImageProxy?,
//                        rotationDegrees: Int
//                    ) {
//                        image?.let { image ->
//                            imageTest.setImageBitmap(transformImageProxyToBitmap(image))
//                        }
//                        super.onCaptureSuccess(image, rotationDegrees)
//                    }
//
//                    override fun onError(
//                        imageCaptureError: ImageCapture.ImageCaptureError,
//                        message: String,
//                        cause: Throwable?
//                    ) {
//                        super.onError(imageCaptureError, message, cause)
//                        cause?.printStackTrace()
//                    }
//                })
            }
        }
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

    private fun transformImageProxyToBitmap(image:ImageProxy): Bitmap {
        image.close()
        val buffer = image.planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    private fun updateTransform() {
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = viewFinder.width / 2f
        val centerY = viewFinder.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when(viewFinder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        viewFinder.setTransform(matrix)
    }

    private fun bindUseCases(){
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val previewConfig = PreviewConfig.Builder().apply {
            setLensFacing(CameraX.LensFacing.BACK)
            setTargetResolution(Size(metrics.widthPixels,metrics.heightPixels))
            setTargetRotation(viewFinder.display.rotation)
        }.build()

        val preview = Preview(previewConfig)
        preview.setOnPreviewOutputUpdateListener {
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)
            viewFinder.surfaceTexture = it.surfaceTexture
            updateTransform()
        }

        val imageAnalysisConfig = ImageAnalysisConfig.Builder().apply {
            setLensFacing(CameraX.LensFacing.BACK)
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            setTargetRotation(viewFinder.display.rotation)
        }.build()


        // This is changed API at Alpha06. you were only supposed to declare Analyzer itself.
        // but from alpha, we have to generate and put executor into setAnalyzer.
        // What is ImageProxy?
        // What is Analyzer?

        val executor = Executors.newSingleThreadExecutor()
        imageAnalyzer = ImageAnalysis(imageAnalysisConfig).apply {
            setAnalyzer(executor, ImageAnalysis.Analyzer { image, rotationDegrees -> })
        }

        val imageCaptureConfig = ImageCaptureConfig.Builder().apply {
            setLensFacing(CameraX.LensFacing.BACK) // CameraX.LensFacing.BACK or FRONT
            setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY) // MIN_LATENCY or MAX_QUALITY
            setTargetResolution(Size(metrics.widthPixels, metrics.heightPixels)) // setTargetResolution or setTargetAspectRatio. it is changed at this version
            setTargetRotation(viewFinder.display.rotation)
        }.build()
        imageCapture = ImageCapture(imageCaptureConfig)

        // That's all!! by doing this, CameraX can follow UI Lifecycle.
        CameraX.bindToLifecycle(activity, preview, imageCapture, imageAnalyzer)
    }

    private fun bindPreviewUseCase(){
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val previewConfig = PreviewConfig.Builder().apply {
            setLensFacing(CameraX.LensFacing.BACK)
            setTargetResolution(Size(metrics.widthPixels,metrics.heightPixels))
            setTargetRotation(viewFinder.display.rotation)
        }.build()

        preview = Preview(previewConfig)
        preview?.setOnPreviewOutputUpdateListener {
            val parent = viewFinder.parent as ViewGroup
            parent.removeView(viewFinder)
            parent.addView(viewFinder, 0)
            viewFinder.surfaceTexture = it.surfaceTexture
            updateTransform()
        }
    }

    private fun bindImageAnalysisUseCase(){
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")
        val imageAnalysisConfig = ImageAnalysisConfig.Builder().apply {
            setLensFacing(CameraX.LensFacing.BACK)
            setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
            setTargetRotation(viewFinder.display.rotation)
        }.build()


        // This is changed API at Alpha06. you were only supposed to declare Analyzer itself.
        // but from alpha, we have to generate and put executor into setAnalyzer.
        // What is ImageProxy?
        // What is Analyzer?

        val executor = Executors.newSingleThreadExecutor()
        imageAnalyzer = ImageAnalysis(imageAnalysisConfig).apply {
            setAnalyzer(executor, ImageAnalysis.Analyzer { image, rotationDegrees ->

            })
        }
    }

    private fun bindImageCaptureUseCase(){
        val metrics = DisplayMetrics().also { viewFinder.display.getRealMetrics(it) }
        val screenAspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val imageCaptureConfig = ImageCaptureConfig.Builder().apply {
            setLensFacing(CameraX.LensFacing.BACK) // CameraX.LensFacing.BACK or FRONT
            setCaptureMode(ImageCapture.CaptureMode.MIN_LATENCY) // MIN_LATENCY or MAX_QUALITY
//            setTargetResolution(Size(metrics.widthPixels, metrics.heightPixels)) // setTargetResolution or setTargetAspectRatio. it is changed at this version

            setTargetResolution(Size(600,800)) // setTargetResolution or setTargetAspectRatio. it is changed at this version
            setTargetRotation(viewFinder.display.rotation)
        }.build()
        imageCapture = ImageCapture(imageCaptureConfig)
    }

    override fun onDestroyView() {
        CameraX.unbindAll()
        super.onDestroyView()
    }

    companion object {
        private const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val PHOTO_EXTENSION = ".jpg"
        /** Milliseconds used for UI animations */
        private const val ANIMATION_FAST_MILLIS = 50L
        private const val ANIMATION_SLOW_MILLIS = 100L

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