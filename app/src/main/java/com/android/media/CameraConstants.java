package com.android.media;


public class CameraConstants {

    public static final int[] CameraIds = {0,1,2,3};
    public static  int[] RECORD_1 = {720,480};

    //播放传输 默认参数配置
    /**
     * 主码流   如果终端设置了参数 就以终端为准为主码流
     */
    public final static int MAIN_VIDEO_WIDTH = 480;
    public final static int MAIN_VIDEO_HIGTH = 320;
    public final static int MAIN_BITRATE = 2 * 1024;//单位K   *1024
    public final static int MAIN_FRAMERATE = 25;


    /**
     * 子码流
     */
    public final static int SUB_VIDEO_WIDTH = 480;//160;//480; 1024
    public final static int SUB_VIDEO_HIGTH = 320;//120;//288;  552
    public final static int SUB_BITRATE = 512;//单位K   *1024
    public final static int SUB_FRAMERATE = 25;

    //录制 默认参数配置
    /**
     * 帧速率
     */
    public final static int FRAMERATE = 25;
    /**
     * 码流率  数值*1024  单位K
     */
    public final static int BITRATE = 3 * 1280*720;
    /**
     * 录制视频 同预览分辨率一致
     */
    public static final int RECORD_VIDEO_WIDTH = 1280;
    public static final int RECORD_VIDEO_HEIGHT = 720;

    // 存储流数据类型 0x01-视频流；0x02-音视频复合流
    public static final int StorageStreamType = 0x02;
    public static final int LiveStreamType = 0x01;

    public final static int SDCard_MinSize = 600;//M 14000

    //摄像头资源释放
    public static final String KEY_CAMERA_RELEASE = "setprop persist.status.check 1";
    //水印 开启
    public static final String KEY_WATERMARK_OPEN = "setprop watermark.dis 0";
    //水印 关闭
    public static final String KEY_WATERMARK_CLOSE = "setprop watermark.dis 1";
    //水印 文件放开权限
    public static final String KEY_WATERMARK_PERMISSION ="setprop persist.water.permission 1";


    //RTP pusher key
    public static final String rtpKey = "$34Q3{jRJQ2RH{26K6j6gsi6K6KU()L6kT36%6j{3{1{iSIR3{hSI{3{j{i{2RJ{j16";

    //音视频实时传输
    public static final String ACTION_AUDIO_TRANSMISSION = "ACTION_AUDIO_TRANSMISSION";
    //音视频录制
    public static final String ACTION_AUDIO_RECORD = "ACTION_AUDIO_RECORD";
    //单向监听
    public static final String ACTION_AUDIO_VOICE_SINGLE = "ACTION_AUDIO_VOICE_SINGLE";
    //双向监听
    public static final String ACTION_AUDIO_VOICE_MULTI = "ACTION_AUDIO_VOICE_MULTI";
    //暂停 重新开始 等控制
    public static final String ACTION_AUDIO_CONTROL = "ACTION_AUDIO_CONTROL";
    //拍照
    public static final String ACTION_PICTURE_TAKE = "ACTION_PICTURE_TAKE";
    //人脸识别
    public static final String ACTION_FACE_RECOGNITION = "ACTION_FACE_RECOGNITION";
    //悬浮框处理
    public static final String ACTION_FlOAT_WINDOWS = "ACTION_FlOAT_WINDOWS";
    //摄像头资源关闭
    public static final String ACTION_CAMERA_CLOSE = "ACTION_CAMERA_CLOSE";

    //人脸识别 结果处理
    public static final String ACTION_FACE_RECOGNITION_REPLY = "ACTION_FACE_RECOGNITION_REPLY";

}
