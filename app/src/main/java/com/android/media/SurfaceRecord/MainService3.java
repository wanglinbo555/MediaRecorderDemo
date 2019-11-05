package com.android.media.SurfaceRecord;


import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.usb.UsbManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

import com.android.media.CameraConstants;
import com.android.media.CleanSDService;
import com.android.media.FileOper;
import com.android.media.PathUtils;
import com.android.media.PreviewCallBack.AvcEncoder;
import com.android.media.R;
import com.android.media.SessionLinearLayout;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


/**
 * 摄像头
 * 服务 视频推流 录像 双向对讲 单向监听
 * <p>
 * 接下来处理
 * 1.四路合一
 * 2.业务分离
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
public class MainService3 extends Service {
    private final String TAG = "MainService";
    //判断服务运行
    public static boolean isrun = false;
    private Context context;
    private View view;

    //路径管理工具
    private PathUtils pathUtils;

    private int NumCamera = 4;

    //摄像头
    public Camera[] mCamera = new Camera[NumCamera];
    //    private TextureView textureView; SurfaceView
    private TextureView[] textureView = new TextureView[NumCamera];
    //摄像头id
    public int[] cid = {0, 1, 2, 3};//, 1, 2, 3

    public boolean[] enable = {false, false, false, false};


    //视图管理
    private WindowManager windowManager;
    //视图是否展示 2全屏 1 四宫格  0隐藏
    private int isWindowViewShow = 0;
    private WindowManager.LayoutParams params;
    private LayoutInflater inflater;
    //点  监听返回键
    private SessionLinearLayout layoutPoint;

    //摄像头布局文件
    private int[] lyids = {R.id.ll_1_0, R.id.ll_1_1, R.id.ll_1_2, R.id.ll_2_0, R.id.ll_2_1, R.id.ll_2_2};
    private View[] lys = new View[lyids.length];
    //控制某路缩放
    boolean isAllVisible = true;

    private static MainService3 instance;

    //接收广播 事件处理
    private BroadcastReceiver SYSBr;
    private BroadcastReceiver WinBr;

    //主码流和子码流相关参数
    //摄像头当前分辨率
    private int[] VIDEO_WIDTH = {CameraConstants.RECORD_VIDEO_WIDTH, CameraConstants.RECORD_VIDEO_WIDTH, CameraConstants.RECORD_VIDEO_WIDTH, CameraConstants.RECORD_VIDEO_WIDTH};
    private int[] VIDEO_HIGTH = {CameraConstants.RECORD_VIDEO_HEIGHT, CameraConstants.RECORD_VIDEO_HEIGHT, CameraConstants.RECORD_VIDEO_HEIGHT, CameraConstants.RECORD_VIDEO_HEIGHT};
    private int BITRATE = CameraConstants.SUB_BITRATE;//单位K   *1024
    private int FRAMERATE = CameraConstants.SUB_FRAMERATE;
    private boolean isAudioRecordOpen = CameraConstants.StorageStreamType == 2;

    //设备支持尺寸
    private List<Camera.Size> supportedPictureSizes = new ArrayList<>();

    //当前的码流 0-主码流，1-子码流
    private int[] streamType = {-1, -1, -1, -1};

    //录制视频 0正常 1投诉  2报警 标志
    public static String VideoAlarmTag = "0";
    //录制视频文件单个时长
    private int RecordDuration = 1;
    private static int CountRecord = 1;

    private Camera.PreviewCallback[] previewCallback = new Camera.PreviewCallback[NumCamera];


    //预览画面旋转角度
    private int displayOrientation = 0;

    //屏幕宽高
    private int SCREEN_WIDTH, SCREEN_HIGTH;

    public static MainService3 getInstance() {
        if (instance == null) {
            instance = new MainService3();
        }
        return instance;
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "[摄像头]----[onCreate]----");
        context = MainService3.this;
        instance = this;
        isrun = true;

        //初始化参数配置
        initUtils();
        //初始化广播
        initBroadcastReceiver();
        initWindowBroadcast();
        //窗口布局
        initWindow();


        //初始化摄像头
        initCamera();

        initSdCard();

        view.findViewById(R.id.tv_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "[摄像头]----[关闭画面]----");
                try {
                    BroadCastCloseCamera();
                    stopSelf();
                } catch (Exception e) {
                    Log.d(TAG, "[摄像头]----[关闭画面]----[Error]" + e.getMessage());
                    e.printStackTrace();
                }

            }
        });


        view.findViewById(R.id.tv_start_record).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "[摄像头]----[录音流程]----[开始]----");
                isRecording[0]  =true;
                isRecording[1]  =true;
                isRecording[2]  =true;
                isRecording[3]  =true;
                ThreadRecord(1);
                ThreadRecord(2);
                ThreadRecord(3);
                ThreadRecord(4);
//                new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        startRecorder(1);
//                        startRecorder(2);
//                        startRecorder(3);
//                        startRecorder(4);
//                    }
//                }).start();



//                timer = new Timer();
//                timer.schedule(new TimerTask() {
//                    @Override
//                    public void run() {
//                        handler.sendEmptyMessage(1);
//                    }
//                },3000,30*1000+500);

            }
        });

        view.findViewById(R.id.tv_end_record).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "[摄像头]----[录音流程]----[结束]----");

                if(timer!=null){
                    timer.cancel();
                }

                Intent intent = new Intent(CameraConstants.ACTION_AUDIO_RECORD);
                intent.putExtra("isOpen", false);
                intent.putExtra("logicChannel", 1);
                sendBroadcast(intent);

                intent.putExtra("logicChannel", 2);
                sendBroadcast(intent);

                intent.putExtra("logicChannel", 3);
                sendBroadcast(intent);

                intent.putExtra("logicChannel", 4);
                sendBroadcast(intent);
            }
        });


        view.findViewById(R.id.tv_close_window).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showWindow(0);
            }
        });
    }


    private surfaceRecord[] mSurfaceRecord = new surfaceRecord[NumCamera];
    private static final String SWAPPED_FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(sTexture, vTextureCoord).gbra;\n" +
                    "}\n";

    private void ThreadRecord(int logicChannel) {
        final  int index = logicChannel -1;
        new Thread(new Runnable() {
            @Override
            public void run() {
                mSurfaceRecord[index] = new surfaceRecord(mCamera[index]);
                generateVideoFilename(MediaRecorder.OutputFormat.MPEG_4, index+1);
                mSurfaceRecord[index].prepareEncoder(CameraConstants.RECORD_VIDEO_WIDTH,
                        CameraConstants.RECORD_VIDEO_HEIGHT,
                        CameraConstants.BITRATE,
                        MrTempName[index]);
                mSurfaceRecord[index].makeCurrent();
                mSurfaceRecord[index].prepareSurfaceTexture();

                long startWhen = System.nanoTime();

                //SurfaceTexture st = mStManager.getSurfaceTexture();
                int frameCount = 0;
                while (isRecording[index]) {
                    // Feed any pending encoder output into the muxer.
                    mSurfaceRecord[index].drainEncoder(false);

                    // Switch up the colors every 15 frames.  Besides demonstrating the use of
                    // fragment shaders for video editing, this provides a visual indication of
                    // the frame rate: if the camera is capturing at 15fps, the colors will change
                    // once per second.
                    if ((frameCount % 15) == 0) {
                        String fragmentShader = null;
                        if ((frameCount & 0x01) != 0) {
                            fragmentShader = SWAPPED_FRAGMENT_SHADER;
                        }
                        //mStManager.changeFragmentShader(fragmentShader);
                        mSurfaceRecord[index].changeFragmentShader(fragmentShader);
                    }
                    frameCount++;

                    // Acquire a new frame of input, and render it to the Surface.  If we had a
                    // GLSurfaceView we could switch EGL contexts and call drawImage() a second
                    // time to render it on screen.  The texture can be shared between contexts by
                    // passing the GLSurfaceView's EGLContext as eglCreateContext()'s share_context
                    // argument.
                    mSurfaceRecord[index].awaitNewImage();
                    mSurfaceRecord[index].drawImage();

                    // Set the presentation time stamp from the SurfaceTexture's time stamp.  This
                    // will be used by MediaMuxer to set the PTS in the video.

                    //mInputSurface.setPresentationTime(st.getTimestamp());
                    mSurfaceRecord[index].updatePresentationTime(startWhen);

                    // Submit it to the encoder.  The eglSwapBuffers call will block if the input
                    // is full, which would be bad if it stayed full until we dequeued an output
                    // buffer (which we can't do, since we're stuck here).  So long as we fully drain
                    // the encoder before supplying additional input, the system guarantees that we
                    // can supply another frame without blocking.
                    Log.d(TAG, "sending frame to encoder");
                    mSurfaceRecord[index].swapBuffers();
                }
                mSurfaceRecord[index].drainEncoder(true);
            }
        }).start();
    }

    Timer timer;

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            stoprecorder(1);
            stoprecorder(2);
            stoprecorder(3);
            stoprecorder(4);


            startRecorder(1);
            startRecorder(2);
            startRecorder(3);
            startRecorder(4);
        }
    };

    private void initSdCard() {
        try {
            if (!new File(PathUtils.getInstance().getRoot_path()).exists()) return;
            long freeSize = FileOper.getSDFreeSize(PathUtils.getInstance().getRoot_path());//MB
            long totalSize = FileOper.getSDAllSize(PathUtils.getInstance().getRoot_path());//MB
            Log.d(TAG, "[摄像头]----[内置存储]----[剩余容量]----" + freeSize + "/" + totalSize + "----" + CleanSDService.isCleanSD);
            if (freeSize < CameraConstants.SDCard_MinSize && !CleanSDService.isCleanSD) {//删除数据
                Log.d(TAG, "[摄像头]----[内置存储]----[启动删除服务]----");
                Intent intent = new Intent(this, CleanSDService.class);
                startService(intent);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "[摄像头]----[onStartCommand]----" + isWindowViewShow);

        return super.onStartCommand(intent, flags, startId);
    }


    private int[] textureViewIds = {R.id.texture_preview, R.id.texture_preview2, R.id.texture_preview3, R.id.texture_preview4};


    /**
     * 初始化摄像头
     */
    private void initCamera() {
        if (NumCamera == 2) lys[3].setVisibility(View.GONE);

        for (int i = 0; i < NumCamera; i++) {
            textureView[i] = view.findViewById(textureViewIds[i]);

            final int index = i;
            textureView[i].setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
                @Override
                public void onSurfaceTextureAvailable(final SurfaceTexture surface, int width, int height) {
                    Log.d(TAG, "[摄像头]----[initCamera]----[onSurfaceTextureAvailable]----打开摄像头id:" + cid[index]);

                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                mCamera[index] = Camera.open(cid[index]);
                            } catch (Exception e) {
                                Log.e(TAG, "[摄像头]----[initCamera]----打开摄像头失败" + e.getMessage());
                                e.printStackTrace();
                            }
                            try {
                                if (mCamera[index] == null) return;

                                mCamera[index].setPreviewTexture(surface);
                                supportedPictureSizes = mCamera[index].getParameters().getSupportedPictureSizes();//getSupportedPreviewSizes 不含720P
                                boolean b = mCamera[index].getParameters().isVideoStabilizationSupported();
                                Log.i(TAG, "[摄像头]----[摄像头初始化]----[onSuccess 摄像头参数]----[防抖动支持]：" + b);
                                Log.i(TAG, "[摄像头]----[摄像头初始化]----当前预览分辨率=" + VIDEO_WIDTH[index] + ":" + VIDEO_HIGTH[index]);

                                mCamera[index].setPreviewCallbackWithBuffer(previewCallback[index]);

                                setADBWH(index, VIDEO_WIDTH[index], VIDEO_HIGTH[index]);

                                mCamera[index].setDisplayOrientation(displayOrientation);//配置角度
                                mCamera[index].startPreview();// 开始预览

//                                mCamera[index].setPreviewCallback(previewCallback[index]);


                                enable[index] = true;


                            } catch (Exception e) {
                                Log.e(TAG, "[摄像头]----[initCamera]----[onSurfaceTextureAvailable]----设置摄像头失败:" + e.getMessage());
                                e.printStackTrace();
                                BroadCastCloseCamera();
//                            closeCamera();
                                stopSelf();
                            }
                        }
                    }).start();

                }

                @Override
                public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                    Log.d(TAG, "[摄像头]----[onSurfaceTextureSizeChanged]----[摄像头变化]----" + width + ":" + height);

                    try {
                        if (mCamera[index] == null) return;
//                        mCamera[index].setPreviewDisplay(holder);
//                        mCamera[index].startPreview();
                    } catch (Exception e) {
                        Log.e(TAG, "[摄像头]----[onSurfaceTextureSizeChanged]----[摄像头变化]----报错" + e.getMessage());
                        e.printStackTrace();
                    }
                }

                @Override
                public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                    Log.d(TAG, "[摄像头]----[initCamera]----[onSurfaceTextureDestroyed]----");
//                closeCamera();
                    BroadCastCloseCamera();
                    return false;
                }

                @Override
                public void onSurfaceTextureUpdated(SurfaceTexture surface) {

                }
            });

        }


    }


    /**
     * 指令设置摄像头分辨率
     */
    private void setADBWH(int index, int video_width, int video_higth) {
        Camera.Size previewSize = mCamera[index].getParameters().getPreviewSize();
        Log.i(TAG, "[摄像头]----[摄像头初始化]----[onSuccess 摄像头参数]----[宽：高]前----" + previewSize.width + ":" + previewSize.height);
        Log.i(TAG, "[摄像头]----[摄像头初始化]----[onSuccess 摄像头参数]----[宽：高]前----" + mCamera[index].getParameters().get("video-size"));
        VIDEO_WIDTH[index] = video_width;
        VIDEO_HIGTH[index] = video_higth;

        isAollowSize(index);

        Camera.Parameters parameters = mCamera[index].getParameters();
        parameters.setPreviewSize(VIDEO_WIDTH[index], VIDEO_HIGTH[index]);//配置大小
//      parameters.setPictureSize(VIDEO_WIDTH, VIDEO_HIGTH);//配置大小
        parameters.setRecordingHint(true);

//        parameters.setAntibanding(parameters.ANTIBANDING_AUTO);
//        parameters.set("recording-hint", "true");
        String vstabSupported = parameters.get("video-stabilization-supported");

        if (parameters.getVideoStabilization()) {
            parameters.setVideoStabilization(true);
        }
        parameters.set("video-size", VIDEO_WIDTH[index] + "x" + VIDEO_HIGTH[index]);

//        parameters.set("video-cds-mode", "on");
//        parameters.set("video-tnr-mode", "on");

        // Set continuous autofocus.
//        List<String> supportedFocus = parameters.getSupportedFocusModes();
//        if (isSupported(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO, supportedFocus)) {
//            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
//        }

        parameters.setPreviewFrameRate(FRAMERATE);
        //设置对焦模式  3种
        //照片格式
        parameters.setPreviewFormat(ImageFormat.NV21);
        mCamera[index].setParameters(parameters);

        Camera.Size previewSize2 = mCamera[index].getParameters().getPreviewSize();
        Log.i(TAG, "[摄像头]----[摄像头初始化]----[onSuccess 摄像头参数]----[宽：高]后----" + previewSize2.width + ":" + previewSize2.height);
        Log.i(TAG, "[摄像头]----[摄像头初始化]----[onSuccess 摄像头参数]----[宽：高]后----" + mCamera[index].getParameters().get("video-size"));

        mCamera[index].addCallbackBuffer(new byte[VIDEO_WIDTH[index] * VIDEO_HIGTH[index] * 3 / 2]);

    }

    private boolean isSupported(String value, List<String> supported) {
        return supported == null ? false : supported.indexOf(value) >= 0;
    }

    /**
     * 查看设置
     */
    private boolean isAollowSize(int index) {
        if (supportedPictureSizes == null || supportedPictureSizes.size() == 0) return false;
        boolean b = false;
        for (int i = 0; i < supportedPictureSizes.size(); i++) {
            Camera.Size csize = supportedPictureSizes.get(i);
            if (VIDEO_WIDTH[index] == csize.width && VIDEO_HIGTH[index] == csize.height) {
                Log.d(TAG, "[摄像头]----[initCamera]----[设备支持尺寸]----" + csize.width + "----" + csize.height);
                b = true;
            }
//            BcmLog.d(TAG, "[摄像头]----[initCamera]----[设备支持尺寸]----" + csize.width + "----" + csize.height);
        }
        //查找出最适合的 差异最小的
        if (!b) getBestSupportedSize(index);
        return b;
    }

    /**
     * 找出最贴近的分辨率设置
     */
    private void getBestSupportedSize(int index) {
        Camera.Size[] tempSizes = supportedPictureSizes.toArray(new Camera.Size[0]);
        Arrays.sort(tempSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size o1, Camera.Size o2) {
                if (o1.width > o2.width) {
                    return -1;
                } else if (o1.width == o2.width) {
                    return o1.height > o2.height ? -1 : 1;
                } else {
                    return 1;
                }
            }
        });
        supportedPictureSizes = Arrays.asList(tempSizes);

        Camera.Size bestSize = supportedPictureSizes.get(0);
        float previewViewRatio = (float) VIDEO_WIDTH[index] / (float) VIDEO_HIGTH[index];

        if (previewViewRatio > 1) {
            previewViewRatio = 1 / previewViewRatio;
        }
        //屏幕旋转角度
//        boolean isNormalRotate = (additionalRotation % 180 == 0);
        for (Camera.Size s : supportedPictureSizes) {
//            if (isNormalRotate) {
            if (Math.abs((s.height / (float) s.width) - previewViewRatio) < Math.abs(bestSize.height / (float) bestSize.width - previewViewRatio)) {
                bestSize = s;
            }
//            } else {
//                if (Math.abs((s.width / (float) s.height) - previewViewRatio) < Math.abs(bestSize.width / (float) bestSize.height - previewViewRatio)) {
//                    bestSize = s;
//                }
//            }
        }
        VIDEO_WIDTH[index] = bestSize.width;
        VIDEO_HIGTH[index] = bestSize.height;
        Log.i(TAG, "[摄像头]----[分辨率]----[最适合尺寸]----" + VIDEO_WIDTH[index] + "----" + VIDEO_HIGTH[index]);
    }

    /**
     * 释放摄像头资源
     */
    public void closeCamera() {
        for (int i = 0; i < NumCamera; i++) {
            stoprecorder(i + 1);
            if (mediaRecorder[i] != null) {
                mediaRecorder[i].release();
                mediaRecorder[i] = null;
            }
            if (mCamera[i] != null) {
                mCamera[i].setPreviewCallback(null);
                mCamera[i].setPreviewCallbackWithBuffer(null);
                mCamera[i].stopPreview();
                mCamera[i].release();
                mCamera[i] = null;
            }
        }

    }

    /**
     * 释放摄像头资源
     */
    private void BroadCastCloseCamera() {
        Intent intent = new Intent(CameraConstants.ACTION_CAMERA_CLOSE);
        sendBroadcast(intent);

    }

    int count = 0;
    private AvcEncoder[] mAvcEncoder = new AvcEncoder[NumCamera];

    /**
     * 初始化相关工具 配置参数
     */
    private void initUtils() {


        pathUtils = PathUtils.getInstance();

        Log.e(TAG, "[摄像头]----[录音流程]----[文件时长]----" + RecordDuration);

        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        //具体返回对象是WindowMangerIml类
        Display display = wm.getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        Log.d(TAG, "[摄像头]----[initWindow]----[屏幕大小]----" + point.x + "----" + point.y);

        SCREEN_WIDTH = point.x;
        SCREEN_HIGTH = point.y;

        for (int i = 0; i < NumCamera; i++) {
            final int index = i;

            //CamcorderProfile.get(mBack2Profile).videoBitRate

            previewCallback[i] = new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    //第一步  Nv21ToYuv420SP
                    camera.addCallbackBuffer(data);

//                    Log.d(TAG,"----------------------------------------");
                    if (isRecording[index]) {
                        if (data != null) {
                            Log.d(TAG, "----MainActivity+h264 start ,data ok");
                            if(mAvcEncoder[index]!=null){
                                mAvcEncoder[index].offerEncoder(data);
                            }
                        }
                    } else {
                        if (mAvcEncoder[index] != null) {
                            Log.d(TAG,"----onPreviewFrame isRecording false");
                            mAvcEncoder[index].stop();
                            mAvcEncoder[index].offerEncoder(data);
                            if(mAvcEncoder[index].isStoped()){
                                mAvcEncoder[index] = null;
                            }
                        }
                    }


                }
            };
        }

    }

    /**
     * 显示窗口
     */
    private void initWindow() {

        Log.d(TAG, "[摄像头]----[initWindow]----[窗口显示开始]");
        windowManager = (WindowManager) getApplication().getSystemService(getApplication().WINDOW_SERVICE);

        params = new WindowManager.LayoutParams();
        params.x = 0;
        params.y = 0;
        params.width = 2;//WindowManager.LayoutParams.MATCH_PARENT;//Constants.UPLOAD_VIDEO_WIDTH;//
        params.height = 2;//WindowManager.LayoutParams.MATCH_PARENT;//Constants.UPLOAD_VIDEO_HEIGHT;//

        if (Build.VERSION.SDK_INT >= 26) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
        //做测试 注释掉
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
        params.format = PixelFormat.RGBA_8888;//

        //布局
        if (inflater == null) {
            inflater = LayoutInflater.from(context);
        }
        //监测回退
        layoutPoint = (SessionLinearLayout) inflater.inflate(R.layout.layout_point, null);
        layoutPoint.setDispatchKeyEventListener(new SessionLinearLayout.DispatchKeyEventListener() {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && isWindowViewShow != 0) {
                    //最大化时候 返回键 可关闭服务
//                    Toast.makeText(context, "隐藏窗口", Toast.LENGTH_SHORT).show();
                    Log.d(TAG, "[摄像头]----[initWindow]----[隐藏窗口]----" + isWindowViewShow + ":" + 1);
                    showWindow(0);
                    return true;
                }
                return false;
            }
        });

        view = inflater.inflate(R.layout.common_camera2, null);
        windowManager.addView(view, params);
        view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

//        showWindow(0);
        Log.d(TAG, "[摄像头]----[initWindow]----[窗口显示结束]");

        for (int i = 0; i < lyids.length; i++) {
            lys[i] = view.findViewById(lyids[i]);
        }
        for (int i = 0; i < lys.length; i++) {
            if (i == 0 || i == 3) continue;
            lys[i].setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        Log.e(TAG, "[摄像头]----[initWindow]----[窗口切换]----[触发]" + v.getId());
                        int loginChannel = -1;
                        if (v.getId() == lyids[1]) {
                            loginChannel = 1;
                        } else if (v.getId() == lyids[2]) {
                            loginChannel = 2;
                        } else if (v.getId() == lyids[4]) {
                            loginChannel = 3;
                        } else if (v.getId() == lyids[5]) {
                            loginChannel = 4;
                        }
                        doScaleWindow(!isAllVisible, loginChannel);
                    } catch (Exception e) {
                        Log.e(TAG, "[摄像头]----[initWindow]----[窗口切换]----[报错]" + e.getMessage());
                    }

                }
            });
        }
    }

    /**
     * 窗口处理  3种效果
     * 最大化 全屏  2
     * 最小化 四路四宫格  1
     * 隐藏  1px显示  0
     */
    public void showWindow(int b) {
        try {
            if (b == 2) {//最大化  显示一个  隐藏其他三个
                Log.d(TAG, "[摄像头]----[initWindow]----[窗口最大化]");
//            if(isWindowViewShow)return;

                params.x = 0;
                params.y = 0;
                params.width = WindowManager.LayoutParams.MATCH_PARENT;//WindowManager.LayoutParams.MATCH_PARENT;//Constants.UPLOAD_VIDEO_WIDTH;//
                params.height = WindowManager.LayoutParams.MATCH_PARENT;//WindowManager.LayoutParams.MATCH_PARENT;//Constants.UPLOAD_VIDEO_HEIGHT;//
                //最大化，不要设置LayoutParams.FLAG_NOT_FOCUSABLE，才能拦截返回键
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;

                windowManager.updateViewLayout(view, params);

                //最大化，添加layout，才能拦截返回键，长宽为1，才不会挡住界面
                params.width = 2;
                params.height = 2;

                if (layoutPoint != null && layoutPoint.isShown()) {
                    windowManager.updateViewLayout(layoutPoint, params);
                } else if (layoutPoint != null && !layoutPoint.isShown()) {
                    windowManager.addView(layoutPoint, params);
                }

            } else if (b == 1) {//最小化  四小宫格
                Log.d(TAG, "[摄像头]----[initWindow]----[窗口最小化]");
                showWindow(2);
                doScaleWindow(true, -1);

            } else {
//                if (isWindowViewShow == 0) return;
                Log.d(TAG, "[摄像头]----[initWindow]----[窗口隐藏]");

                params.x = 1;
                params.y = 1;

                params.width = 2;//1行2路分至少1px
                params.height = 2;
                //最小化到后台，需要设置LayoutParams.FLAG_NOT_FOCUSABLE，才能取消对返回键的拦截，并且移除layout
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED;
                if (layoutPoint != null && layoutPoint.isShown()) {
                    windowManager.removeView(layoutPoint);
                }

                windowManager.updateViewLayout(view, params);

            }
            isWindowViewShow = b;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(TAG, "[摄像头]----[initWindow]----[Error]----[窗口处理出错]" + e.getMessage());
        }

    }

    /**
     * 缩放某路视频预览画面
     */
    public void doScaleWindow(boolean isAllVisible, int logicChannel) {
        Log.d(TAG, "[摄像头]----[doScaleWindow]----[缩放某路]----" + isAllVisible + ":" + logicChannel);
        this.isAllVisible = isAllVisible;
        if (isAllVisible) {//四小宫格
            for (int i = 0; i < lys.length; i++) {
                lys[i].setVisibility(View.VISIBLE);
            }
            if (NumCamera == 2) {
                lys[3].setVisibility(View.GONE);
            }
        } else {//显示一路
            if (logicChannel == -1) return;
            for (int i = 0; i < lys.length; i++) {
                lys[i].setVisibility(View.GONE);
            }
            if (logicChannel == 1) {
                lys[1].setVisibility(View.VISIBLE);
                lys[0].setVisibility(View.VISIBLE);
            } else if (logicChannel == 2) {
                lys[2].setVisibility(View.VISIBLE);
                lys[0].setVisibility(View.VISIBLE);
            } else if (logicChannel == 3) {
                lys[4].setVisibility(View.VISIBLE);
                lys[3].setVisibility(View.VISIBLE);
                Log.d(TAG, "[摄像头]----[doScaleWindow]----[缩放某路]----" + lys[0].getVisibility() + ":" + lys[5].getVisibility());
            } else if (logicChannel == 4) {
                lys[5].setVisibility(View.VISIBLE);
                lys[3].setVisibility(View.VISIBLE);
            }
        }
    }


    /**
     * 广播 统一接收处理
     */
    private void initBroadcastReceiver() {
        SYSBr = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                int logicChannel = intent.getIntExtra("logicChannel", -1);
                Log.d(TAG, "[摄像头]----[接收广播]----" + action + "----通道----" + logicChannel);

                int index = logicChannel - 1;
                if (action.equals(CameraConstants.ACTION_AUDIO_RECORD)) {
                    //自动录像
                    boolean isOpen = intent.getBooleanExtra("isOpen", false);
                    Log.d(TAG, "[摄像头]----[录像]----[状态]----" + logicChannel + ":" + isOpen + ":" + isRecording);
                    if (isOpen && !isRecording[index]) {
                        Log.d(TAG, "[摄像头]----[录像]----[开始]");
                        startRecorder(logicChannel);
                        return;
                    }
                    if (!isOpen && isRecording[index]) {
                        Log.d(TAG, "[摄像头]----[录像]----[结束]");

                        stoprecorder(logicChannel);
                        return;
                    }
                }


            }
        };
        IntentFilter localIntentFilter = new IntentFilter();
        localIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        localIntentFilter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        localIntentFilter.addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);

        //增加  音视频实时传输 录制
        localIntentFilter.addAction(CameraConstants.ACTION_AUDIO_TRANSMISSION);
        localIntentFilter.addAction(CameraConstants.ACTION_AUDIO_RECORD);
        localIntentFilter.addAction(CameraConstants.ACTION_AUDIO_VOICE_MULTI);
        localIntentFilter.addAction(CameraConstants.ACTION_AUDIO_VOICE_SINGLE);
        localIntentFilter.addAction(CameraConstants.ACTION_AUDIO_CONTROL);
        localIntentFilter.addAction(CameraConstants.ACTION_PICTURE_TAKE);
        localIntentFilter.addAction(CameraConstants.ACTION_FACE_RECOGNITION);

        registerReceiver(SYSBr, localIntentFilter);


    }


    /**
     * 广播 统一接收处理 窗口变化 传参修改等
     */
    private void initWindowBroadcast() {

        WinBr = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (CameraConstants.ACTION_FlOAT_WINDOWS.equals(action)) {
                    int windshow = intent.getIntExtra("window_1", -1);
                    if (windshow == -1) return;
                    if (windshow == 2) {
                        showWindow(windshow);
                        int logicChannel = intent.getIntExtra("logicChannel", 1);
                        doScaleWindow(false, logicChannel);
                    } else if (windshow == 1) {
                        showWindow(windshow);
                    } else if (windshow == 0) {
                        showWindow(windshow);
                    }
                    Log.d(TAG, "[摄像头]----[悬浮窗]----变化：" + windshow);
                } else if (CameraConstants.ACTION_CAMERA_CLOSE.equals(action)) {
                    closeCamera();
                    stopSelf();
                    Log.d(TAG, "[摄像头]----[关闭资源]----变化：" + 1);
                }
            }
        };
        IntentFilter localIntentFilter = new IntentFilter();
        localIntentFilter.addAction(CameraConstants.ACTION_FlOAT_WINDOWS);
        localIntentFilter.addAction(CameraConstants.ACTION_CAMERA_CLOSE);

        registerReceiver(WinBr, localIntentFilter);

    }


    /*************************录音视频部分开始************************/

    //录像部分参数
    private MediaRecorder[] mediaRecorder = new MediaRecorder[NumCamera];
    private boolean[] isRecording = {false, false, false, false};
    private String[] MrTempName = new String[NumCamera];
    //文件属性保存
    private ContentValues[] contentValues = new ContentValues[NumCamera];

    /**
     * 开始录像
     */
    public void startRecorder(final int logicChannel) {

        final int index = logicChannel - 1;
        if (mCamera[index] == null) return;

        try {
            generateVideoFilename(MediaRecorder.OutputFormat.MPEG_4, logicChannel);
            mAvcEncoder[index] = new AvcEncoder(CameraConstants.RECORD_VIDEO_WIDTH,
                    CameraConstants.RECORD_VIDEO_HEIGHT,
                    CameraConstants.FRAMERATE,
                    CameraConstants.BITRATE,
                    MrTempName[index]);
            isRecording[index] = true;

        } catch (Exception e) {
            Log.e(TAG, "[摄像头]----[录像流程]----[开启录像]----过程报错:" + e.getMessage());
            isRecording[index] = false;
//            closeCamera();
            BroadCastCloseCamera();
            e.printStackTrace();
        }


    }

    /**
     * 录像 基本元素 配置  增加报警 投诉标志
     */
    private void generateVideoFilename(int outputFileFormat, int logicChannel) {
        int index = logicChannel - 1;
        File mFile;
        try {
            String time = new SimpleDateFormat("yyMMddHHmmss")
                    .format(Calendar.getInstance().getTime()); // 获取系统当前时间
            mFile = CreateText(pathUtils.getAudio_path());
            String title = String.format("%d-%s-%d-%s", logicChannel, time, isAudioRecordOpen && index == 0 ? 2 : 1, VideoAlarmTag);
            String filename = title + convertOutputFormatToFileExt(outputFileFormat);
            File file = new File(mFile, filename);
            String path = file.getPath();
            String tmpPath = path;// + ".tmp";
            String mime = convertOutputFormatToMimeType(outputFileFormat);
            contentValues[index] = new ContentValues(4);
            contentValues[index].put(MediaStore.Video.Media.TITLE, title);
            contentValues[index].put(MediaStore.Video.Media.DISPLAY_NAME, filename);
            contentValues[index].put(MediaStore.Video.Media.MIME_TYPE, mime);
            contentValues[index].put(MediaStore.Video.Media.DATA, path);

            Log.d(TAG, "[摄像头]----[录像流程]----[录像输出参数]----title:" + title);
            Log.d(TAG, "[摄像头]----[录像流程]----[录像输出参数]----filename:" + filename);
            Log.d(TAG, "[摄像头]----[录像流程]----[录像输出参数]----mime:" + mime);
            Log.d(TAG, "[摄像头]----[录像流程]----[录像输出参数]----path:" + path);

            MrTempName[index] = tmpPath;
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    /**
     * 设置报警标志  需要重置
     */
    public void setVideoAlarmTag(String VideoAlarmTag) {
        this.VideoAlarmTag = VideoAlarmTag;
    }

    private String convertOutputFormatToFileExt(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return ".mp4";
        }
        return ".3gp";
    }

    public String convertOutputFormatToMimeType(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return "video/mp4";
        }
        return "video/3gpp";
    }

    private File CreateText(String gg) throws IOException {
        File file = new File(gg);
        if (!file.exists()) {
            try {
                file.mkdirs();
            } catch (Exception e) {
                // TODO: handle exception
            }
        }
        File dir = new File(gg);
        if (!dir.exists()) {
            try {
                dir.createNewFile();
            } catch (Exception e) {
            }
        }
        return dir;
    }

    /**
     * 根据摄像头id停止录像
     */
    private void stoprecorder(int logicChannel) {
        int index = logicChannel - 1;
        try {
            if (mCamera[index] != null) {
                Log.d(TAG, "[摄像头]----[录像流程]----[停止录像]");
                addVideo(MrTempName[index], contentValues[index]);
                isRecording[index] = false;
            }
        } catch (Exception e) {
            Log.d(TAG, "[摄像头]----[录像流程]----[停止录像]----[Error]----" + e.getMessage());
            e.printStackTrace();
        }

    }

    private void addVideo(final String path, final ContentValues values) {
        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    String finalName = values.getAsString(MediaStore.Video.Media.DATA);
                    String title = values.getAsString(MediaStore.Video.Media.TITLE);
                    if (!VideoAlarmTag.equals(title.split("-")[3])) {
                        finalName = finalName.replace(title.split("-")[3] + ".mp4", VideoAlarmTag + ".mp4");
                    }
                    Log.e("----", title.split("-")[3] + ".mp4" + "----" + VideoAlarmTag + ".mp4");
                    Log.e("----", path + "----" + finalName);
                    new File(path).renameTo(new File(finalName));

                } catch (Exception e) {

                }
            }
        });

    }

    /**
     * 设置录音文件时长 单位分钟
     *
     * @param Duration
     */
    public void setRecordDuration(int Duration) {
        RecordDuration = Duration;
    }

    /*************************录音视频部分结束************************/


    @Override
    public void onDestroy() {
        Log.d(TAG, "[摄像头]----[onDestroy]----");
        isrun = false;
        if (SYSBr != null) {
            unregisterReceiver(SYSBr);
        }
        if (WinBr != null) {
            unregisterReceiver(WinBr);
        }

        closeCamera();
        if (view != null) {
            windowManager.removeView(view);
            view = null;
        }
        super.onDestroy();

    }


}
