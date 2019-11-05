package com.android.media.SurfaceView;


import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.usb.UsbManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.provider.MediaStore;
import android.support.annotation.RequiresApi;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;

import com.android.media.CameraConstants;
import com.android.media.CleanSDService;
import com.android.media.FileOper;
import com.android.media.Log;
import com.android.media.PathUtils;
import com.android.media.R;
import com.android.media.SessionLinearLayout;
import com.libyuv.util.YuvUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


/**
 * 摄像头
 * 服务 视频推流 录像 双向对讲 单向监听
 * <p>
 * 接下来处理
 * 1.四路合一
 * 2.业务分离
 */
@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR1)
public class MainService2 extends Service {
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
    private SurfaceView[] textureView = new SurfaceView[NumCamera];
    //摄像头id
    public int[] cid = {0, 1, 2, 3};//

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

    private static MainService2 instance;

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


    ExecutorService fixedThreadPool = null;//线程池
    ExecutorService ComThreadPool = null;//线程池

    public static MainService2 getInstance() {
        if (instance == null) {
            instance = new MainService2();
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
        context = MainService2.this;
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

        fixedThreadPool = Executors.newFixedThreadPool(4);
        ComThreadPool = Executors.newFixedThreadPool(5);


        view.findViewById(R.id.tv_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "[摄像头]----[关闭画面]----");
                try {
                    BroadCastCloseCamera();
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
//                new Handler().postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        startRecorder(1);
//                    }
//                },1000);
//                new Handler().postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        startRecorder(2);
//                    }
//                },15*1000);
//                new Handler().postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        startRecorder(3);
//                    }
//                },30*1000);
//                new Handler().postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        startRecorder(4);
//                    }
//                },45*1000);
                ComThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        startRecorder(1);
                    }
                });
                ComThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        startRecorder(2);
                    }
                });
                ComThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        startRecorder(3);
                    }
                });
                ComThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        startRecorder(4);
                    }
                });

            }
        });

        view.findViewById(R.id.tv_end_record).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "[摄像头]----[录音流程]----[结束]----");


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

        view.findViewById(R.id.tv_photo).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isPhoto = true;
            }
        });

    }


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
            textureView[i].getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(final SurfaceHolder holder) {
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

                                mCamera[index].setPreviewDisplay(holder);
                                supportedPictureSizes = mCamera[index].getParameters().getSupportedPictureSizes();//getSupportedPreviewSizes 不含720P
                                boolean b = mCamera[index].getParameters().isVideoStabilizationSupported();
                                Log.i(TAG, "[摄像头]----[摄像头初始化]----[onSuccess 摄像头参数]----[防抖动支持]：" + b);
                                Log.i(TAG, "[摄像头]----[摄像头初始化]----当前预览分辨率=" + VIDEO_WIDTH[index] + ":" + VIDEO_HIGTH[index]);

//                                mCamera[index].setPreviewCallbackWithBuffer(previewCallback[index]);

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
                            }
                        }
                    }).start();
                }

                @Override
                public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
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
                public void surfaceDestroyed(SurfaceHolder holder) {
                    Log.d(TAG, "[摄像头]----[initCamera]----[onSurfaceTextureDestroyed]----");
//                closeCamera();
                    BroadCastCloseCamera();
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

        parameters.set("video-size", VIDEO_WIDTH[index] + "x" + VIDEO_HIGTH[index]);

        //设置对焦模式  3种
        //照片格式
        parameters.setPreviewFormat(ImageFormat.NV21);
        mCamera[index].setParameters(parameters);

        Camera.Size previewSize2 = mCamera[index].getParameters().getPreviewSize();
        Log.i(TAG, "[摄像头]----[摄像头初始化]----[onSuccess 摄像头参数]----[宽：高]后----" + previewSize2.width + ":" + previewSize2.height);
        Log.i(TAG, "[摄像头]----[摄像头初始化]----[onSuccess 摄像头参数]----[宽：高]后----" + mCamera[index].getParameters().get("video-size"));

//        mCamera[index].addCallbackBuffer(new byte[VIDEO_WIDTH[index] * VIDEO_HIGTH[index] * 3 / 2]);

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

    boolean isPhoto = false;

//    YuvUtil yuvUtil = null;

    /**
     * 初始化相关工具 配置参数
     */
    private void initUtils() {

//        yuvUtil = new YuvUtil();

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
            previewCallback[i] = new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    //第一步  Nv21ToYuv420SP
                    camera.addCallbackBuffer(data);
                    if (index == 0) {
                        Log.e(TAG, "!!!!!");
                    }
                    //帧图片 缩放处理
                    if (isPhoto && index == 0 && data != null) {
                        isPhoto = false;
                        final byte[] da = data.clone();
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Log.e("-----", "-----帧画面缩放处理");
                                    byte[] bytes = new byte[640*480*3/2];
//                                    byte[] bytes = new byte[1280 * 720 * 3 / 2];
                                    YuvUtil.yuvCompress(da, VIDEO_WIDTH[index], VIDEO_HIGTH[index], bytes, 480, 640, 0, 0, false);

                                    byte[] bytes2 = new byte[640*480*3/2];
                                    YuvUtil.yuvI420ToNV21(bytes,480, 640,bytes2);

                                    doByteToImage(System.currentTimeMillis()+"",bytes2);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    Log.e("-----", "-----" + e.getMessage());
                                }

                            }
                        }).start();

                    }


                }
            };
        }

    }

    /**
     * 数组转成图片文件
     */
    private void doByteToImage(String imageId, byte[] data) {
        try {
            long aaa = System.currentTimeMillis();
            File saveFile = new File(pathUtils.getPicture_path() + imageId + ".jpg");

            int w = 640;
            int h = 480;
            int q = 50;
            //图片饱和度等处理 700ms
            if (q == 100) {

//                Bitmap photo = nv21ToBitmap.nv21ToBitmap(data, VIDEO_WIDTH[index], VIDEO_HIGTH[index]);
//                photo = Bitmap.createScaledBitmap(photo, w, h, true);
//
//                FileOutputStream outputStream = new FileOutputStream(saveFile);
//                photo.compress(Bitmap.CompressFormat.JPEG, q, outputStream);//保留原图像80%的品质，压缩20%
//
//                outputStream.flush();
//                outputStream.close();
//
//                if (photo != null && !photo.isRecycled()) {
//                    photo.recycle();
//                    photo = null;
//                }
            } else {//80ms

                //图片保存 不做处理
                FileOutputStream filecon = new FileOutputStream(saveFile);
                YuvImage image = new YuvImage(data,
                        ImageFormat.NV21, w, h,
                        null);

                image.compressToJpeg(
                        new Rect(0, 0, w, h),
                        q, filecon);   // 将NV21格式图片，以质量70压缩成Jpeg，并得到JPEG数据流
                filecon.close();
            }

            long bbb = System.currentTimeMillis();

        } catch (Exception e) {
            e.printStackTrace();
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

        view = inflater.inflate(R.layout.common_camera3, null);
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
                    showWindow(0);
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
            int frameRate = CameraConstants.FRAMERATE;
            int bitRate = CameraConstants.BITRATE;

            if (isRecording[index]) return;
            isRecording[index] = true;

            mCamera[index].unlock();
            if (mediaRecorder[index] == null) {
                mediaRecorder[index] = new MediaRecorder();
                Log.e(TAG, "[摄像头]----[录像流程]----[mediaRecorder]----未创建");
            } else {
                Log.e(TAG, "[摄像头]----[录像流程]----[mediaRecorder]----已创建");
            }
            mediaRecorder[index].reset();
            mediaRecorder[index].setCamera(mCamera[index]);
            //参数设置 顺序不能改变
            mediaRecorder[index].setVideoSource(MediaRecorder.VideoSource.CAMERA);
            mediaRecorder[index].setAudioSource(MediaRecorder.AudioSource.DEFAULT);//MIC CAMCORDER
            //设置audio的编码格式
//            if (isAudioRecordOpen && index == 0) {//保证唯一1通道有声
//                //设定录音来源于同方向的相机麦克风相同，若相机无内置相机或无法识别，则使用预设的麦克风
//                mediaRecorder[index].setAudioSource(MediaRecorder.AudioSource.DEFAULT);//MIC CAMCORDER
//            }
            //1 T3 2 一甲丙益后视镜  3 有方后视镜
//            mediaRecorder[index].setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//            mediaRecorder[index].setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            //音 视频 混合
            if (isAudioRecordOpen && index == 0) {
//                mediaRecorder[index].setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            }
//            mediaRecorder[index].setVideoEncodingBitRate(bitRate);

            CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_720P);
            profile.videoFrameWidth = 1280;
            profile.videoFrameHeight =  720;
            //录像和预览要一致
//            mediaRecorder[index].setVideoSize(VIDEO_WIDTH[index], VIDEO_HIGTH[index]);
            Log.d(TAG, "[摄像头]----[录像流程]----[setVideoSize]----" + VIDEO_WIDTH[index] + ":" + VIDEO_HIGTH[index]);
            //注意：在某些具有自动帧速率的设备上，这将设置
            //最大帧速率，而不是恒定的帧速率。实际帧速率
            //将根据照明条件变化。
//            mediaRecorder[index].setVideoFrameRate(frameRate);

            mediaRecorder[index].setProfile(profile);

            mediaRecorder[index].setOnErrorListener(new MediaRecorder.OnErrorListener() {
                @Override
                public void onError(MediaRecorder mr, int what, int extra) {
                    Log.d(TAG, "[摄像头]----[录像流程]----[setOnErrorListener]----[Error]----" + what + ":" + extra);
                    //先停止掉录制
                    if (what == MediaRecorder.MEDIA_ERROR_SERVER_DIED) {
                        //MediaRecorder.MEDIA_ERROR_SERVER_DIED--100，说明mediaService死了，需要释放MediaRecorder
                        stoprecorder(logicChannel);
                        isRecording[index] = false;
                    }
                }
            });

            generateVideoFilename(MediaRecorder.OutputFormat.MPEG_4, logicChannel);
            mediaRecorder[index].setOutputFile(MrTempName[index]);
            Log.d(TAG, "[摄像头]----[录像流程]----[录像输出路径]----" + MrTempName[index] + ":" + CountRecord);

            mediaRecorder[index].setMaxDuration(1 * 60 * 1000);
            mediaRecorder[index].setOnInfoListener(new MediaRecorder.OnInfoListener() {
                @Override
                public void onInfo(MediaRecorder mr, int what, int extra) {
                    if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                        Log.d(TAG, "[摄像头]----[录像流程]----[录像达到规定时长]----");
                        fixedThreadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                stoprecorder(logicChannel);
                                startRecorder(logicChannel);
                            }
                        });
                    }
                }
            });

            mediaRecorder[index].setPreviewDisplay(textureView[index].getHolder().getSurface());

            mediaRecorder[index].prepare();
            mediaRecorder[index].start();

            Log.d(TAG, "[摄像头]----[录像流程]----[开启录像]----完毕");
            if (index == 0) {
                initSdCard();
            }

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
            mFile = CreateText(pathUtils.getMedia_path());
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
                if (mediaRecorder[index] != null) {
                    try {
                        mediaRecorder[index].setOnErrorListener(null);
                        mediaRecorder[index].setOnInfoListener(null);
//                        mediaRecorder[index].setPreviewDisplay(null);
                        mediaRecorder[index].stop();
                        mediaRecorder[index].reset();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    Log.d(TAG, "[摄像头]----[录像流程]----[停止录像]");
                    mCamera[index].lock();
                    addVideo(MrTempName[index], contentValues[index]);
                    isRecording[index] = false;
                }
            }
        } catch (Exception e) {
            Log.d(TAG, "[摄像头]----[录像流程]----[停止录像]----[Error]----" + e.getMessage());
            e.printStackTrace();
        }

    }

    private void addVideo(final String path, final ContentValues values) {
        ComThreadPool.execute(new Runnable() {
            @Override
            public void run() {//ComThreadPool
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
