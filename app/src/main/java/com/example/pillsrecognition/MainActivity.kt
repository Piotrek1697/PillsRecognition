package com.example.pillsrecognition

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Surface.ROTATION_90
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Camera
import com.google.ar.sceneform.animation.ModelAnimator
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.objects.FirebaseVisionObject
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.automl.AutoMLImageLabelerLocalModel
import com.google.mlkit.vision.label.automl.AutoMLImageLabelerOptions
import com.otaliastudios.cameraview.Frame
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private var arFragment: ArFragment? = null
    private var animationNospa: ModelRenderable? = null
    private var anchorNode: AnchorNode? = null
    private var animator: ModelAnimator? = null
    private var nextAnimation : Int = 0
    private var transformNode: TransformableNode? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
//            != PackageManager.PERMISSION_GRANTED
//        ) {
//            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
//
//        } else {
//            cameraView!!.setLifecycleOwner(this)
//
//
//            cameraView!!.addFrameProcessor {
//                extractDataFromFrame(it!!) { result ->
//                    text_view.text = result
//                }
//            }
//        }

        arFragment = supportFragmentManager.findFragmentById(R.id.sceneform_fragment) as ArFragment
        arFragment!!.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            if (animationNospa == null) {
                val anchor = hitResult.createAnchor()
                if (anchorNode == null) {
                    anchorNode = AnchorNode(anchor)
                    anchorNode!!.setParent(arFragment!!.arSceneView.scene)

                    transformNode = TransformableNode(arFragment!!.transformationSystem)
                    transformNode!!.scaleController.minScale = 0.09f
                    transformNode!!.scaleController.maxScale = 0.09f
                    transformNode!!.setParent(anchorNode)
                    transformNode!!.renderable = animationNospa
                }
            }

        }
        arFragment!!.arSceneView.scene.addOnUpdateListener {
            if (anchorNode == null){
                if(animate.isEnabled){
                    animate.backgroundTintList = ColorStateList.valueOf(Color.GRAY)
                    animate.isEnabled = false
                }
            }else{
                if(animate.isEnabled){
                    animate.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this,R.color.colorAccent))
                    animate.isEnabled = true
                }
            }

        }

        animate.isEnabled = false
        animate.setOnClickListener {
            if(animator == null || !animator!!.isRunning){
                val data = animationNospa!!.getAnimationData(nextAnimation)
                nextAnimation = (nextAnimation + 1) % animationNospa!!.animationDataCount
                animator = ModelAnimator(data,animationNospa)
                animator!!.start()
            }
        }

        setupModel()




    }

    private fun setupModel() {
        ModelRenderable.builder() // To load as an asset from the 'assets' folder ('src/main/assets/andy.sfb'):
            .setSource(this, R.raw.cangrejo)
            .build().thenAccept { modelRenderable ->
                animationNospa = modelRenderable
            }.exceptionally { throwable ->
                Toast.makeText(this, ""+throwable.message,Toast.LENGTH_SHORT).show()
                null
            }
    }

    private fun extractDataFromFrame(frame: Frame, callback: (String) -> Unit) {

        val localModel = AutoMLImageLabelerLocalModel.Builder()
            .setAssetFilePath("manifest.json")
            // or .setAbsoluteFilePath(absolute file path to manifest file)
            .build()

        val autoMLImageLabelerOptions = AutoMLImageLabelerOptions.Builder(localModel)
            .setConfidenceThreshold(0.5f)  // Evaluate your model in the Firebase console
            // to determine an appropriate value.
            .build()
        val labeler = ImageLabeling.getClient(autoMLImageLabelerOptions)
        labeler.process(getVisionImageFromFrame(frame))
            .addOnSuccessListener { labels ->
                for (label in labels) {
                    val text = label.text
                    val confidence = label.confidence
                    val index = label.index
                    Log.d("LabelTag", text)
                    Log.d("LabelTag", confidence.toString())
                    Log.d("LabelTag", index.toString())
                }
            }.addOnFailureListener { e ->

            }

    }

    private fun getVisionImageFromFrame(frame: Frame): InputImage {
        val data: ByteArray = frame?.data
        val imageMetaData = FirebaseVisionImageMetadata.Builder()
            .setFormat(FirebaseVisionImageMetadata.IMAGE_FORMAT_NV21)
            .setRotation(FirebaseVisionImageMetadata.ROTATION_90)
            .setHeight(frame.size.height)
            .setWidth(frame.size.width)
            .build()

        val image = InputImage.fromByteArray(
            data,
            /* image width */ frame.size.width,
            /* image height */ frame.size.height,
            ROTATION_90,
            InputImage.IMAGE_FORMAT_NV21
        );// or IMAGE_FORMAT_YV12)

        return image
    }

}

class DrawingView(context: Context, var visionObjects: List<FirebaseVisionObject>) : View(context) {

    companion object {
        val categoryNames: Map<Int, String> = mapOf(
            FirebaseVisionObject.CATEGORY_UNKNOWN to "Unknown",
            FirebaseVisionObject.CATEGORY_HOME_GOOD to "Home Goods",
            FirebaseVisionObject.CATEGORY_FASHION_GOOD to "Fashion Goods",
            FirebaseVisionObject.CATEGORY_FOOD to "Food",
            FirebaseVisionObject.CATEGORY_PLACE to "Place",
            FirebaseVisionObject.CATEGORY_PLANT to "Plant"
        )
    }
}
