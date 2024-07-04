package com.example.m_health.model

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.example.m_health.model.data.BPValueBoundingBox
import com.example.m_health.model.data.BoundingBox
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.DequantizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader


/**
 * Class responsible for digit detection process.
 *
 * This class handles the initialization and execution of the digit detection model
 * using TensorFlow Lite. It processes input images, runs the model, and returns
 * detection results through a listener interface.
 *
 * @param context the application context for loading resources and assets.
 * @param modelPath the file path to the TensorFlow Lite model.
 * @param labelPath the file path to the labels file associated with the model.
 * @param detectorListener an instance of `DetectorListener` for receiving detection results.
 */
class DigitDetector (
    private val context: Context,
    private val modelPath: String,
    private val labelPath: String,
    private val detectorListener: DetectorListener
) {
    private var interpreter: Interpreter? = null        // tflite interpreter
    private var labels = mutableListOf<String>()        // list of class labels

    private var tensorWidth = 0             // Input width --> 640
    private var tensorHeight = 0            // Input height --> 640
    private var numChannel = 0              // Output dimension (1cx + 1cy + 1w + 1h + confidence_of_each_class) --> 15
    private var numElements = 0             // Number of output (detected object/boxes) --> 8400

    /** Quantize input from FLOAT32 to INPUT_IMAGE_TYPE */
    private val inputProcessor = ImageProcessor.Builder()
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    /** Dequantize input from OUTPUT_IMAGE_TYPE to FLOAT32 */
    private val resultProcessor = TensorProcessor.Builder()
        .add(DequantizeOp(0f, (1 / 255.0).toFloat()))
        .build()

    /**
     * Initializes the TensorFlow Lite interpreter and sets up the necessary parameters for digit detection.
     *
     * This function loads the TensorFlow Lite model from the specified path, configures the interpreter with the
     * appropriate options, and retrieves input/output tensor shapes and data types. It also reads the labels
     * from the specified file.
     *
     * @throws IOException if there is an error reading the label file.
     */
    fun setup() {
        val model = FileUtil.loadMappedFile(context, modelPath)
        val options = Interpreter.Options()
        options.numThreads = 4
        interpreter = Interpreter(model, options)

        val inputShape = interpreter?.getInputTensor(0)?.shape() ?: return
        val outputShape = interpreter?.getOutputTensor(0)?.shape() ?: return
        val inputType = interpreter?.getInputTensor(0)?.dataType() ?: return
        val outputType = interpreter?.getInputTensor(0)?.dataType() ?: return
        val inputRange = getTensorDataRange(inputType)
        val outputRange = getTensorDataRange(outputType)

        tensorWidth = inputShape[1]
        tensorHeight = inputShape[2]
        numChannel = outputShape[1]
        numElements = outputShape[2]

        Log.d(TAG, "w:$tensorWidth h:$tensorHeight c:$numChannel e:$numElements")
        Log.d(TAG, "in:$inputType($inputRange) out:$outputType($outputRange)")

        try {
            val inputStream: InputStream = context.assets.open(labelPath)
            val reader = BufferedReader(InputStreamReader(inputStream))
            var line: String? = reader.readLine()
            while (line != null && line != "") {
                labels.add(line)
                line = reader.readLine()
            }
            // Log.d(TAG, "Label: $labels")
            reader.close()
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Releases resources associated with the TensorFlow Lite interpreter.
     *
     * This function closes the TensorFlow Lite interpreter and sets it to null,
     * effectively releasing any resources that were allocated for the interpreter.
     * This should be called when the interpreter is no longer needed to avoid memory leaks.
     */
    fun clear() {
        interpreter?.close()
        interpreter = null
    }

    /**
     * Detects digits in the provided frame using the TensorFlow Lite interpreter.
     *
     * This function takes a bitmap frame as input, processes it to match the input tensor shape,
     * runs the TensorFlow Lite interpreter to perform inference, and processes the output to
     * identify bounding boxes and corresponding digit values. It returns the identified digit values
     * as a Triple containing systolic, diastolic, and pulse rate values. Detection results are also
     * passed to the detector listener.
     *
     * @param frame the input bitmap frame to be processed for digit detection.
     * @return a Triple containing the detected systolic, diastolic, and pulse rate values or a Triple of zeros if nothing detected
     */
    fun detect(frame: Bitmap) : Triple<Int, Int, Int> {
        interpreter ?: return Triple(0, 0, 0)
        if (tensorWidth == 0) return Triple(0, 0, 0)
        if (tensorHeight == 0) return Triple(0, 0, 0)
        if (numChannel == 0) return Triple(0, 0, 0)
        if (numElements == 0) return Triple(0, 0, 0)

        var ovrDetectionTime = SystemClock.uptimeMillis()

        val resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)
        val tensorImage = TensorImage(DataType.UINT8)
        tensorImage.load(resizedBitmap)
        val processedImage = inputProcessor.process(tensorImage)
        val imageBuffer = processedImage.buffer
        val output = TensorBuffer.createFixedSize(intArrayOf(1 , numChannel, numElements), OUTPUT_IMAGE_TYPE)

        var inferenceTime = SystemClock.uptimeMillis()
        interpreter?.run(imageBuffer, output.buffer)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime

        val dequantizedBuffer = resultProcessor.process(output)
        val resultBoundingBoxes = getBoundingBox(dequantizedBuffer.floatArray)
        if (resultBoundingBoxes == null) {
            Log.i(TAG, "Nothing detected")
            detectorListener.onEmptyDetect()
            return Triple(0, 0, 0)
        }
        val bpValues = identifyResult(resultBoundingBoxes)

        ovrDetectionTime = SystemClock.uptimeMillis() - ovrDetectionTime
        Log.d(TAG, "Inference: $inferenceTime ms")
        Log.d(TAG, "OVR Detection: $ovrDetectionTime ms")
        Log.d(TAG, "Result: $bpValues")

        detectorListener.onDetect(resultBoundingBoxes, inferenceTime, bpValues)
        return bpValues
    }

    /**
     * Extracts bounding boxes from the model output array.
     *
     * This function processes the output array from the TensorFlow Lite interpreter by extracting bounding boxes
     * that meet the confidence threshold. It iterates through the elements of the output array, identifying
     * the class with the highest confidence for each element, and calculates the bounding box coordinates.
     * Boxes that are outside the valid range are discarded. The resulting bounding boxes are then processed
     * with Non-Maximum Suppression (NMS) to filter out overlapping boxes.
     *
     * @param array the output array from the TensorFlow Lite interpreter containing detection data.
     * @return a list of bounding boxes that meet the confidence threshold, or null if no boxes are found.
     */
    private fun getBoundingBox(array: FloatArray) : List<BoundingBox>? {
        val boundingBoxes = mutableListOf<BoundingBox>()
        for (i in 0 until numElements) {
            var maxConf = -1.0f
            var maxConfIdx = -1
            var j = 4
            var arrayIdx = i + numElements * j
            while (j < numChannel){
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxConfIdx = j - 4
                }
                j++
                arrayIdx += numElements
            }
            if (maxConf > CONFIDENCE_THRESHOLD) {
                val clsName = labels[maxConfIdx]
                val cx = array[i]
                val cy = array[i + numElements]
                val w = array[i + numElements * 2]
                val h = array[i + numElements * 3]
                val x1 = cx - (w/2F)
                val y1 = cy - (h/2F)
                val x2 = cx + (w/2F)
                val y2 = cy + (h/2F)
                if (x1 < 0F || x1 > 1F) continue
                if (y1 < 0F || y1 > 1F) continue
                if (x2 < 0F || x2 > 1F) continue
                if (y2 < 0F || y2 > 1F) continue
                boundingBoxes.add(
                    BoundingBox(
                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                        cx = cx, cy = cy, w = w, h = h,
                        conf = maxConf, cls = maxConfIdx, clsName = clsName
                    )
                )
            }
        }
        Log.d(TAG, "Number of Boxes: ${boundingBoxes.count()}")
        if (boundingBoxes.isEmpty()) return null
        return applyNMS(boundingBoxes)
    }

    /**
     * Applies Non-Maximum Suppression (NMS) to filter overlapping bounding boxes.
     *
     * This function performs Non-Maximum Suppression on a list of bounding boxes to remove
     * boxes that overlap significantly with higher confidence boxes. The boxes are first
     * sorted by confidence score in descending order. The highest confidence box is selected,
     * and all other boxes with an Intersection over Union (IoU) greater than the threshold
     * are removed. This process continues until no boxes remain.
     *
     * @param boxes the list of bounding boxes to be filtered.
     * @return a mutable list of bounding boxes after applying Non-Maximum Suppression.
     */
    private fun applyNMS(boxes: List<BoundingBox>) : MutableList<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.conf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()
        while(sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.first()
            selectedBoxes.add(first)
            sortedBoxes.remove(first)
            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val nextBox = iterator.next()
                val iou = calculateIoU(first, nextBox)
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }
        Log.d(TAG, "Number of Boxes after NMS: ${selectedBoxes.count()}")
        return selectedBoxes
    }

    /**
     * Calculates the Intersection over Union (IoU) of two bounding boxes.
     *
     * This function computes the IoU of two bounding boxes, which is a measure of the overlap
     * between the boxes. The IoU is calculated as the area of the intersection divided by the
     * area of the union of the two boxes.
     *
     * @param box1 the first bounding box.
     * @param box2 the second bounding box.
     * @return the IoU value as a float, ranging from 0 to 1.
     */
    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox) : Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    /**
     * Determines if the first bounding box is significantly inside the second bounding box.
     *
     * This function checks if the area of the intersection between the two bounding boxes
     * is at least 75% of the area of the first bounding box. This is used to determine if
     * the first bounding box is predominantly inside the second bounding box.
     *
     * @param box1 the first bounding box to check.
     * @param box2 the second bounding box to check against.
     * @return true if the first bounding box is at least 75% inside the second bounding box, false otherwise.
     */
    private fun isBox1InsideBox2(box1: BoundingBox, box2: BoundingBox) : Boolean {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        return intersectionArea/box1Area >= 0.75
    }

    /**
     * Identifies the blood pressure (BP) values from the list of bounding boxes.
     *
     * This function processes a list of bounding boxes to identify and extract blood pressure values.
     * It first separates value boxes (labeled "10") from digit boxes, then matches digits to their corresponding
     * value boxes based on containment. It calculates the blood pressure values by sorting and interpreting
     * the digits within each value box, and returns the systolic, diastolic, and pulse values as a Triple.
     *
     * @param boxes the list of bounding boxes to be processed.
     * @return a Triple containing the systolic, diastolic, and pulse values, or (0, 0, 0) if unable to identify all values.
     */
    private fun identifyResult(boxes: List<BoundingBox>) : Triple<Int, Int, Int> {
        val valueBoxes = boxes.filter { it.clsName == "10" }
        val digitBoxes = boxes.minus(valueBoxes.toSet())
        val bpValueBoundingBoxes = mutableListOf<BPValueBoundingBox>()
        for (vBox in valueBoxes) {
            val digits = mutableListOf<BoundingBox>()
            var count = 0
            var sumConf = vBox.conf
            for (dBox in digitBoxes) {
                if (count >= 3) break
                if (isBox1InsideBox2(dBox, vBox)) {
                    digits.add(dBox)
                    count += 1
                    sumConf += dBox.conf
                }
            }
            val sortedDigits = digits.sortedBy { it.x1 }.toMutableList()
            val value = getValueFromDigits(sortedDigits)
            val avgConf = sumConf / (count + 1)
            bpValueBoundingBoxes.add(BPValueBoundingBox(vBox, sortedDigits, value, avgConf))
        }
        if (bpValueBoundingBoxes.count() < 3) return Triple(0, 0, 0)
        val sortedBPValues = bpValueBoundingBoxes.sortedBy { it.valueBox.y1 }
        val sys = sortedBPValues[0]
        val dia = sortedBPValues[1]
        val pul = sortedBPValues[2]
        return Triple(sys.value, dia.value, pul.value)
    }

    /**
     * Converts a list of digit bounding boxes into an integer value.
     *
     * This function takes a list of bounding boxes representing digits and converts them into
     * an integer value by concatenating their class names and parsing the resulting string.
     * If the conversion fails, it returns 0 as the default value.
     *
     * @param digits the list of bounding boxes representing digits.
     * @return the integer value derived from the concatenated digits, or 0 if conversion fails.
     */
    private fun getValueFromDigits(digits: List<BoundingBox>) : Int {
        val value = digits.joinToString("") { it.clsName }
        return value.toIntOrNull() ?: 0
    }

    /**
     * Retrieves the data range for a given tensor data type.
     *
     * This function returns the minimum and maximum allowable values for a given tensor data type.
     * It handles common data types such as FLOAT32, INT8, and UINT8, and throws an IllegalArgumentException
     * for unsupported data types.
     *
     * @param dataType the data type for which to retrieve the data range.
     * @return a Pair containing the minimum and maximum allowable values for the data type.
     * @throws IllegalArgumentException if the data type is not supported.
     */
    private fun getTensorDataRange(dataType: DataType) : Pair<Number, Number> {
        return when (dataType) {
            DataType.FLOAT32 -> Pair(Float.MIN_VALUE, Float.MAX_VALUE)
            DataType.INT8 -> Pair(Byte.MIN_VALUE, Byte.MAX_VALUE)
            DataType.UINT8 -> Pair(0, 255)
            else -> throw IllegalArgumentException("Unsupported data type: $dataType")
        }
    }

    /**
     * Listener interface for handling digit detection events.
     */
    interface DetectorListener {
        /**
         * Called when no detection results are found.
         */
        fun onEmptyDetect()

        /**
         * Called when detection results are available.
         *
         * @param boundingBoxes the list of bounding boxes detected.
         * @param inferenceTime the time taken for inference in milliseconds.
         * @param bpValues the blood pressure values detected as a Triple containing systolic, diastolic, and pulse values.
         */
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long, bpValues: Triple<Int, Int, Int>)
    }

    companion object {
        private const val TAG = "Detector"
        private val INPUT_IMAGE_TYPE = DataType.UINT8
        private val OUTPUT_IMAGE_TYPE = DataType.UINT8
        private const val CONFIDENCE_THRESHOLD = 0.4F
        private const val IOU_THRESHOLD = 0.5F
    }
}