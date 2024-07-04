package com.example.m_health.ui.camera

import android.Manifest
import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.AdapterView.OnItemSelectedListener
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.cardview.widget.CardView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.m_health.MainActivity
import com.example.m_health.R
import com.example.m_health.databinding.FragmentCameraBinding
import com.example.m_health.model.DigitDetector
import com.example.m_health.model.FirestoreDataViewModel
import com.example.m_health.model.ModelConstants.LABELS_PATH
import com.example.m_health.model.ModelConstants.MODEL_PATH
import com.example.m_health.model.data.BPUser
import com.example.m_health.model.data.BoundingBox
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import java.io.FileDescriptor
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class CameraFragment : Fragment(), DigitDetector.DetectorListener, OnItemSelectedListener {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!      // This property is only valid between onCreateView and onDestroyView.

    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isCamPaused: Boolean = false
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var detector: DigitDetector
    private lateinit var safeContext: Context
    private lateinit var firestoreDataViewModel: FirestoreDataViewModel

    // UI Components
    private lateinit var galleryButton: Button
    private lateinit var captureButton: Button
    private lateinit var retakeButton: Button
    private lateinit var confirmButton: Button
    private lateinit var camLiveFeed: PreviewView
    private lateinit var capturedView: ImageView
    private lateinit var boundingBoxOverlay: BoundingBoxOverlay
    private lateinit var inferenceTimeText: TextView
    private lateinit var bpUserSelector: Spinner
    private lateinit var sysValue: TextView
    private lateinit var diaValue: TextView
    private lateinit var pulValue: TextView
    private lateinit var categoryLayout: LinearLayout
    private lateinit var categoryCard: CardView
    private lateinit var categoryValue: TextView
    private lateinit var loadingWidget: ProgressBar

    private lateinit var bpUserList: MutableList<BPUser>
    private lateinit var bpUserNameList: Array<String>
    private lateinit var bpUserSelected: BPUser
    private lateinit var bpUserListAdapter: ArrayAdapter<Any?>
    private lateinit var bpValues: Triple<Int, Int, Int>

    private var galleryActivityResultLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == RESULT_OK && it.data != null) {
            val galleryPickUri = it.data!!.data
            handlePickedPhoto(galleryPickUri!!)
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        safeContext = context
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        val root: View = binding.root

        firestoreDataViewModel = ViewModelProvider(this)[FirestoreDataViewModel::class.java]

        // UI Binding
        galleryButton       = binding.galleryButton
        captureButton       = binding.captureButton
        retakeButton        = binding.retakeButton
        confirmButton       = binding.confirmButton
        camLiveFeed         = binding.camLiveFeed
        capturedView        = binding.capturedView
        boundingBoxOverlay  = binding.boundingBoxOverlay
        inferenceTimeText   = binding.inferenceTime
        bpUserSelector      = binding.userSelector
        sysValue            = binding.sysValue
        diaValue            = binding.diaValue
        pulValue            = binding.pulValue
        categoryLayout      = binding.resultCategory
        categoryCard        = binding.resultCategoryCard
        categoryValue       = binding.resultCategoryValue
        loadingWidget       = binding.loadingWidget

        // User Selector Setup
        bpUserList = mutableListOf()
        bpUserSelector.onItemSelectedListener = this
        firestoreDataViewModel.bpUserList.observe(viewLifecycleOwner) { item ->
            bpUserList = item
            bpUserNameList = bpUserList.map { it.name }.toTypedArray()
            Log.d(TAG, "User Count: ${bpUserNameList.size}")
            bpUserListAdapter = ArrayAdapter<Any?>(
                safeContext,
                android.R.layout.simple_spinner_item,
                bpUserNameList
            )
            bpUserListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            bpUserSelector.adapter = bpUserListAdapter
        }

        bpValues = Triple(0,0,0)
        isCamPaused = false

        Log.d(TAG, "onCreateView")

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        detector = DigitDetector(safeContext, MODEL_PATH, LABELS_PATH, this)
        detector.setup()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(requireActivity(), REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        galleryButton.setOnClickListener {
            pickPhoto()
        }

        captureButton.setOnClickListener {
            takePhoto()
        }

        retakeButton.setOnClickListener {
            restartCamera()
        }

        confirmButton.setOnClickListener {
            confirmedResult()
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isCamPaused = true
        detector.clear()
        cameraExecutor.shutdown()
        cameraProvider?.unbindAll()
//        detector.clear()
        boundingBoxOverlay.clear()
        _binding = null

        Log.d(TAG, "onDestroyView")
    }

    override fun onPause() {
        super.onPause()
        isCamPaused = true

        Log.d(TAG, "onPause")
    }

    override fun onResume() {
        super.onResume()
        firestoreDataViewModel.getBPUserData()
        if (allPermissionsGranted()){
            startCamera()
        } else {
            val requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()) {
                if (it[Manifest.permission.CAMERA] == true) { startCamera() }
            }
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    // Digit Detector Override
    override fun onEmptyDetect() {
        boundingBoxOverlay.clear()
    }
    override fun onDetect(
        boundingBoxes: List<BoundingBox>,
        inferenceTime: Long,
        bpValues: Triple<Int, Int, Int>
    ) {
        activity?.runOnUiThread {
            val inferenceTimeString = "$inferenceTime ms"
            inferenceTimeText.text = inferenceTimeString
            sysValue.text = if (bpValues.first > 0) bpValues.first.toString() else "-"
            diaValue.text = if (bpValues.second > 0) bpValues.second.toString() else "-"
            pulValue.text = if (bpValues.third > 0) bpValues.third.toString() else "-"
            boundingBoxOverlay.apply {
                setResults(boundingBoxes)
                invalidate()
            }
        }
    }

    // UserAdapter Override
    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        bpUserSelected = bpUserList[position]
        Log.i(TAG, "${bpUserNameList[position]} is selected")
    }
    override fun onNothingSelected(p0: AdapterView<*>?) {
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(safeContext)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            cameraProvider = cameraProviderFuture.get()
            val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")
            val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

            preview = Preview.Builder().build()
            imageCapture = ImageCapture.Builder().build()
            imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(camLiveFeed.display.rotation)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()

            imageAnalysis?.setAnalyzer(cameraExecutor) { imageProxy ->
                if (isCamPaused) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                val bitmapBuffer =
                    Bitmap.createBitmap(
                        imageProxy.width,
                        imageProxy.height,
                        Bitmap.Config.ARGB_8888
                    )
                imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }
                imageProxy.close()
                val matrix = Matrix().apply { postRotate(imageProxy.imageInfo.rotationDegrees.toFloat()) }
                val rotatedBitmap = Bitmap.createBitmap(
                    bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                    matrix, true
                )
                bpValues = detector.detect(rotatedBitmap)
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis)
                preview?.setSurfaceProvider(camLiveFeed.surfaceProvider)
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(safeContext))
    }

    private fun restartCamera() {
        cameraProvider?.unbindAll()
        isCamPaused = false
        startCamera()
        galleryButton.visibility = View.VISIBLE
        captureButton.visibility = View.VISIBLE
        retakeButton.visibility = View.GONE
        confirmButton.visibility = View.GONE
        capturedView.visibility = View.GONE
        categoryLayout.visibility = View.GONE
    }

    private fun takePhoto() {
        loadingWidget.visibility = View.VISIBLE
        isCamPaused = true
        val imageCapture = imageCapture ?: return
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(safeContext),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val bitmapBuffer = imageProxy.toBitmap()
                    imageProxy.close()
                    val matrix = Matrix().apply { postRotate(imageProxy.imageInfo.rotationDegrees.toFloat()) }
                    val rotatedBitmap = Bitmap.createBitmap(
                        bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height,
                        matrix, true
                    )
                    cameraProvider?.unbindAll()
                    loadingWidget.visibility = View.GONE
                    bpValues = detector.detect(rotatedBitmap)
                    capturedView.setImageBitmap(cropBitmap1x1(rotatedBitmap))
                    capturedView.visibility = View.VISIBLE
                    galleryButton.visibility = View.GONE
                    captureButton.visibility = View.GONE
                    retakeButton.visibility = View.VISIBLE
                    confirmButton.visibility = View.VISIBLE
                    val category = firestoreDataViewModel.getRecordCategory(bpValues.first, bpValues.second, bpUserSelected)
                    categoryCard.setCardBackgroundColor(getResultColor(category))
                    categoryValue.text = category
                    categoryLayout.visibility = View.VISIBLE
                }
                override fun onError(exception: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                }
            }
        )
    }

    private fun pickPhoto() {
        loadingWidget.visibility = View.VISIBLE
        isCamPaused = true
        val galleryIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        galleryActivityResultLauncher.launch(galleryIntent)
    }

    private fun handlePickedPhoto(imageUri: Uri) {
        val inputImage = uriToBitmap(imageUri)
        if (inputImage != null) {
            bpValues = detector.detect(inputImage)
            cameraProvider?.unbindAll()
            loadingWidget.visibility = View.GONE
            val resizedBitmap = resizeBitmap1x1(inputImage)
            capturedView.setImageBitmap(resizedBitmap)
            capturedView.visibility = View.VISIBLE
            galleryButton.visibility = View.GONE
            captureButton.visibility = View.GONE
            retakeButton.visibility = View.VISIBLE
            confirmButton.visibility = View.VISIBLE
            val category = firestoreDataViewModel.getRecordCategory(bpValues.first, bpValues.second, bpUserSelected)
            categoryCard.setCardBackgroundColor(getResultColor(category))
            categoryValue.text = category
            categoryLayout.visibility = View.VISIBLE
        }
    }

    private fun confirmedResult() {
//        val sys = bpValues.first
//        val dia = bpValues.second
//        val pul = bpValues.third
        val sys = sysValue.text.toString().toIntOrNull()
        val dia = diaValue.text.toString().toIntOrNull()
        val pul = pulValue.text.toString().toIntOrNull()
        val time = Timestamp.now()
        if (sys == null || dia == null || pul == null || sys <= 0 || dia <= 0 || pul <= 0) {
            Log.i(TAG, "Invalid record data: $sys $dia $pul $time")
            Snackbar.make(requireView(), "Invalid record data, please check again!", Snackbar.LENGTH_SHORT).show()
            return
        }
        Log.i(TAG, "Adding record data: $sys $dia $pul $time")
        firestoreDataViewModel.setBPRecordData(bpUserSelected, sys, dia, pul, time)
        Snackbar.make(requireView(), "New record added successfully", Snackbar.LENGTH_SHORT).show()
        (activity as MainActivity).goToHome()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(safeContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getResultColor(category: String) : Int {
        return when (category) {
            "LOW" -> ContextCompat.getColor(safeContext, R.color.bp_blue)
            "OPTIMAL" -> ContextCompat.getColor(safeContext, R.color.bp_green)
            "NORMAL" -> ContextCompat.getColor(safeContext, R.color.bp_green)
            "HIGH NORMAL: PREHYPERTENSION" -> ContextCompat.getColor(safeContext, R.color.bp_yellow)
            "HIGH: STAGE 1 HYPERTENSION" -> ContextCompat.getColor(safeContext, R.color.bp_orange)
            "HIGH: STAGE 2 HYPERTENSION" -> ContextCompat.getColor(safeContext, R.color.bp_red)
            "VERY HIGH: STAGE 3 HYPERTENSION" -> ContextCompat.getColor(safeContext, R.color.bp_red_dark)
            else -> ContextCompat.getColor(safeContext, R.color.gray)
        }
    }

    private fun uriToBitmap(selectedFileUri: Uri) : Bitmap? {
        try {
            val parcelFileDescriptor =
                safeContext.contentResolver.openFileDescriptor(selectedFileUri, "r")
            val fileDescriptor: FileDescriptor = parcelFileDescriptor!!.fileDescriptor
            val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor.close()
            return image?.let { rotateBitmapIfRequired(it, selectedFileUri) }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }

    private fun rotateBitmapIfRequired(bitmap: Bitmap, uri: Uri): Bitmap {
        val inputStream = safeContext.contentResolver.openInputStream(uri)
        val exifInterface = ExifInterface(inputStream!!)
        val orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        }
        inputStream.close()
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun cropBitmap1x1(bitmap: Bitmap) : Bitmap {
        val cropSize = minOf(bitmap.width, bitmap.width)
        val startX = (bitmap.width - cropSize) / 2
        val startY = (bitmap.height - cropSize) / 2
        return Bitmap.createBitmap(bitmap, startX, startY, cropSize, cropSize)
    }

    private fun resizeBitmap1x1(bitmap: Bitmap) : Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        return Bitmap.createScaledBitmap(bitmap, size, size, false)
    }

    companion object {
        private const val TAG = "Camera"
        private const val REQUEST_CODE_PERMISSIONS = 20
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}