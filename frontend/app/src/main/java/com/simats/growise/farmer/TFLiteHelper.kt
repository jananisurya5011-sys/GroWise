package com.simats.growise.farmer

import android.content.Context
import android.graphics.Bitmap
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class TFLiteHelper(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var labels: List<String> = emptyList()
    var lastError: String? = null

    init {
        try {
            // Load the model
            val mappedByteBuffer = FileUtil.loadMappedFile(context, "crop_model.tflite")
            val options = Interpreter.Options().apply {
                setNumThreads(4)
            }
            interpreter = Interpreter(mappedByteBuffer, options)

            // Load labels
            val labelsData = context.assets.open("labels.txt").bufferedReader().use { it.readText() }
            labels = labelsData.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
            
            android.util.Log.d("TFLiteHelper", "Model and ${labels.size} labels loaded successfully.")
        } catch (e: Exception) {
            lastError = "Init Error: " + android.util.Log.getStackTraceString(e)
            android.util.Log.e("TFLiteHelper", "Error loading model or labels", e)
        }
    }

    fun predict(bitmap: Bitmap): PredictionResult? {
        if (interpreter == null) {
            lastError = "Interpreter is null. Prev Error: $lastError"
            return null
        }
        if (labels.isEmpty()) {
            lastError = "Labels are empty."
            return null
        }

        try {
            // 1. Resize to 224x224 exactly like Web
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true)

            // 2. Prepare ByteBuffer (1 batch * 224 height * 224 width * 3 channels * 4 bytes per float)
            val byteBuffer = ByteBuffer.allocateDirect(1 * 224 * 224 * 3 * 4)
            byteBuffer.order(ByteOrder.nativeOrder())

            // 3. Normalize to [0.0, 255.0] Float32 exactly like Web (tf.cast(tensor, 'float32'))
            val intValues = IntArray(224 * 224)
            resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)
            
            var pixel = 0
            for (i in 0 until 224) {
                for (j in 0 until 224) {
                    val `val` = intValues[pixel++]
                    // Extract RGB channels
                    val r = ((`val` shr 16) and 0xFF).toFloat()
                    val g = ((`val` shr 8) and 0xFF).toFloat()
                    val b = (`val` and 0xFF).toFloat()
                    
                    // Normalize to [0.0, 1.0] Float32 exactly like backend disease_model_loader.py
                    byteBuffer.putFloat(r / 255.0f)
                    byteBuffer.putFloat(g / 255.0f)
                    byteBuffer.putFloat(b / 255.0f)
                }
            }

            // 4. Run inference
            val outputArray = Array(1) { FloatArray(labels.size) }
            interpreter?.run(byteBuffer, outputArray)

            // 5. Extract probabilities
            val probabilities = outputArray[0]
            
            // 6. Find highest probability
            var maxIndex = 0
            var maxConfidence = probabilities[0]
            for (i in 1 until probabilities.size) {
                if (probabilities[i] > maxConfidence) {
                    maxConfidence = probabilities[i]
                    maxIndex = i
                }
            }

            return PredictionResult(
                disease = labels[maxIndex],
                confidence = maxConfidence
            )
        } catch (e: Exception) {
            lastError = "Predict Error: " + android.util.Log.getStackTraceString(e)
            android.util.Log.e("TFLiteHelper", "Inference error", e)
            return null
        }
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }

    data class PredictionResult(
        val disease: String,
        val confidence: Float
    )
}
