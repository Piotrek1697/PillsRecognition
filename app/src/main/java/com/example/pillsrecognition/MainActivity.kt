package com.example.pillsrecognition

import android.content.Context
import android.graphics.Bitmap
import android.media.Image
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.PixelCopy
import android.view.Surface.ROTATION_90
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.FrameTime
import com.google.ar.sceneform.animation.ModelAnimator
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.google.firebase.ml.vision.objects.FirebaseVisionObject
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.automl.AutoMLImageLabelerLocalModel
import com.google.mlkit.vision.label.automl.AutoMLImageLabelerOptions
import kotlinx.android.synthetic.main.activity_main.*


class MainActivity : AppCompatActivity() {

    private var arFragment: ArFragment? = null
    private var animationNospa: ModelRenderable? = null
    private var anchorNode: AnchorNode? = null
    private var animator: ModelAnimator? = null
    private var nextAnimation: Int = 0
    private var transformNode: TransformableNode? = null
    private var session: Session? = null
    private var config: Config? = null
    private var lele: Int = 0
    private var testBool: Boolean = false

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

        //arFragment!!.transformationSystem.selectionVisualizer = CustomVisualizer()

        //No white dots
        arFragment!!.arSceneView.planeRenderer.isVisible = false

        //No hand
        arFragment!!.planeDiscoveryController.hide()
        arFragment!!.planeDiscoveryController.setInstructionView(null)

        arFragment!!.arSceneView.scene
            .addOnUpdateListener { frameTime: FrameTime? ->
                this.onUpdateFrame(frameTime)
            }

        animate.setOnClickListener {

            testBool = !testBool
            Log.d("TapBool", "${testBool}")
            if (!testBool) {
                arFragment!!.arSceneView.planeRenderer.isVisible = false

                //arFragment!!.planeDiscoveryController.hide()
                //arFragment!!.planeDiscoveryController.setInstructionView(null)

                if (anchorNode != null) {
                    removeAnchorNode(anchorNode!!)
                    anchorNode = null
                }

            } else {
                arFragment!!.arSceneView.planeRenderer.isVisible = true
                //arFragment!!.planeDiscoveryController.show()

                //val container = findViewById<ViewGroup>(R.id.sceneform_hand_layout)
                //arFragment!!.planeDiscoveryController.setInstructionView(container)
            }
        }

        arFragment!!.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            Log.d("TapBool", "Ar Plane tapped")
            if (testBool) {
                if (animationNospa != null) {
                    val anchor = hitResult.createAnchor()
                    if (anchorNode == null) {
                        anchorNode = AnchorNode(anchor)
                        anchorNode!!.setParent(arFragment!!.arSceneView.scene)

                        transformNode = TransformableNode(arFragment!!.transformationSystem)
                        //Set scale of model
                        transformNode!!.scaleController.minScale = 0.03f
                        transformNode!!.scaleController.maxScale = 0.07f
                        transformNode!!.setParent(anchorNode)
                        transformNode!!.renderable = animationNospa
                    }
                }
            }
        }

        setupModel()


    }

    private fun removeAnchorNode(nodeToRemove: AnchorNode) {
        //Remove an Anchor node
        arFragment!!.arSceneView.scene.removeChild(nodeToRemove);
        nodeToRemove.anchor?.detach();
        nodeToRemove.setParent(null);
        nodeToRemove.renderable = null
    }

    override fun onResume() {
        super.onResume()
        if (session == null) {
            session = Session(this@MainActivity)
            session?.setupAutofocus()
        }
    }

    private fun Session.setupAutofocus() {

        //Create the config
        val config = Config(this)

        //Check if the configuration is set to fixed
        if (config?.focusMode == Config.FocusMode.FIXED)
            config?.focusMode = Config.FocusMode.AUTO

        //Sceneform requires that the ARCore session is configured to the UpdateMode LATEST_CAMERA_IMAGE.
        //This is probably not required for just auto focus. I was updating the camera configuration as well
        config?.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE

        //Reconfigure the session
        configure(config)

        //Setup the session with ARSceneView
        arFragment!!.arSceneView.setupSession(this)

        //log out if the camera is in auto focus mode
        Log.d("LabelTag", "The camera is current in focus mode : ${config.focusMode.name}")
    }

    private fun onUpdateFrame(frameTime: FrameTime?) {
        lele++
        val view = arFragment!!.arSceneView
        val frame = arFragment!!.arSceneView.arFrame

        // If there is no frame or ARCore is not tracking yet, just return.
        if (frame == null || frame.camera.trackingState != TrackingState.TRACKING) {
            return
        }
        if (lele % 10 == 0) {
            val cameraImage: Image = frame!!.acquireCameraImage()
            //The camera image received is in YUV YCbCr Format. Get buffers for each of the planes and use them to create a new bytearray defined by the size of all three buffers combined
            extractDataFromFrame(getBitmap(cameraImage))
            cameraImage.close()
        }
    }

    private fun getBitmap(cameraImage: Image): InputImage {
        val cameraPlaneY = cameraImage.planes[0].buffer
        val cameraPlaneU = cameraImage.planes[1].buffer
        val cameraPlaneV = cameraImage.planes[2].buffer

//Use the buffers to create a new byteArray that
        val compositeByteArray =
            ByteArray(cameraPlaneY.capacity() + cameraPlaneU.capacity() + cameraPlaneV.capacity())

        cameraPlaneY.get(compositeByteArray, 0, cameraPlaneY.capacity())
        cameraPlaneU.get(compositeByteArray, cameraPlaneY.capacity(), cameraPlaneU.capacity())
        cameraPlaneV.get(
            compositeByteArray,
            cameraPlaneY.capacity() + cameraPlaneU.capacity(),
            cameraPlaneV.capacity()
        )

        return InputImage.fromByteArray(
            compositeByteArray,
            /* image width */ cameraImage.width,
            /* image height */ cameraImage.height,
            ROTATION_90,
            InputImage.IMAGE_FORMAT_YV12
        )
    }

    private fun setupModel() {
        Log.d("Tap", "Model setup")
        ModelRenderable.builder() // To load as an asset from the 'assets' folder ('src/main/assets/andy.sfb'):
            .setSource(this, R.raw.nospa)
            .build().thenAccept { modelRenderable ->
                animationNospa = modelRenderable
            }.exceptionally { throwable ->
                Toast.makeText(this@MainActivity, "" + throwable.message, Toast.LENGTH_SHORT).show()
                null
            }
    }

    private fun extractDataFromFrame(frame: InputImage) {

        val localModel = AutoMLImageLabelerLocalModel.Builder()
            .setAssetFilePath("manifest.json")
            // or .setAbsoluteFilePath(absolute file path to manifest file)
            .build()

        val autoMLImageLabelerOptions = AutoMLImageLabelerOptions.Builder(localModel)
            .setConfidenceThreshold(0.5f)  // Evaluate your model in the Firebase console
            // to determine an appropriate value.
            .build()

        //getVisionImageFromFrame(frame){
        val labeler = ImageLabeling.getClient(autoMLImageLabelerOptions)
        labeler.process(frame)
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
        //}


    }

    private fun getVisionImageFromFrame(frame: SurfaceView, callback: (InputImage) -> Unit) {
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        PixelCopy.request(frame, bitmap, { copyResult ->
            if (copyResult == PixelCopy.SUCCESS) {
                Log.i("LabelTag", "Copied ArFragment view.")
                callback(InputImage.fromBitmap(bitmap, 0))
            }
        }, Handler())

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
