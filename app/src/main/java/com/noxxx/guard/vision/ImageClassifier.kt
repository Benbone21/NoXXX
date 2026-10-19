package com.noxxx.guard.vision

import android.content.Context
import com.noxxx.guard.policy.Sensitivity
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** Model output. Class order follows the model's class_labels.txt (GantMan nsfw_model, MobileNetV2, MIT). */
class Scores(private val p: FloatArray) {
    val explicit get() = at(ILLUSTRATED_EXPLICIT) + at(EXPLICIT)
    val suggestive get() = at(SUGGESTIVE)

    private fun at(i: Int) = p.getOrElse(i) { 0f }

    /** Every class score, two decimals, for the diagnostics log. */
    fun toLog(): Map<String, String> = mapOf(
        "drawing" to at(DRAWING), "illustratedExplicit" to at(ILLUSTRATED_EXPLICIT), "neutral" to at(NEUTRAL),
        "explicitPhoto" to at(EXPLICIT), "suggestive" to at(SUGGESTIVE), "explicitTotal" to explicit,
    ).mapValues { "%.2f".format(it.value) }

    // "Suggestive" alone fires on ordinary photos of people (faces, skin, clothes), so it never
    // blocks by itself; it only tips a borderline explicit score over the line in Strict.
    /** The rule each level applies, in words, for logs. Must match [isFlagged]. */
    fun ruleText(s: Sensitivity) = when (s) {
        Sensitivity.RELAXED -> "explicit ≥ 0.90"
        Sensitivity.BALANCED -> "explicit ≥ 0.80"
        Sensitivity.STRICT -> "explicit ≥ 0.60, or explicit ≥ 0.30 with explicit + suggestive ≥ 0.85"
    }

    fun isFlagged(s: Sensitivity) = when (s) {
        Sensitivity.RELAXED -> explicit >= 0.90f
        Sensitivity.BALANCED -> explicit >= 0.80f
        Sensitivity.STRICT -> explicit >= 0.60f || (explicit >= 0.30f && explicit + suggestive >= 0.85f)
    }

    companion object {
        const val DRAWING = 0
        const val ILLUSTRATED_EXPLICIT = 1
        const val NEUTRAL = 2
        const val EXPLICIT = 3
        const val SUGGESTIVE = 4

        /** Each tile must earn a block on its own; scores from different tiles are never combined. */
        fun anyFlagged(tiles: List<Scores>, s: Sensitivity) = tiles.any { it.isFlagged(s) }
    }
}

/** Runs the bundled TFLite image model fully on-device. Not thread-safe: use from one thread. */
class ImageClassifier(context: Context) : Closeable {
    private val interpreter = Interpreter(loadModel(context), Interpreter.Options().setNumThreads(2))
    val inputWidth: Int
    val inputHeight: Int
    private val floatInput: Boolean
    private val input: ByteBuffer
    private val output: Array<FloatArray>

    init {
        val t = interpreter.getInputTensor(0)
        val shape = t.shape() // [1, h, w, 3]
        inputHeight = shape[1]
        inputWidth = shape[2]
        floatInput = t.dataType() == DataType.FLOAT32
        val bytesPerChannel = if (floatInput) 4 else 1
        input = ByteBuffer.allocateDirect(inputWidth * inputHeight * 3 * bytesPerChannel).order(ByteOrder.nativeOrder())
        output = arrayOf(FloatArray(interpreter.getOutputTensor(0).shape().last()))
    }

    /** [pixels] is ARGB, exactly inputWidth * inputHeight. */
    fun classify(pixels: IntArray): Scores {
        input.rewind()
        for (px in pixels) {
            val r = (px shr 16) and 0xFF
            val g = (px shr 8) and 0xFF
            val b = px and 0xFF
            if (floatInput) {
                input.putFloat(r / 255f); input.putFloat(g / 255f); input.putFloat(b / 255f)
            } else {
                input.put(r.toByte()); input.put(g.toByte()); input.put(b.toByte())
            }
        }
        input.rewind()
        interpreter.run(input, output)
        return Scores(output[0].copyOf())
    }

    override fun close() = interpreter.close()

    private companion object {
        const val MODEL = "nsfw_mobilenet_v2.tflite"

        fun loadModel(context: Context): MappedByteBuffer {
            val fd = context.assets.openFd(MODEL)
            return FileInputStream(fd.fileDescriptor).use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }
}
