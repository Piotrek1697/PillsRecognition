package com.example.pillsrecognition

import android.media.Image
import android.os.Bundle
import android.util.Log
import android.view.Surface.ROTATION_90
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.automl.AutoMLImageLabelerLocalModel
import com.google.mlkit.vision.label.automl.AutoMLImageLabelerOptions
import kotlinx.android.synthetic.main.activity_main.*
import java.nio.ByteBuffer
import kotlin.properties.Delegates


class MainActivity : AppCompatActivity() {

    private var arFragment: ArFragment? = null
    private var animationNospa: ModelRenderable? = null
    private var anchorNode: AnchorNode? = null
    private var animator: ModelAnimator? = null
    private var nextAnimation: Int = 0
    private var transformNode: TransformableNode? = null
    private var session: Session? = null
    private var config: Config? = null
    private var frameCounter: Int = 0
    private var detectionIterator = 0
    private var aiArray: MutableList<AiRecognizer> = mutableListOf()

    private var display3DModel: Boolean by Delegates.observable(false) { _, old, new ->
        Log.d("LabelTag", "Name changed from $old to $new")
        if (new)
            enable3DModel()
        else
            disable3DModel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = supportFragmentManager.findFragmentById(R.id.sceneform_fragment) as ArFragment

        //No white dots
        arFragment!!.arSceneView.planeRenderer.isVisible = false

        //No hand
        arFragment!!.planeDiscoveryController.hide()
        arFragment!!.planeDiscoveryController.setInstructionView(null)

        arFragment!!.arSceneView.scene
            .addOnUpdateListener { frameTime: FrameTime? ->
                this.onUpdateFrame(frameTime)
            }


        hideModelButton.setOnClickListener {
            Log.d("TapBool", "${display3DModel}")
            display3DModel = false
            hideModelButton.visibility = View.GONE
            //disable3DModel()
        }

        arFragment!!.setOnSessionInitializationListener {
            Log.d("LabelTag","Session intialize listener")
        }

        arFragment!!.setOnTapArPlaneListener { hitResult, plane, motionEvent ->
            Log.d("TapBool", "Ar Plane tapped")
            if (display3DModel) {
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
                        infoText.text = ""
                        hideModelButton.visibility = View.VISIBLE
                    }
                }
            }
        }

    }

    private fun disable3DModel() {
        if (!display3DModel) {
            arFragment!!.arSceneView.planeRenderer.isVisible = false

            //Wyłączanie Rączki
            //arFragment!!.planeDiscoveryController.hide()
            //arFragment!!.planeDiscoveryController.setInstructionView(null)

            if (anchorNode != null) {
                removeAnchorNode(anchorNode!!)
                anchorNode = null
            }

        }
    }

    private fun enable3DModel() {
        arFragment!!.arSceneView.planeRenderer.isVisible = true
        //arFragment!!.planeDiscoveryController.show()

        val container = findViewById<ViewGroup>(R.id.sceneform_hand_layout)
        arFragment!!.planeDiscoveryController.setInstructionView(container)
        setupModel(aiArray.last().modelChooser)
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
        //No hand
        arFragment!!.planeDiscoveryController.hide()
        arFragment!!.planeDiscoveryController.setInstructionView(null)
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
        frameCounter++
        val view = arFragment!!.arSceneView
        val frame = arFragment!!.arSceneView.arFrame

        // If there is no frame or ARCore is not tracking yet, just return.
        if (frame == null || frame.camera.trackingState != TrackingState.TRACKING) {
            return
        }
        if (frameCounter % 10 == 0) {
            if (!display3DModel) {
                val cameraImage: Image = frame!!.acquireCameraImage()
                //The camera image received is in YUV YCbCr Format. Get buffers for each of the planes and use them to create a new bytearray defined by the size of all three buffers combined
                extractDataFromFrame(getInputImage(cameraImage))
                cameraImage.close()
            }
        }
    }

    private fun getInputImage(cameraImage: Image): InputImage {

        val data: ByteArray
        val buffer0: ByteBuffer = cameraImage.planes[0].buffer
        val buffer2: ByteBuffer = cameraImage.planes[2].buffer
        val buffer0_size: Int = buffer0.remaining()
        val buffer2_size: Int = buffer2.remaining()
        data = ByteArray(buffer0_size + buffer2_size)
        buffer0.get(data, 0, buffer0_size)
        buffer2.get(data, buffer0_size, buffer2_size)


        return InputImage.fromByteArray(
            data,
            /* image width */ cameraImage.width,
            /* image height */ cameraImage.height,
            ROTATION_90,
            InputImage.IMAGE_FORMAT_NV21
        )
    }

    private fun setupModel(model: ModelChooser) {
        Log.d("LabelTag", "Model setup")
        ModelRenderable.builder() // To load as an asset from the 'assets' folder ('src/main/assets/andy.sfb'):
            .setSource(this, model.modelPath!!)
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
            .setConfidenceThreshold(0.65f)  // Evaluate your model in the Firebase console
            // to determine an appropriate value.
            .build()

        //getVisionImageFromFrame(frame){
        val labeler = ImageLabeling.getClient(autoMLImageLabelerOptions)
        labeler.process(frame)
            .addOnSuccessListener { labels ->
                for (label in labels) {
                    val aiRecognizer = AiRecognizer(
                        label.text,
                        label.confidence,
                        label.index,
                        ModelChooser.parseString(label.text)
                    )
                    makeSurePill(aiRecognizer)
                    aiArray.add(aiRecognizer)
                    Log.d("LabelTag", aiRecognizer.toString())
                }
            }.addOnFailureListener { e ->

            }
        //}

    }

    private fun makeSurePill(aiRecognizer: AiRecognizer) {
        val detectionThreshold = 5
        Log.d("LabelTag", "Detection: ${(((detectionIterator.toFloat()) / (detectionThreshold.toFloat())) * 100).toInt()}%")
        infoText.text = "Detecting: ${(((detectionIterator.toFloat()) / (detectionThreshold.toFloat())) * 100).toInt()}%"
        if (aiArray.size > 0) {
            if (aiArray.last().label == aiRecognizer.label) {
                detectionIterator++
            } else {
                detectionIterator = 0
            }
        }
        if (detectionIterator >= detectionThreshold) {
            Log.d("LabelTag", "Rozpoznano tabletke: ${aiRecognizer.label}")
            infoText.text = "Detected: ${aiRecognizer.label} (${(aiRecognizer.probality*100).toInt()}%)\n" +
                    "Find flat surface"
            display3DModel = true
            detectionIterator = 0
            aiArray.clear()
        }
    }

}
