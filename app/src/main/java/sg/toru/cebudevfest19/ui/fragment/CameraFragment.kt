package sg.toru.cebudevfest19.ui.fragment

import android.graphics.Matrix
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.*
import androidx.fragment.app.Fragment
import androidx.camera.core.*

import sg.toru.cebudevfest19.R
import java.util.concurrent.Executor
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

    private lateinit var viewFinder:TextureView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_camera, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
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
            setAnalyzer(executor, ImageAnalysis.Analyzer { image, rotationDegrees ->

            })
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
            setTargetResolution(Size(metrics.widthPixels, metrics.heightPixels)) // setTargetResolution or setTargetAspectRatio. it is changed at this version
            setTargetRotation(viewFinder.display.rotation)
        }.build()
        imageCapture = ImageCapture(imageCaptureConfig)
    }

    override fun onDestroyView() {
        CameraX.unbindAll()
        super.onDestroyView()
    }
}