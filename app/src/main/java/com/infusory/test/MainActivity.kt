package com.infusory.test

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import android.view.SurfaceView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.android.filament.Engine
import com.google.android.filament.View
import com.google.android.filament.android.UiHelper
import com.google.android.filament.utils.AutomationEngine
import com.google.android.filament.utils.KTX1Loader
import com.google.android.filament.utils.ModelViewer
import com.google.android.filament.utils.Utils
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MainActivity : AppCompatActivity() {

    private val animationIndex = 1
    private val TAG = "MainActivity"
    private var usingVulkan = false

    companion object {
        init {
            Utils.init()
        }
    }

    private lateinit var surfaceView: SurfaceView
    private lateinit var choreographer: Choreographer
    private lateinit var uiHelper: UiHelper
    private lateinit var modelViewer: ModelViewer

    private val viewerContent = AutomationEngine.ViewerContent()
    private val frameCallback = FrameCallback()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)
        choreographer = Choreographer.getInstance()

        // Initialize rendering
        initializeRenderer()

        surfaceView.setOnTouchListener { _, event ->
            modelViewer.onTouchEvent(event)
            true
        }

        createRenderables()
        createIndirectLight()
        configureViewer()
    }

    private fun initializeRenderer() {
        // Check for Vulkan support
        if (isVulkanSupported()) {
            Log.i(TAG, "Vulkan is supported on this device")
            usingVulkan = true
            Toast.makeText(this, "Device supports Vulkan", Toast.LENGTH_SHORT).show()
        } else {
            Log.i(TAG, "Vulkan not supported, using OpenGL ES")
            usingVulkan = false
            Toast.makeText(this, "Using OpenGL ES", Toast.LENGTH_SHORT).show()
        }

        // Create UiHelper and ModelViewer
        // Note: Most Filament versions automatically choose the best backend
        uiHelper = UiHelper(UiHelper.ContextErrorPolicy.DONT_CHECK).apply {
            isOpaque = false
        }

        // Create engine first
        val engine = Engine.create()

        modelViewer = ModelViewer(
            surfaceView = surfaceView,
            engine = engine,
            uiHelper = uiHelper
        )

        Log.i(TAG, "Renderer initialized successfully")
    }

    private fun isVulkanSupported(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val hasVulkanHardware = packageManager.hasSystemFeature(
                PackageManager.FEATURE_VULKAN_HARDWARE_LEVEL, 1
            )
            val hasVulkanVersion = packageManager.hasSystemFeature(
                PackageManager.FEATURE_VULKAN_HARDWARE_VERSION, 0x400003 // Vulkan 1.0.3
            )

            Log.d(TAG, "Vulkan hardware support: $hasVulkanHardware")
            Log.d(TAG, "Vulkan version support: $hasVulkanVersion")

            hasVulkanHardware && hasVulkanVersion
        } else {
            Log.d(TAG, "Android version too old for Vulkan (requires API 24+)")
            false
        }
    }

    private fun configureViewer() {
        modelViewer.view.apply {
            // Use opaque blend mode for better performance
            blendMode = View.BlendMode.OPAQUE

            // Optimize render quality
            renderQuality = renderQuality.apply {
                hdrColorBuffer = if (usingVulkan) View.QualityLevel.MEDIUM else View.QualityLevel.LOW
            }

            // Configure dynamic resolution
            dynamicResolutionOptions = dynamicResolutionOptions.apply {
                enabled = false
                quality = View.QualityLevel.LOW
            }

            // Configure MSAA - enable if Vulkan is supported for better quality
            multiSampleAntiAliasingOptions = multiSampleAntiAliasingOptions.apply {
                enabled = usingVulkan // Enable for Vulkan-capable devices
                sampleCount = if (usingVulkan) 4 else 1
            }

            // Anti-aliasing settings
            antiAliasing = if (usingVulkan) {
                View.AntiAliasing.FXAA // Better AA for Vulkan-capable devices
            } else {
                View.AntiAliasing.NONE
            }

            // Disable expensive effects for better performance
            ambientOcclusionOptions = ambientOcclusionOptions.apply {
                enabled = false
            }

            bloomOptions = bloomOptions.apply {
                enabled = false
            }

            screenSpaceReflectionsOptions = screenSpaceReflectionsOptions.apply {
                enabled = false
            }

            temporalAntiAliasingOptions = temporalAntiAliasingOptions.apply {
                enabled = false
            }

            fogOptions = fogOptions.apply {
                enabled = false
            }

            depthOfFieldOptions = depthOfFieldOptions.apply {
                enabled = false
            }

            vignetteOptions = vignetteOptions.apply {
                enabled = false
            }
        }

        Log.d(TAG, "Viewer configured - Vulkan support: $usingVulkan")
        Log.d(TAG, "MSAA enabled: ${modelViewer.view.multiSampleAntiAliasingOptions.enabled}")
        Log.d(TAG, "Anti-aliasing: ${modelViewer.view.antiAliasing}")
    }

    private fun createIndirectLight() {
        val engine = modelViewer.engine
        val scene = modelViewer.scene
        val ibl = "default_env_ibl.ktx"

        try {
            readCompressedAsset(ibl).let { buffer ->
                scene.indirectLight = KTX1Loader.createIndirectLight(engine, buffer)
                scene.indirectLight!!.intensity = 50_000.0f
                viewerContent.indirectLight = modelViewer.scene.indirectLight
            }
            Log.d(TAG, "Indirect light created successfully")
//            readCompressedAsset("default_env_skybox.ktx").let{
//                buffer ->
//                scene.skybox= KTX1Loader.createSkybox(engine,buffer)
//            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create indirect light", e)
        }
    }

    private fun readCompressedAsset(assetName: String): ByteBuffer {
        val input = assets.open(assetName)
        val bytes = ByteArray(input.available())
        input.read(bytes)
        input.close()
        return ByteBuffer.wrap(bytes)
    }

    private fun createRenderables() {
        try {
            val buffer = assets.open("skeleton.glb").use { input ->
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

                Log.d(TAG, "Model loaded successfully with ${entities.size} entities")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load 3D model", e)
        }
    }

    inner class FrameCallback : Choreographer.FrameCallback {
        private val startTime = System.nanoTime()
        private var currentAnimationIndex = 0
        private var animationStartTime = startTime
        private var currentAnimationDuration = 0f
        private var frameCount = 0
        private var lastFpsTime = startTime

        override fun doFrame(frameTimeNanos: Long) {
            choreographer.postFrameCallback(this)

            try {
                modelViewer.render(frameTimeNanos)
                handleAnimations(frameTimeNanos)
                updateFPS(frameTimeNanos)
            } catch (e: Exception) {
                Log.e(TAG, "Error in frame callback", e)
            }
        }

        private fun handleAnimations(frameTimeNanos: Long) {
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

                        Log.d(TAG, "Switched to animation $currentAnimationIndex")
                    } else {
                        // Continue current animation
                        applyAnimation(currentAnimationIndex, elapsedTimeSeconds.toFloat())
                    }

                    updateBoneMatrices()
                }
            }
        }

        private fun updateFPS(frameTimeNanos: Long) {
            frameCount++
            if (frameTimeNanos - lastFpsTime >= 1000000000L) { // Every second
                val fps = frameCount * 1000000000L / (frameTimeNanos - lastFpsTime)
                val backend = if (usingVulkan) "Vulkan-capable" else "OpenGL ES"
                Log.d(TAG, "FPS: $fps ($backend)")
                frameCount = 0
                lastFpsTime = frameTimeNanos
            }
        }

        // Method to manually switch to a specific animation
        fun switchToAnimation(animationIndex: Int) {
            modelViewer.animator?.apply {
                if (animationIndex >= 0 && animationIndex < animationCount) {
                    currentAnimationIndex = animationIndex
                    animationStartTime = System.nanoTime()
                    currentAnimationDuration = getAnimationDuration(currentAnimationIndex)
                    Log.d(TAG, "Manually switched to animation $animationIndex")
                }
            }
        }

        // Method to get current animation info
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

    // Public method to get rendering info
    fun getRenderingInfo(): String {
        val backend = if (usingVulkan) "Vulkan-capable device" else "OpenGL ES device"
        return "$backend, MSAA: ${modelViewer.view.multiSampleAntiAliasingOptions.enabled}, " +
                "AA: ${modelViewer.view.antiAliasing}"
    }

    // Public method to check if device supports Vulkan
    fun deviceSupportsVulkan(): Boolean {
        return usingVulkan
    }

    override fun onResume() {
        super.onResume()
        choreographer.postFrameCallback(frameCallback)
        Log.d(TAG, "Resumed - Vulkan support: $usingVulkan")
    }

    override fun onPause() {
        super.onPause()
        choreographer.removeFrameCallback(frameCallback)
        Log.d(TAG, "Paused")
    }

    override fun onDestroy() {
        super.onDestroy()
        choreographer.removeFrameCallback(frameCallback)

        // Proper cleanup
        try {
            uiHelper.detach()
            Log.d(TAG, "Cleanup completed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}