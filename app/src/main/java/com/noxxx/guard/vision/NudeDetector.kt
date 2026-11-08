package com.noxxx.guard.vision

import android.content.Context
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Runs the bundled NudeNet 320n detector (YOLOv8n, AGPL-3.0, float16 TFLite) fully on-device. Not
 * thread-safe: use from one thread.
 */
class NudeDetector(context: Context) : Closeable {
    private val interpreter = Interpreter(loadModel(context), Interpreter.Options().setNumThreads(2))

    /** Square input side, 320. */
    val side: Int
    private val input: ByteBuffer
    private val output: Array<Array<FloatArray>>

    init {
        val shape = interpreter.getInputTensor(0).shape() // [1, 320, 320, 3], float32 RGB 0..1
        side = shape[1]
        input = ByteBuffer.allocateDirect(side * side * 3 * 4).order(ByteOrder.nativeOrder())
        val out = interpreter.getOutputTensor(0).shape() // [1, 22, 2100]
        output = arrayOf(Array(out[1]) { FloatArray(out[2]) })
    }

    /** [pixels] is ARGB, exactly side * side: the screen letterboxed into the top-left corner on black. */
    fun detect(pixels: IntArray): Scores {
        input.rewind()
        for (px in pixels) {
            input.putFloat(((px shr 16) and 0xFF) / 255f)
            input.putFloat(((px shr 8) and 0xFF) / 255f)
            input.putFloat((px and 0xFF) / 255f)
        }
        input.rewind()
        interpreter.run(input, output)
        return Scores(YoloDecoder.decode(output[0]))
    }

    override fun close() = interpreter.close()

    private companion object {
        const val MODEL = "nudenet_320n_fp16.tflite"

        fun loadModel(context: Context): MappedByteBuffer {
            val fd = context.assets.openFd(MODEL)
            return FileInputStream(fd.fileDescriptor).use { stream ->
                stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
            }
        }
    }
}
