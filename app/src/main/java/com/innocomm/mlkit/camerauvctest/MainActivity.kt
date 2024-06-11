package com.innocomm.mlkit.camerauvctest

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.PointF
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Api
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ResetTv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toComposeRect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.MultiplePermissionsState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceLandmark
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.text.Text
import com.innocomm.mlkit.camerauvctest.ui.theme.CameraUVCTestTheme
import com.innocomm.mlkit.camerauvctest.utils.adjustPoint
import com.innocomm.mlkit.camerauvctest.utils.adjustSize
import com.innocomm.mlkit.camerauvctest.utils.dpToPx
import com.innocomm.mlkit.camerauvctest.utils.drawBounds
import com.innocomm.mlkit.camerauvctest.utils.drawLandmark
import com.innocomm.mlkit.camerauvctest.utils.drawLine2
import com.innocomm.mlkit.camerauvctest.utils.drawTriangle
import com.innocomm.mlkit.camerauvctest.utils.gOffsetX
import com.innocomm.mlkit.camerauvctest.utils.gOffsetY
import com.innocomm.mlkit.camerauvctest.utils.myPose
import com.jiangdg.ausbc.CameraClient
import com.jiangdg.ausbc.camera.bean.PreviewSize
import com.jiangdg.ausbc.widget.AspectRatioSurfaceView2
import kotlinx.coroutines.CoroutineExceptionHandler


class MainActivity : ComponentActivity() {
    companion object {
        const val TAG = "MainActivity"
    }

    val mmyViewModel: myViewModel by viewModels { myViewModel.Factory }
    lateinit var backCallback: OnBackPressedCallback

    @OptIn(ExperimentalPermissionsApi::class, ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.v(TAG, "onCreate()")

        backCallback = onBackPressedDispatcher.addCallback {
            Log.v(TAG, "Back pressed")
            finish()
        }
        //Force restart Activity if crash
        val recomposer = window.decorView.createLifecycleAwareWindowRecomposer(
            CoroutineExceptionHandler { coroutineContext, throwable ->
                throwable.printStackTrace()
                recreate() }, lifecycle)
        window.decorView.compositionContext = recomposer
        //~
        setContent {
            CameraUVCTestTheme {
                HideSystemBars()
                /*if(resources.displayMetrics.widthPixels<resources.displayMetrics.heightPixels) {
                    LockScreenOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                }*/
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RequestMultiplePermissions(
                        permissions = listOf(
                            Manifest.permission.CAMERA,
                            Manifest.permission.ACCESS_NETWORK_STATE,
                            Manifest.permission.ACCESS_WIFI_STATE,
                            Manifest.permission.INTERNET,
                            Manifest.permission.RECORD_AUDIO,
                        )
                    )

                }
            }
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
    ///

    fun hasUSBPermission():Boolean {
        //获取service
        val manager = getSystemService(USB_SERVICE) as UsbManager
        //获取设备列表(一般只有一个,usb 口只有一个)
        val deviceList = manager.deviceList
        val deviceIterator: Iterator<UsbDevice> = deviceList.values.iterator()

        if(deviceList.size>0){
            val device = deviceIterator.next()
            Log.v(TAG,"USB device ${device.deviceName}: "+manager.hasPermission(device))
            return manager.hasPermission(device)
        }
        return false
    }

}

@ExperimentalPermissionsApi
@Composable
fun RequestMultiplePermissions(
    permissions: List<String>,
    deniedMessage: String = "This app requires the camera and access to storage. If it doesn't work, then you'll have to do it manually from the settings.",
    rationaleMessage: String = "To use this app's functionalities, you need to give us the permission.",
) {
    val multiplePermissionsState = rememberMultiplePermissionsState(permissions)

    HandleRequests(
        multiplePermissionsState = multiplePermissionsState,
        deniedContent = { shouldShowRationale ->
            PermissionDeniedContent(
                deniedMessage = deniedMessage,
                rationaleMessage = rationaleMessage,
                shouldShowRationale = shouldShowRationale,
                onRequestPermission = { multiplePermissionsState.launchMultiplePermissionRequest() }
            )
        },
        content = {
            Content(
                text = "PERMISSION GRANTED!",
                showButton = false
            ) {}
        }
    )
}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@ExperimentalPermissionsApi
@Composable
private fun HandleRequests(
    multiplePermissionsState: MultiplePermissionsState,
    deniedContent: @Composable (Boolean) -> Unit,
    content: @Composable () -> Unit
) {
    val viewmodel = (LocalContext.current as MainActivity).mmyViewModel
    var shouldShowRationale by remember { mutableStateOf(false) }
    val result = multiplePermissionsState.permissions.all {
        Log.v(MainActivity.TAG,""+it.permission+" "+it.status.isGranted)
        shouldShowRationale = it.status.shouldShowRationale
        it.status == PermissionStatus.Granted
    }
    if (result) {
        Toast.makeText(LocalContext.current, "Permission granted successfully", Toast.LENGTH_SHORT)
            .show()

        AppBody(viewmodel)

    } else {
        deniedContent(shouldShowRationale)
    }
}
@Composable
fun HideSystemBars() {
    val context = LocalContext.current

    DisposableEffect(Unit) {
        val window = context.findActivity()?.window ?: return@DisposableEffect onDispose {}
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)

        insetsController.apply {
            hide(WindowInsetsCompat.Type.statusBars())
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            insetsController.apply {
                show(WindowInsetsCompat.Type.statusBars())
                show(WindowInsetsCompat.Type.navigationBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            }
        }
    }
}
@Composable
fun LockScreenOrientation(orientation: Int) {
    val context = LocalContext.current
    DisposableEffect(orientation) {
        val activity = context.findActivity() ?: return@DisposableEffect onDispose {}
        val originalOrientation = activity.requestedOrientation
        activity.requestedOrientation = orientation
        onDispose {
            // restore original orientation when view disappears
            activity.requestedOrientation = originalOrientation
        }
    }
}
fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBody(viewmodel: myViewModel) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { AppBar(viewmodel) },
        content = {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                MyApp(viewmodel,it)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBar(viewmodel: myViewModel) {
    val activity = (LocalContext.current as? MainActivity)
    val showMLTypeDlg = remember { mutableStateOf(false) }
    val showPreviewSizeDlg = remember { mutableStateOf(false) }
    val indxAnalyzer by viewmodel.indxAnalyzer.collectAsState()


    TopAppBar(
        colors = TopAppBarDefaults.mediumTopAppBarColors(
           // containerColor = MaterialTheme.colorScheme.primaryContainer,
           // titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if(indxAnalyzer==0){
                    Text(
                        text =  stringResource(R.string.app_name),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    DisplayVersion()
                }else{
                    Text(
                        text = "Analyzer: "+myViewModel.Analyzers[indxAnalyzer],
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = {
                activity?.onBackPressedDispatcher?.onBackPressed()
            }) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Localized description",
                    //tint = MaterialTheme.colorScheme.primary
                )
            }
        },
        actions = {
            IconButton(onClick = {
                showMLTypeDlg.value = true
            }) {
                Icon(
                    imageVector = Icons.Filled.Api,
                    contentDescription = "Api",
                    //tint = MaterialTheme.colorScheme.primary
                )
                if(showMLTypeDlg.value){
                    popupMenuSelectMLType(viewmodel = viewmodel) {
                        showMLTypeDlg.value = false
                    }
                }

            }
            IconButton(onClick = {
                showPreviewSizeDlg.value = true
            }) {
                Icon(
                    imageVector = Icons.Filled.ResetTv,
                    contentDescription = "Resolution",
                    //tint = MaterialTheme.colorScheme.primary
                )
                if(showPreviewSizeDlg.value){
                    popupMenuPreviewSize(viewmodel = viewmodel) {
                        showPreviewSizeDlg.value = false
                    }
                }
            }
        },
    )
}

@Composable
fun DisplayVersion() {
    val context = LocalContext.current
    val app = (context.applicationContext as? myApp)
    // on below line we are creating a column
    Column(
        // on below line we are adding a modifier to it
        modifier = Modifier
            .wrapContentSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // on below line we are creating a variable
        // and storing our version name
        // and version code.
        val version =
            "v." + app?.getPackageVersion()

        Text(
            text = version,
            modifier = Modifier.padding(5.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

    }

}
@ExperimentalPermissionsApi
@Composable
fun PermissionDeniedContent(
    deniedMessage: String,
    rationaleMessage: String,
    shouldShowRationale: Boolean,
    onRequestPermission: () -> Unit
) {
    if (shouldShowRationale) AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = "Permission Request",
                style = TextStyle(
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    fontWeight = FontWeight.Bold
                )
            )
        },
        text = {
            Text(rationaleMessage)
        },
        confirmButton = {
            Button(onClick = onRequestPermission) {
                Text("Give Permission")
            }
        }
    ) else Content(text = deniedMessage, onClick = onRequestPermission)

}

@Composable
fun Content(text: String, showButton: Boolean = true, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(50.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = text, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(12.dp))
        if (showButton) {
            Button(onClick = onClick) {
                Text(text = "Request")
            }
        }
    }
}

@Composable
fun MyApp(viewmodel: myViewModel, paddingValues: PaddingValues) {
    val act = (LocalContext.current as? MainActivity)
    val myText by viewmodel.text.collectAsState()
    val fps by viewmodel.fps.collectAsState()
    val texts by viewmodel.texts.collectAsState()
    val barcodes by viewmodel.barcodes.collectAsState()
    val facemesh by viewmodel.facemesh.collectAsState()
    val faces by viewmodel.faces.collectAsState()
    val detectedObjects by viewmodel.objects.collectAsState()
    val screenSize by viewmodel.screenSize.collectAsState()
    val mypose by viewmodel.poses.collectAsState()
    val myCamClient by viewmodel.camclient.collectAsState()
    val imgSize by viewmodel.imgSize.collectAsState()
    val showSelfieSegment by viewmodel.showSelfieSegment.collectAsState()
    val (showRefresh ,onSetShowRefresh) =  remember { mutableStateOf(!(act?.hasUSBPermission()?:false)) }

    //val screenWidth = remember { mutableStateOf(context.resources.displayMetrics.widthPixels) }
    //val screenHeight = remember { mutableStateOf(context.resources.displayMetrics.heightPixels) }

    LaunchedEffect(Unit) {
        act?.let { viewmodel.initCameraClient(it, imgSize.width, imgSize.height)}
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(color = Color.Black)
            .padding(paddingValues = paddingValues),
        contentAlignment = Alignment.Center
    ) {
        myCamClient.cameraclient?.let {
            UVCCameraPreview(it, viewmodel, paddingValues)
        }

        if(showRefresh) {
            RefreshButton() {
                act?.let {
                    onSetShowRefresh(!it.hasUSBPermission())
                    it.finish()
                    it.startActivity(it.getIntent())
                    if (Build.VERSION.SDK_INT >= 34) {
                        it.overrideActivityTransition(
                            Activity.OVERRIDE_TRANSITION_OPEN,
                            0,
                            0
                        )
                    }else{
                        it.overridePendingTransition(0,0)
                    }
                }
            }
        }

        DrawBarcode( barcodes, imgSize.width, imgSize.height, screenSize.width, screenSize.height )
        DrawRecognizedText( texts, imgSize.width, imgSize.height, screenSize.width, screenSize.height )
        DrawFace(  faces = faces, imgSize.width, imgSize.height, screenSize.width, screenSize.height )
        DrawFaceMesh( faces = facemesh, imgSize.width, imgSize.height, screenSize.width, screenSize.height )
        DrawDetectedObjects(  detectedObjects,imgSize.width, imgSize.height, screenSize.width, screenSize.height )
        DrawPoses(  mypose = mypose,imgSize.width, imgSize.height, screenSize.width, screenSize.height )
        if (showSelfieSegment) DrawDetectedSegmentation(viewmodel)
        Column(Modifier.align(Alignment.BottomCenter)) {
            displayMessage(myText)
        }
        /*Column(
            Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 5.dp)) {
            RadioButtonMLType(viewmodel)
        }
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .padding(horizontal = 5.dp)) {
            RadioButtonPreviewSize(previewsize,viewmodel)
        }*/
        Column(
            Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = 5.dp)) {
            Text(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(3.dp),
                text = "FPS: "+fps,
                color = Color.White,
                fontSize = 30.sp
            )
        }
    }
}
@Composable
fun RefreshButton( onClick: () -> Unit) {
    Box(modifier = Modifier.wrapContentSize()) {
        IconButton(onClick = {
            onClick()
        }) {
            Icon(
                modifier = Modifier.size(200.dp),
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Refresh",
                tint = Color.White
            )
        }

    }
}

@Composable
fun DrawPoses(
    mypose: myPose,
    imageWidth: Int,
    imageHeight: Int,
    screenWidth: Int,
    screenHeight: Int
) {
    if (!mypose.hasVal) return
    val pose = mypose.pose!!

    Canvas(modifier = Modifier.fillMaxSize()) {
        val stroke = 5.0f
        val lineColor = Color.Cyan
        val landmarkColor = Color.Blue
        val poseLandmarks = pose.allPoseLandmarks
        if (poseLandmarks.isEmpty()) return@Canvas

        val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
        val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
        val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
        val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
        val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
        val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
        val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
        val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
        val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
        val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
        val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)

        val leftPinky = pose.getPoseLandmark(PoseLandmark.LEFT_PINKY)
        val rightPinky = pose.getPoseLandmark(PoseLandmark.RIGHT_PINKY)
        val leftIndex = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)
        val rightIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)
        val leftThumb = pose.getPoseLandmark(PoseLandmark.LEFT_THUMB)
        val rightThumb = pose.getPoseLandmark(PoseLandmark.RIGHT_THUMB)
        val leftHeel = pose.getPoseLandmark(PoseLandmark.LEFT_HEEL)
        val rightHeel = pose.getPoseLandmark(PoseLandmark.RIGHT_HEEL)
        val leftFootIndex = pose.getPoseLandmark(PoseLandmark.LEFT_FOOT_INDEX)
        val rightFootIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_FOOT_INDEX)

        drawLine2(
            leftShoulder,
            rightShoulder,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            leftHip,
            rightHip,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )

        // Left body
        drawLine2(
            leftShoulder,
            leftElbow,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            leftElbow,
            leftWrist,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            leftShoulder,
            leftHip,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            leftHip,
            leftKnee,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            leftKnee,
            leftAnkle,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            leftWrist,
            leftThumb,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            leftWrist,
            leftPinky,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            leftWrist,
            leftIndex,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            leftIndex,
            leftPinky,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            leftAnkle,
            leftHeel,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            leftHeel,
            leftFootIndex,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        // Right body
        drawLine2(
            rightShoulder,
            rightElbow,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            rightElbow,
            rightWrist,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            rightShoulder,
            rightHip,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            rightHip,
            rightKnee,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            rightKnee,
            rightAnkle,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            rightWrist,
            rightThumb,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            rightWrist,
            rightPinky,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            rightWrist,
            rightIndex,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            rightIndex,
            rightPinky,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            rightAnkle,
            rightHeel,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )
        drawLine2(
            rightHeel,
            rightFootIndex,
            lineColor,
            stroke,
            imageWidth,
            imageHeight,
            screenWidth,
            screenHeight
        )

        poseLandmarks.forEach {
            val landmark = adjustPoint(
                PointF(it.position.x, it.position.y),
                imageWidth,
                imageHeight,
                screenWidth,
                screenHeight
            )
            drawLandmark(landmark, landmarkColor, 3f)
        }
    }

}

@Composable
fun displayMessage(barcode: String) {
    if (!TextUtils.isEmpty(barcode)) {
        Column(
            Modifier
                .background(color = Color.Black)
                .padding(3.dp)
        ) {

            Text(
                modifier = Modifier
                    .verticalScroll(rememberScrollState()),
                text = barcode,
                color = Color.White,
                fontSize = 30.sp
            )
        }
    }
}

@Composable
fun UVCCameraPreview(
    cameraClient: CameraClient,
    viewmodel: myViewModel,
    paddingValues: PaddingValues
) {
    val context = LocalContext.current
    val act = context as MainActivity
    val topOffset = paddingValues.calculateTopPadding().dpToPx()
    AndroidView(
        modifier = Modifier.onGloballyPositioned {
            viewmodel.setOffsetX(it.positionInRoot().x)
            viewmodel.setOffsetY(it.positionInRoot().y-topOffset )
        },
        factory = { ctx ->
            AspectRatioSurfaceView2(ctx).apply {
                setAspectRatio(viewmodel.lastPreviewSize.width,viewmodel.lastPreviewSize.height)
                this.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Log.i(MainActivity.TAG, "surfaceCreated ")
                        viewmodel.cameraClient = cameraClient
                        cameraClient.openCamera(this@apply)
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        Log.i(MainActivity.TAG, "surfaceChanged " + width + "x" + height+"   "+ act.hasUSBPermission())
                        viewmodel.setScreenSize(width, height)
                        cameraClient.setRenderSize(width, height)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Log.i(MainActivity.TAG, "surfaceDestroyed ")
                        viewmodel.cameraClient = null
                        cameraClient.closeCamera()
                        viewmodel.shouldReInitCamera(context)
                    }
                })
            }
        }
    ) {
    }
}
@Composable
fun DrawFace(
    faces: List<Face>,
    imageWidth: Int,
    imageHeight: Int,
    screenWidth: Int,
    screenHeight: Int
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier = Modifier.fillMaxSize()) {
        faces.forEachIndexed {index, face ->
            val boundingBox = face.boundingBox.toComposeRect()
            val topLeft = adjustPoint(
                PointF(boundingBox.topLeft.x, boundingBox.topLeft.y),
                imageWidth,
                imageHeight,
                screenWidth,
                screenHeight
            )
            if(topLeft.x<0 || topLeft.y <0 ||topLeft.x>screenWidth||topLeft.y>screenHeight){
                Log.v(MainActivity.TAG,"Error " +topLeft.x+"-"+topLeft.y)
                Log.v(MainActivity.TAG,"boundingBox " +boundingBox.topLeft.x+"-"+boundingBox.topLeft.y)
                Log.v(MainActivity.TAG,"screenWidth " +screenWidth+"-"+screenHeight)
                return@forEachIndexed
            }
            val size =
                adjustSize(boundingBox.size, imageWidth, imageHeight, screenWidth, screenHeight)
            drawBounds(topLeft, size, Color.Yellow, 5f)

            face.allContours.forEach {contour ->
                contour.points.forEach{
                    val landmark = adjustPoint(
                        PointF(it.x, it.y),
                        imageWidth,
                        imageHeight,
                        screenWidth,
                        screenHeight
                    )
                    drawLandmark(landmark, Color.Cyan, 3f)
                }
            }

            face.allLandmarks.forEach{
                val landmark = adjustPoint(
                    PointF(it.position.x, it.position.y),
                    imageWidth,
                    imageHeight,
                    screenWidth,
                    screenHeight
                )
                drawLandmark(landmark, Color.Red, 3f)
            }

            drawText(
                textMeasurer = textMeasurer,
                text = getFaceInfo(index,face),
                style = TextStyle(
                    fontSize = 20.sp,
                    color = Color.Yellow,
                    background = Color.Black.copy(alpha = 0.2f)
                ),
                topLeft = Offset(
                    x = topLeft.x+ gOffsetX,
                    y = topLeft.y+ gOffsetY
                )
            )
        }
    }
}

fun getFaceInfo(index:Int,face:Face):String{
    var faceInfoString = "Face $index\n"												// Face area in rectangle
    val bounds = face.boundingBox
    // Face is facing upward
    val rotX = face.headEulerAngleX
    //faceInfoString += "\\nRotation X: $rotX (${if (rotX >= 0) "Facing Upward" else "Facing Down"})"
    // Face is facing to the right of the camera
    val rotY = face.headEulerAngleY
    //faceInfoString += "\\nRotation Y: $rotY (${if (rotY >= 0) "Facing Right" else "Facing Left"})"
    // Face is rotated counter-clockwise relative to the camera
    val rotZ = face.headEulerAngleZ
    //faceInfoString += "\\nRotation Z: $rotZ (${if (rotZ >= 0) "Rotation Counter-Clockwise" else "Facing Rotation Clockwise"})"
    // Landmark
    face.getLandmark(FaceLandmark.LEFT_EAR)?.let {}
    // Contour
    face.getContour(FaceContour.FACE)?.points?.let {}
    // Classification
    val leftEyeOpenProbability = face.leftEyeOpenProbability
    val rightEyeOpenProbability = face.rightEyeOpenProbability
    val smilingProbability = face.smilingProbability
    //Smiling Face
    if((smilingProbability ?: 0f) > 0.3f) {
        faceInfoString += " isSmiling\n"
    }

    //Eyes are open
    if((leftEyeOpenProbability ?: 0F) > 0.9F && (rightEyeOpenProbability ?: 0F) > 0.9F
    ){
        faceInfoString +=  " areEyesOpen\n"
    }

    //Blinking face
    if(((leftEyeOpenProbability ?: 0F) < 0.4 && (leftEyeOpenProbability != 0f)) && ((rightEyeOpenProbability ?: 0F) < 0.4F && (leftEyeOpenProbability != 0f))
    ){
        faceInfoString += " isBlinking\n"
    }

    return faceInfoString.trim()
}
@Composable
fun DrawFaceMesh(
    faces: List<FaceMesh>,
    imageWidth: Int,
    imageHeight: Int,
    screenWidth: Int,
    screenHeight: Int
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        faces.forEach { face ->
            val boundingBox = face.boundingBox.toComposeRect()
            val topLeft = adjustPoint(
                PointF(boundingBox.topLeft.x, boundingBox.topLeft.y),
                imageWidth,
                imageHeight,
                screenWidth,
                screenHeight
            )
            if(topLeft.x<0 || topLeft.y <0 ||topLeft.x>screenWidth||topLeft.y>screenHeight){
                Log.v(MainActivity.TAG,"Error " +topLeft.x+"-"+topLeft.y)
                Log.v(MainActivity.TAG,"boundingBox " +boundingBox.topLeft.x+"-"+boundingBox.topLeft.y)
                Log.v(MainActivity.TAG,"screenWidth " +screenWidth+"-"+screenHeight)
                return@forEach
            }
            val size =
                adjustSize(boundingBox.size, imageWidth, imageHeight, screenWidth, screenHeight)
            drawBounds(topLeft, size, Color.Yellow, 5f)

            face.allPoints.forEach {
                val landmark = adjustPoint(
                    PointF(it.position.x, it.position.y),
                    imageWidth,
                    imageHeight,
                    screenWidth,
                    screenHeight
                )
                drawLandmark(landmark, Color.Cyan, 3f)
            }

            face.allTriangles.forEach { triangle ->
                val points = triangle.allPoints.map {
                    adjustPoint(
                        PointF(it.position.x, it.position.y),
                        imageWidth,
                        imageHeight,
                        screenWidth,
                        screenHeight
                    )
                }
                drawTriangle(points, Color.Cyan, 1f)
            }
        }
    }
}

@Composable
fun DrawDetectedObjects(
    objects: List<DetectedObject>,
    imageWidth: Int,
    imageHeight: Int,
    screenWidth: Int,
    screenHeight: Int
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        objects.forEach {
            val boundingBox = it.boundingBox.toComposeRect()
            val topLeft = adjustPoint(
                PointF(boundingBox.topLeft.x, boundingBox.topLeft.y),
                imageWidth,
                imageHeight,
                screenWidth,
                screenHeight
            )
            val size =
                adjustSize(boundingBox.size, imageWidth, imageHeight, screenWidth, screenHeight)

            drawBounds(topLeft, size, Color.Yellow, 10f)
        }
    }
}

@Composable
fun DrawBarcode(
    objects: List<Barcode>,
    imageWidth: Int,
    imageHeight: Int,
    screenWidth: Int,
    screenHeight: Int
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.fillMaxSize()) {
        objects.forEach {
            it.boundingBox?.let { boundingBox->
                val boundingBox = boundingBox.toComposeRect()
                val topLeft = adjustPoint(
                    PointF(boundingBox.topLeft.x, boundingBox.topLeft.y),
                    imageWidth,
                    imageHeight,
                    screenWidth,
                    screenHeight
                )
                val size =
                    adjustSize(boundingBox.size, imageWidth, imageHeight, screenWidth, screenHeight)

                drawBounds(topLeft, size, Color.Yellow, 10f)

                drawText(
                    textMeasurer = textMeasurer,
                    text = it.displayValue.toString(),
                    style = TextStyle(
                        fontSize = 20.sp,
                        color = Color.Red,
                        background = Color.Black.copy(alpha = 0.2f)
                    ),
                    topLeft = Offset(
                        x = topLeft.x+ gOffsetX,
                        y = topLeft.y-25.sp.toPx()+ gOffsetY
                    )
                )
            }
        }
    }
}

@Composable
fun DrawRecognizedText(
    objects: List<Text>,
    imageWidth: Int,
    imageHeight: Int,
    screenWidth: Int,
    screenHeight: Int
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.fillMaxSize()) {
        objects.forEach {
            it.textBlocks.forEach{

                it.boundingBox?.let {  boundingBox->
                    val boundingBox = boundingBox.toComposeRect()
                    val topLeft = adjustPoint(
                        PointF(boundingBox.topLeft.x, boundingBox.topLeft.y),
                        imageWidth,
                        imageHeight,
                        screenWidth,
                        screenHeight
                    )
                    val size =
                        adjustSize(boundingBox.size, imageWidth, imageHeight, screenWidth, screenHeight)

                    drawBounds(topLeft, size, Color.Yellow, 10f)

                    drawText(
                        textMeasurer = textMeasurer,
                        text = it.text,
                        style = TextStyle(
                            fontSize = 20.sp,
                            color = Color.Red,
                            background = Color.Black.copy(alpha = 0.2f)
                        ),
                        topLeft = Offset(
                            x = topLeft.x+ gOffsetX,
                            y = topLeft.y-25.sp.toPx()+ gOffsetY
                        )
                    )
                }

            }
        }
    }
}

@Composable
fun DrawDetectedSegmentation(
    viewmodel: myViewModel
) {
    val segmask by viewmodel.segmask.collectAsState()

    Image(
        bitmap = segmask.asImageBitmap(),
        contentDescription = "some useful description",
    )
}

@Composable
fun popupMenuSelectMLType(viewmodel: myViewModel,onDone: () -> Unit){
    val radioOptions by  remember { mutableStateOf(myViewModel.Analyzers) }

    DropdownMenu(
        modifier = Modifier.padding(10.dp),
        expanded = true,
        onDismissRequest = { onDone() }
    ) {
        radioOptions.forEachIndexed { index, text ->
            DropdownMenuItem(
                text = {
                    Text(
                        text,
                        fontSize = 20.sp
                    )
                },
                onClick = {
                    viewmodel.setAnalyzer(index)
                    onDone()
                }
            )
        }
    }

}

fun PreviewSize.name():String{
    return this.width.toString()+"x"+this.height.toString()
}

@Composable
fun popupMenuPreviewSize(viewmodel: myViewModel,onDone: () -> Unit){
    val previewsize by viewmodel.previewsize.collectAsState()

    DropdownMenu(
        modifier = Modifier.padding(10.dp),
        expanded = true,
        onDismissRequest = { onDone() }
    ) {
        previewsize.forEachIndexed { index, size ->
            DropdownMenuItem(
                text = {
                    Text(
                        size.name(),
                        fontSize = 20.sp
                    )
                },
                onClick = {
                    onDone()
                    viewmodel.updatePreviewSize(previewsize[index])
                }
            )
        }
    }

}

@Preview(showBackground = true, device = "spec:width=1024dp,height=768dp,dpi=160")
@Composable
fun DefaultPreviewTablet() {
    val viewmodel = myViewModel(myApp())
    CameraUVCTestTheme {
        Box(
            modifier = Modifier.wrapContentSize()
        ) {
            AppBody(viewmodel)
        }
    }
}

@Preview(showBackground = true, device = "spec:width=600dp,height=1024dp,dpi=160")
@Composable
fun DefaultPreviewPhone() {
    val viewmodel = myViewModel(myApp())
    CameraUVCTestTheme {
        Box(
            modifier = Modifier.wrapContentSize()
        ) {
            AppBody(viewmodel)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun Preview_DialogSelectMLType() {
    val viewmodel = myViewModel(myApp())
    CameraUVCTestTheme {
        popupMenuSelectMLType(viewmodel) {

        }
    }
}