package sg.toru.cebudevfest19.ui.core

import com.google.firebase.FirebaseException
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.automl.FirebaseAutoMLLocalModel
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.label.FirebaseVisionImageLabeler
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceAutoMLImageLabelerOptions
import com.google.firebase.ml.vision.label.FirebaseVisionOnDeviceImageLabelerOptions

class ImageClassfier
@Throws(FirebaseException::class) constructor() {
    private var labeler:FirebaseVisionImageLabeler?

    init {
        val firebaseAutoMLLocalModel = FirebaseAutoMLLocalModel.Builder().setAssetFilePath("").build()
        labeler = FirebaseVision.getInstance()
                    .getOnDeviceAutoMLImageLabeler(
                        FirebaseVisionOnDeviceAutoMLImageLabelerOptions.Builder(firebaseAutoMLLocalModel)
                            .setConfidenceThreshold(0.7F)
                            .build()
                    )
    }

    fun classify(image:FirebaseVisionImage, callback:(String)->Unit){
        labeler?.processImage(image)?.addOnCompleteListener { label ->
            if(label.isSuccessful) {
                label.result?.let { labelListResult->
                    labelListResult.sortByDescending {
                        it.confidence
                    }
                    val detectedImage = labelListResult.firstOrNull {
                        it.confidence >= 0.7F
                    }
                    if(detectedImage == null){
                        callback.invoke("Detected Image Null Case!!")
                    }
                    else{
                        callback.invoke(detectedImage.text)
                    }
                }
            }
        }?.addOnFailureListener { exception ->
            exception.printStackTrace()

        }
    }
}