package com.innocomm.mlkit.camerauvctest

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.google.mlkit.common.model.LocalModel
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetection
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.custom.CustomImageLabelerOptions
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.innocomm.mlkit.camerauvctest.utils.gOffsetX
import com.innocomm.mlkit.camerauvctest.utils.gOffsetY
import com.innocomm.mlkit.camerauvctest.utils.getResizedBitmap
import com.innocomm.mlkit.camerauvctest.utils.maskColorsFromByteBuffer
import com.innocomm.mlkit.camerauvctest.utils.myCameraClient
import com.innocomm.mlkit.camerauvctest.utils.myPose
import com.jiangdg.ausbc.CameraClient
import com.jiangdg.ausbc.callback.IPreviewDataCallBack
import com.jiangdg.ausbc.camera.CameraUvcStrategy
import com.jiangdg.ausbc.camera.bean.CameraRequest
import com.jiangdg.ausbc.camera.bean.PreviewSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.regex.Pattern

data class AnalyzerState(var processing: Boolean, var lastProcessTime: Long, var counter: Int,var lastFPS: Int)

@SuppressLint("MissingPermission")
class myViewModel(val context: Application) : ViewModel() {
    // Define ViewModel factory in a companion object
    companion object {

        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                // Get the Application object from extras
                val application = checkNotNull(extras[APPLICATION_KEY])

                return myViewModel(
                    application
                ) as T
            }
        }

        val Analyzers = arrayOf(
            "BarCode",
            "Face Detection",
            "FaceMesh",
            "Text Recognizer",
            "Image Labeling",
            "Image Labeling Custom",
            "Object Detection",
            "Pose Detection",
            "Selfie Segmentation"
        )

    }
    val analyzerState  = Array(Analyzers.size) { AnalyzerState(false,0,0,0) }
    var cameraClient: CameraClient? = null
    private var mFPSCounter = 0 // the value to count
    private var mFPSTime: Long = 0
    public val selectedAnalyzer = ArrayList<Int>()
    val openAnalyzerDialog = mutableStateOf(false)
    var resetRequest = false
    fun process(data: ByteArray) {
        if (previewSizeList == null) {
            dumpPreViewSize()
        }

        if (SystemClock.uptimeMillis() - mFPSTime > 1000) {
            mFPSTime = SystemClock.uptimeMillis()
            _fps.value = mFPSCounter
            mFPSCounter = 0
            dumpFPS()
        } else {
            mFPSCounter++
        }

        val s = selectedAnalyzer.map { when (it) {
            0 -> detectBarCode(it,data)
            1 -> detectFace(it,data)
            2 -> detectFaceMesh(it,data)
            3 -> detectText(it,data)
            4 -> detectImageLabel(it,data)
            5 -> detectImageLabelCustom(it,data)
            6 -> detectObject(it,data)
            7 -> detectPose(it,data)
            8 -> {
                detectSegmentation(it,data)
                if (!_showSelfieSegment.value) _showSelfieSegment.value = true
            }
        } }.toSet()
        if (s.size==0) {
            if (resetRequest) {
                resetDetectedData()
                resetRequest=false
            }
        }else{
            resetRequest = true
        }
    }

    private val TAG: String = "myViewModel"

    private val digitPattern: Pattern = Pattern.compile("\\d{3,4}")
    //if(digitPattern.matcher(t_str).matches())

    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text.asStateFlow()

    private val _fps = MutableStateFlow(0)
    val fps: StateFlow<Int> = _fps.asStateFlow()

    private val _analyzerFps = MutableStateFlow("")
    val analyzerFps: StateFlow<String> = _analyzerFps.asStateFlow()

    private val _showSelfieSegment = MutableStateFlow(false)
    val showSelfieSegment: StateFlow<Boolean> = _showSelfieSegment.asStateFlow()

    private val _barcodes = MutableStateFlow(emptyList<Barcode>())
    val barcodes: StateFlow<List<Barcode>> = _barcodes.asStateFlow()

    private val _texts = MutableStateFlow(emptyList<Text>())
    val texts: StateFlow<List<Text>> = _texts.asStateFlow()

    private val _faces = MutableStateFlow(emptyList<Face>())
    val faces: StateFlow<List<Face>> = _faces.asStateFlow()

    private val _facemesh = MutableStateFlow(emptyList<FaceMesh>())
    val facemesh: StateFlow<List<FaceMesh>> = _facemesh.asStateFlow()

    private val _objects = MutableStateFlow(emptyList<DetectedObject>())
    val objects: StateFlow<List<DetectedObject>> = _objects.asStateFlow()

    private val _screenSize = MutableStateFlow(Size(0, 0))
    val screenSize: StateFlow<Size> = _screenSize.asStateFlow()

    private val _poses = MutableStateFlow<myPose>(myPose(false, null))
    val poses: StateFlow<myPose> = _poses.asStateFlow()

    private val _camclient = MutableStateFlow<myCameraClient>(myCameraClient(false, null))
    val camclient: StateFlow<myCameraClient> = _camclient.asStateFlow()

    var lastPreviewSize = PreviewSize(640, 480)
    private val _imgSize = MutableStateFlow<PreviewSize>(lastPreviewSize)
    val imgSize: StateFlow<PreviewSize> = _imgSize.asStateFlow()

    private val _previewsize = MutableStateFlow(emptyList<PreviewSize>())
    val previewsize: StateFlow<List<PreviewSize>> = _previewsize.asStateFlow()
    var previewSizeList: ArrayList<PreviewSize>? = null

    private val _segmask = MutableStateFlow(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
    val segmask: StateFlow<Bitmap> = _segmask.asStateFlow()

    private val _benchmarkResult = MutableStateFlow(emptyList<String>())
    val benchmarkResult: StateFlow<List<String>> = _benchmarkResult.asStateFlow()

    private val _camerabusy = MutableStateFlow(false)
    val camerabusy: StateFlow<Boolean> = _camerabusy.asStateFlow()

    //Barcode
    private val optionsBarcode = BarcodeScannerOptions.Builder().build()
    private val scanner by lazy { BarcodeScanning.getClient(optionsBarcode) }

    //Face
    private val optionsFace = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
        .enableTracking()
        .build()

    private val faceDetector by lazy { FaceDetection.getClient(optionsFace) }

    //Face Mesh
    private val meshDetector by lazy { FaceMeshDetection.getClient() }

    //Text Recognizer
    //private val textRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val textRecognizer by lazy {
        TextRecognition.getClient(
            ChineseTextRecognizerOptions.Builder().build()
        )
    }

    //Image Labeling
    private val optionsImageLabeling = ImageLabelerOptions.Builder()
        .setConfidenceThreshold(0.7f)
        .build()
    private val localModel = LocalModel.Builder()
        .setAssetFilePath("2.tflite")
        // or .setAbsoluteFilePath(absolute file path to model file)
        // or .setUri(URI to model file)
        .build()
    private val customImageLabelerOptions = CustomImageLabelerOptions.Builder(localModel)
        .setConfidenceThreshold(0.5f)
        .setMaxResultCount(5)
        .build()

    private val labeler by lazy { ImageLabeling.getClient(optionsImageLabeling) }
    private val labelerCustom by lazy { ImageLabeling.getClient(customImageLabelerOptions) }

    //Object Detection
    private val optionsObject = ObjectDetectorOptions.Builder()
        .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
        //.enableMultipleObjects()
        .enableClassification()
        .build()
    private val detectorObject by lazy { ObjectDetection.getClient(optionsObject) }

    //Pose Detection
    private val optionsPose = PoseDetectorOptions.Builder()
        .setDetectorMode(PoseDetectorOptions.STREAM_MODE)
        .build()
    private val poseDetector by lazy { PoseDetection.getClient(optionsPose) }

    //Selfie Segmentation
    private val optionsSelfieSegment =
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            //.enableRawSizeMask()
            .build()
    private val segmenter by lazy { Segmentation.getClient(optionsSelfieSegment) }

    val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {

            }
        }
    }

    fun dumpFPS(){
        if(selectedAnalyzer.size==0) return
        val result = selectedAnalyzer.asSequence()
            .joinToString(separator = ""){
                Analyzers[it] + ": "+analyzerState[it].lastFPS +"/sec\n"
            }
        Log.v(TAG,""+selectedAnalyzer.size+": "+result)
        _analyzerFps.value = result
    }

    fun processWithControlledFPS(
        analyzerIdx: Int,
        data: ByteArray,
        run: (image: InputImage, cb: (Boolean) -> Unit) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val state  = analyzerState[analyzerIdx]
            if (!state.processing) {
                state.processing = true

                if (SystemClock.uptimeMillis() - state.lastProcessTime > 1000) {
                    state.lastProcessTime = SystemClock.uptimeMillis()
                    state.lastFPS = state.counter
                    //Log.v(TAG,"FPS "+Analyzers[analyzerIdx]+": "+state.lastFPS)
                    state.counter = 0
                } else {
                    state.counter++
                }

                if (selectedAnalyzer.size == 0) {
                    state.processing = false
                    return@launch
                }
                val imageValue = InputImage.fromByteArray(
                    data,
                    /* image width */ lastPreviewSize.width,
                    /* image height */ lastPreviewSize.height,
                    /*rotationDegrees*/ 0,
                    InputImage.IMAGE_FORMAT_NV21 // or IMAGE_FORMAT_YV12
                )
                run(imageValue) {
                    state.processing = false
                }
            }
        }
    }

    fun detectBarCode(idx: Int,data: ByteArray) {
        processWithControlledFPS(idx,data) { imageValue, onDone ->
            try {
                scanner.process(imageValue)
                    .addOnSuccessListener { barcodes ->
                        if (!selectedAnalyzer.contains(idx)) {
                            onDone(false)
                            return@addOnSuccessListener
                        }
                        _barcodes.value = barcodes
                    }.addOnFailureListener { failure ->
                        failure.printStackTrace()
                        Log.v(TAG, "addOnFailureListener: " + failure.toString())
                        onDone(false)
                    }
                    .addOnCompleteListener {
                        //imageProxy.close()
                        onDone(true)
                    }
            } catch (e: Exception) {
                scanner.close()
                Log.v(TAG, "scanner Exception: " + e.toString())
            }
        }
    }

    fun detectText(idx: Int,data: ByteArray) {
        processWithControlledFPS(idx,data) { imageValue, onDone ->
            try {
                textRecognizer.process(imageValue)
                    .addOnSuccessListener { text ->
                        if (!selectedAnalyzer.contains(idx)) {
                            onDone(false)
                            return@addOnSuccessListener
                        }
                        //Log.v(TAG, "Text: " + text.text)
                        val list = ArrayList<Text>()
                        list.add(text)
                        _texts.value = list
                        //_text.value = "Text: " + (text.text)
                    }.addOnFailureListener { failure ->
                        Log.v(TAG, "addOnFailureListener: " + failure.toString())
                        onDone(false)
                    }.addOnCompleteListener {
                        onDone(true)
                    }
            } catch (e: Exception) {
                textRecognizer.close()
                Log.v(TAG, "scanner Exception: " + e.toString())
            }
        }
    }

    fun detectFace(idx: Int,data: ByteArray) {
        processWithControlledFPS(idx,data) { imageValue, onDone ->
            try {
                faceDetector.process(imageValue)
                    .addOnSuccessListener { faces ->
                        if (!selectedAnalyzer.contains(idx)) {
                            onDone(false)
                            return@addOnSuccessListener
                        }

                        _faces.value = faces
                    }.addOnFailureListener { failure ->
                        Log.v(TAG, "addOnFailureListener: " + failure.toString())
                        onDone(false)
                    }.addOnCompleteListener {
                        onDone(true)
                    }
            } catch (e: Exception) {
                faceDetector.close()
                Log.v(TAG, "meshDetector Exception: " + e.toString())
            }
        }

    }

    fun detectFaceMesh(idx: Int,data: ByteArray) {
        processWithControlledFPS(idx,data) { imageValue, onDone ->
            try {
                meshDetector.process(imageValue)
                    .addOnSuccessListener { meshes ->
                        if (!selectedAnalyzer.contains(idx)) {
                            onDone(false)
                            return@addOnSuccessListener
                        }
                        _facemesh.value = meshes
                    }.addOnFailureListener { failure ->
                        Log.v(TAG, "addOnFailureListener: " + failure.toString())
                        onDone(false)
                    }.addOnCompleteListener {
                        onDone(true)
                    }
            } catch (e: Exception) {
                meshDetector.close()
                Log.v(TAG, "meshDetector Exception: " + e.toString())
            }
        }

    }

    fun detectImageLabel(idx: Int,data: ByteArray) {
        processWithControlledFPS(idx,data) { imageValue, onDone ->
            try {
                labeler.process(imageValue)
                    .addOnSuccessListener { labels ->
                        if (!selectedAnalyzer.contains(idx)) {
                            onDone(false)
                            return@addOnSuccessListener
                        }
                        if (labels.size > 0) {
                            val result =
                                labels.joinToString(separator = "\n") { it.text }
                            Log.v(TAG, "ImageLabel: " + result)
                            _text.value = "ImageLabel: " + result
                        }
                    }.addOnFailureListener { failure ->
                        Log.v(TAG, "addOnFailureListener: " + failure.toString())
                        onDone(false)
                    }.addOnCompleteListener {
                        onDone(true)
                    }
            } catch (e: Exception) {
                labeler.close()
                Log.v(TAG, "labeler Exception: " + e.toString())
            }
        }

    }

    fun detectImageLabelCustom(idx: Int,data: ByteArray) {
        processWithControlledFPS(idx,data) { imageValue, onDone ->
            try {
                labelerCustom.process(imageValue)
                    .addOnSuccessListener { labels ->
                        if (!selectedAnalyzer.contains(idx)) {
                            onDone(false)
                            return@addOnSuccessListener
                        }
                        if (labels.size > 0) {
                            val result =
                                labels.joinToString(separator = "\n") { it.text }
                            Log.v(TAG, "ImageLabel: " + result)
                            _text.value = "ImageLabel: " + result
                        }
                    }.addOnFailureListener { failure ->
                        Log.v(TAG, "addOnFailureListener: " + failure.toString())
                        onDone(false)
                    }.addOnCompleteListener {
                        onDone(true)
                    }
            } catch (e: Exception) {
                labelerCustom.close()
                Log.v(TAG, "labelerCustom Exception: " + e.toString())
            }
        }

    }

    fun detectObject(idx: Int,data: ByteArray) {
        processWithControlledFPS(idx,data) { imageValue, onDone ->
            try {
                detectorObject.process(imageValue)
                    .addOnSuccessListener { objects ->
                        if (!selectedAnalyzer.contains(idx)) {
                            onDone(false)
                            return@addOnSuccessListener
                        }
                        if (objects.size > 0) {
                            //Log.v(TAG, "Found Objects: " + objects.size)
                            val list = ArrayList<DetectedObject>()
                            list.addAll(objects)
                            _objects.value = list
                            /*objects.forEach {
                                val result = it.labels.joinToString(separator = "\n") { it.text }
                                Log.v(TAG, "ObjectLabel: " + it.trackingId + "->" + result)
                            }*/
                        }
                    }.addOnFailureListener { failure ->
                        Log.v(TAG, "addOnFailureListener: " + failure.toString())
                        onDone(false)
                    }.addOnCompleteListener {
                        onDone(true)
                    }
            } catch (e: Exception) {
                detectorObject.close()
                Log.v(TAG, "detectorObject Exception: " + e.toString())
            }
        }

    }

    private fun detectPose(idx: Int,data: ByteArray) {
        processWithControlledFPS(idx,data) { imageValue, onDone ->
            try {
                poseDetector.process(imageValue)
                    .addOnSuccessListener { results ->
                        if (!selectedAnalyzer.contains(idx)) {
                            onDone(false)
                            return@addOnSuccessListener
                        }
                        _poses.tryEmit(myPose(true, results))
                    }.addOnFailureListener { failure ->
                        Log.v(TAG, "addOnFailureListener: " + failure.toString())
                        onDone(false)
                    }.addOnCompleteListener {
                        onDone(true)
                    }
            } catch (e: Exception) {
                poseDetector.close()
                Log.v(TAG, "poseDetector Exception: " + e.toString())
            }
        }
    }

    private fun detectSegmentation(idx: Int,data: ByteArray) {
        processWithControlledFPS(idx,data) { imageValue, onDone ->
            try {
                segmenter.process(imageValue)
                    .addOnSuccessListener { segmentationMask ->
                        if (!selectedAnalyzer.contains(idx)) {
                            onDone(false)
                            return@addOnSuccessListener
                        }
                        val bitmap = Bitmap.createBitmap(
                            maskColorsFromByteBuffer(segmentationMask),
                            segmentationMask.width,
                            segmentationMask.height,
                            Bitmap.Config.ARGB_8888
                        )
                        _segmask.value = getResizedBitmap(
                            bitmap,
                            screenSize.value.width,
                            _screenSize.value.height
                        )
                    }.addOnFailureListener { failure ->
                        Log.v(TAG, "addOnFailureListener: " + failure.toString())
                        onDone(false)
                    }.addOnCompleteListener {
                        onDone(true)
                    }
            } catch (e: Exception) {
                segmenter.close()
                Log.v(TAG, "segmenter Exception: " + e.toString())
            }
        }
    }

    fun setScreenSize(width: Int, height: Int) {

        _screenSize.value = Size(width, height)
        // _screenSize.value = Size(context.resources.displayMetrics.widthPixels, context.resources.displayMetrics.heightPixels)
    }

    fun setOffsetX(offset: Float) {
        if (gOffsetX == offset) return
        Log.i(MainActivity.TAG, "onGloballyPositioned X " + offset)
        gOffsetX = offset
    }

    fun setOffsetY(offset: Float) {
        if (gOffsetY == offset) return
        Log.i(MainActivity.TAG, "onGloballyPositioned Y " + offset)
        gOffsetY = offset
    }

    fun resetDetectedData() {
        _text.value = ""
        _barcodes.value = ArrayList<Barcode>()
        _texts.value = ArrayList<Text>()
        _faces.value = ArrayList<Face>()
        _facemesh.value = ArrayList<FaceMesh>()
        _objects.value = ArrayList<DetectedObject>()
        _poses.value = myPose(false, null)
        _showSelfieSegment.value = false
        _analyzerFps.value = ""
    }

    fun dumpPreViewSize() {
        Log.v(TAG, "dumpPreViewSize()")
        viewModelScope.launch(Dispatchers.IO) {
            cameraClient?.getAllPreviewSizes()?.let {
                if (it.size > 0) {
                    val list = ArrayList<PreviewSize>()
                    list.addAll(it)
                    previewSizeList = list
                    _previewsize.value = list
                    list.forEach {
                        Log.v(TAG, "Preview " + it.width + "x" + it.height)
                    }
                }
            }
        }
    }

    fun updatePreviewSize(previewsize: PreviewSize?) {
        Log.v(TAG, "updatePreviewSize() ")
        selectedAnalyzer.clear()
        shouldReinitCamera = true
        previewsize?.let {
            lastPreviewSize = it.copy()
        }
        Log.v(TAG, "updatePreviewSize() " + lastPreviewSize.toString())
        _camclient.value = myCameraClient(false, null)
    }

    var shouldReinitCamera = false
    fun shouldReInitCamera(ctx: Context) {
        if (shouldReinitCamera) {
            shouldReinitCamera = false
            initCameraClient(ctx, lastPreviewSize.width, lastPreviewSize.height)
        }
    }

    fun initCameraClient(ctx: Context, width: Int, height: Int) {
        Log.v(TAG, "initCameraClient: " + width + "x" + height)
        _camerabusy.value = true
        _imgSize.value = PreviewSize(width, height)
        val cameraUvcStrategy = CameraUvcStrategy(ctx)
        val list = cameraUvcStrategy.getUsbDeviceList()
        list?.let {
            it.forEach {
                Log.v(TAG, "UVCCam: " + it.deviceName)
            }
        }

        cameraUvcStrategy.addPreviewDataCallBack(object : IPreviewDataCallBack {
            override fun onPreviewData(data: ByteArray?, format: IPreviewDataCallBack.DataFormat) {
                //Log.i(MainActivity.TAG, "onPreviewData "+data+" "+format)
                if(_camerabusy.value) _camerabusy.value = false
                data?.let {
                    process(it)
                }
            }
        })


        _camclient.value = myCameraClient(true, CameraClient.newBuilder(ctx).apply {
            setEnableGLES(true)
            setRawImage(false)
            setCameraStrategy(cameraUvcStrategy)
            setCameraRequest(
                CameraRequest.Builder()
                    .setFrontCamera(false)
                    .setPreviewWidth(width)
                    .setPreviewHeight(height)
                    .create()
            )
            openDebug(true)

        }.build())
        _fps.value = 0
    }
}