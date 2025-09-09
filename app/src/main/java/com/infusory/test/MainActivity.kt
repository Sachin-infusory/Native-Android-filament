package com.infusory.test

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Choreographer
import android.view.SurfaceView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.filament.View
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.AutomationEngine
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private val animationIndex=1

    companion object {
        init {
            Utils.init()
        }
    }


    private lateinit var surfaceView: SurfaceView
    private lateinit var choreographer: Choreographer
    private lateinit var uiHelper: UiHelper

    private lateinit var modelViewer: ModelViewer


    private val viewerContent= AutomationEngine.ViewerContent()
    private val frameCallback = FrameCallback()
//    private val frameScheduler = ()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        surfaceView=findViewById(R.id.surfaceView)

        choreographer= Choreographer.getInstance()

        uiHelper= UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply { isOpaque=false }

        modelViewer= ModelViewer(surfaceView=surfaceView,uiHelper=uiHelper)

        surfaceView.setOnTouchListener { _,event ->
            modelViewer.onTouchEvent(event)
            true
        }

        createRenderables()
        createIndirectLight()
        configureViewer()
//        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
//            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
//            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
//            insets
//        }
    }

    private fun configureViewer(){
        modelViewer.view.blendMode = View.BlendMode.TRANSLUCENT
        modelViewer.renderer.clearOptions = modelViewer.renderer.clearOptions.apply {
            clear = true
        }

        modelViewer.view.apply {
            // Use opaque blend mode for better performance
            blendMode = View.BlendMode.OPAQUE

            // Optimize render quality for performance
            renderQuality = renderQuality.apply {
                hdrColorBuffer = View.QualityLevel.LOW
            }

            // Disable dynamic resolution for consistent performance
            dynamicResolutionOptions = dynamicResolutionOptions.apply {
                enabled = false
                quality = View.QualityLevel.LOW
            }

            // Disable MSAA for better performance
            multiSampleAntiAliasingOptions = multiSampleAntiAliasingOptions.apply {
                enabled = false
            }

            // Disable anti-aliasing for maximum performance
            antiAliasing = View.AntiAliasing.NONE

            // Disable expensive effects
            ambientOcclusionOptions = ambientOcclusionOptions.apply {
                enabled = false
            }

            bloomOptions = bloomOptions.apply {
                enabled = false
            }

            // Disable screen space reflections
            screenSpaceReflectionsOptions = screenSpaceReflectionsOptions.apply {
                enabled = false
            }

            // Optimize temporal anti-aliasing
            temporalAntiAliasingOptions = temporalAntiAliasingOptions.apply {
                enabled = false  // Disable for maximum performance
            }

            // Disable fog for performance
            fogOptions = fogOptions.apply {
                enabled = false
            }

            // Optimize depth of field
            depthOfFieldOptions = depthOfFieldOptions.apply {
                enabled = false
            }

            // Optimize vignette
            vignetteOptions = vignetteOptions.apply {
                enabled = false
            }

//            // Disable post-processing effects
//            isPostProcessingEnabled = false
        }
    }

    private fun createIndirectLight(){
        val engine = modelViewer.engine
        val scene = modelViewer.scene
        val ibl="default_env_ibl.ktx"
        val skyBox="default_env_skybox.ktx"
        readCompressedAsset(ibl).let{
            scene.indirectLight= KTX1Loader.createIndirectLight(engine,it)
            scene.indirectLight!!.intensity=50_000.0f
            viewerContent.indirectLight=modelViewer.scene.indirectLight
        }

//        readCompressedAsset(skyBox).let {
//            scene.skybox= KTX1Loader.createSkybox(engine,it)
//        }

    }

    private fun readCompressedAsset(assetName:String):ByteBuffer{
        val input =assets.open(assetName)
        val bytes = ByteArray(input.available())
        input.read(bytes)
        return ByteBuffer.wrap(bytes)
    }


    private fun createRenderables(){
        val buffer = assets.open("skeleton.glb").use{ input ->
            val bytes = ByteArray(input.available())
            input.read(bytes)
            ByteBuffer.allocateDirect(bytes.size).apply {
                order(ByteOrder.nativeOrder())
                put(bytes)
                rewind()
            }
        }

        modelViewer.loadModelGlb(buffer)
        modelViewer.transformToUnitCube()

        // Clear the scene first
        val scene = modelViewer.scene
        val asset = modelViewer.asset

        if (asset != null) {
            // Remove all entities from scene
            val entities = asset.entities
            for (entity in entities) {
                scene.removeEntity(entity)
            }

            // Add back only the first entity (or the one you want)
            if (entities.isNotEmpty()) {
                scene.addEntity(entities[0])
            }
        }
    }

    inner class FrameCallback : Choreographer.FrameCallback {
        private val startTime = System.nanoTime()
        private var currentAnimationIndex = 0
        private var animationStartTime = startTime
        private var currentAnimationDuration = 0f

        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)
            modelViewer.render(frameTimeNanos)

            modelViewer.animator?.apply {
                if (animationCount > 0) {
                    // Calculate elapsed time for current animation
                    val elapsedTimeSeconds = (frameTimeNanos - animationStartTime).toDouble() / 1000000000

                    // Get current animation duration
                    if (currentAnimationDuration == 0f) {
                        currentAnimationDuration = getAnimationDuration(currentAnimationIndex)
                    }

                    // Check if current animation has finished
                    if (elapsedTimeSeconds >= currentAnimationDuration) {
                        // Move to next animation
                        currentAnimationIndex = (currentAnimationIndex + 1) % animationCount
                        animationStartTime = frameTimeNanos
                        currentAnimationDuration = getAnimationDuration(currentAnimationIndex)

                        // Reset elapsed time for new animation
                        val newElapsedTime = 0f
                        applyAnimation(currentAnimationIndex, newElapsedTime)
                    } else {
                        // Continue current animation
                        applyAnimation(currentAnimationIndex, elapsedTimeSeconds.toFloat())
                    }

                    updateBoneMatrices()
                }
            }
        }

        // Optional: Method to manually switch to a specific animation
        fun switchToAnimation(animationIndex: Int) {
            modelViewer.animator?.apply {
                if (animationIndex >= 0 && animationIndex < animationCount) {
                    currentAnimationIndex = animationIndex
                    animationStartTime = System.nanoTime()
                    currentAnimationDuration = getAnimationDuration(currentAnimationIndex)
                }
            }
        }

        // Optional: Method to get current animation info
        fun getCurrentAnimationInfo(): String {
            return modelViewer.animator?.let { animator ->
                if (animator.animationCount > 0) {
                    "Animation ${currentAnimationIndex + 1}/${animator.animationCount} - ${animator.getAnimationName(currentAnimationIndex)}"
                } else {
                    "No animations available"
                }
            } ?: "No animator available"
        }
    }

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameCallback)
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameCallback)
    }

    override fun onDestroy() {
        super.onDestroy()
        choreographer.removeFrameCallback(frameCallback)
        uiHelper.detach()
    }

}