package com.simats.growise.farmer

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class CropDiseaseClassifier(context: Context) {
    private var interpreter: Interpreter? = null
    private var labels = listOf<String>()
    private val inputSize = 224

    init {
        val modelBuffer = loadModelFile(context, "crop_model.tflite")
        interpreter = Interpreter(modelBuffer)
        labels = context.assets.open("labels.txt").bufferedReader().readLines()
    }

    private fun loadModelFile(context: Context, modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    // Intelligent Scaling: Pads with black to maintain aspect ratio, preventing stretching
    private fun formatBitmap(bitmap: Bitmap): Bitmap {
        val size = Math.max(bitmap.width, bitmap.height)
        val paddedBitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(paddedBitmap)
        canvas.drawColor(Color.BLACK)
        val left = (size - bitmap.width) / 2f
        val top = (size - bitmap.height) / 2f
        canvas.drawBitmap(bitmap, left, top, Paint())
        return Bitmap.createScaledBitmap(paddedBitmap, inputSize, inputSize, true)
    }

    fun analyze(bitmap: Bitmap): String {
        val formattedBitmap = formatBitmap(bitmap)
        // 4 bytes per float * 224 * 224 * 3 channels
        val byteBuffer = ByteBuffer.allocateDirect(4 * inputSize * inputSize * 3)
        byteBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(inputSize * inputSize)
        formattedBitmap.getPixels(intValues, 0, formattedBitmap.width, 0, 0, formattedBitmap.width, formattedBitmap.height)

        var pixel = 0
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val `val` = intValues[pixel++]
                // FIX: The Colab model already has preprocess_input baked inside it.
                // Sending [-1, 1] from Android causes "Double Normalization", blinding the AI.
                // We must send raw [0, 255] float pixels and let the TFLite model handle the math.
                byteBuffer.putFloat((`val` shr 16 and 0xFF).toFloat())
                byteBuffer.putFloat((`val` shr 8 and 0xFF).toFloat())
                byteBuffer.putFloat((`val` and 0xFF).toFloat())
            }
        }

        val output = Array(1) { FloatArray(labels.size) }
        interpreter?.run(byteBuffer, output)

        val probabilities = output[0]
        val maxIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: -1

        if (maxIndex != -1) {
            val label = labels[maxIndex]
            val confidence = probabilities[maxIndex] * 100

            // 1. Non-Plant Rejection Check
            if (label.equals("Background_Noise", ignoreCase = true)) {
                return "Error: It is not a plant or crop. Show me the crop or plant."
            }

            // 2. Intelligent Confidence Thresholds
            return if (confidence < 45.0) {
                "Error: Crop not recognized by offline mode. Please switch online for an advanced AI diagnosis."
            } else if (confidence in 45.0..70.0) {
                "Disease Identified: ${label.replace("_", " ")}\nConfidence: ${String.format("%.1f", confidence)}%\n\n⚠️ Not fully sure. Double-check online if connected."
            } else {
                "Disease Identified: ${label.replace("_", " ")}\nConfidence: ${String.format("%.1f", confidence)}%"
            }
        }
        return "Error: Analysis failed."
    }

    fun close() {
        interpreter?.close()
    }
}