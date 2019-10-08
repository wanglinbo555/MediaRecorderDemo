package com.android.media;


import android.content.Context;
import android.os.Environment;
import android.os.storage.StorageManager;
import android.text.TextUtils;
import android.util.Log;


import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class PathUtils {
    private static final String TAG = "MainService";

    private String root_path = "/BCM";
    private String media_path = "/Video/";
    private String audio_path = "/Audio/";
    private String picture_path = "/Picture/";

    public String sd_path = "";

    private static PathUtils instance;

    /**
     * 获取唯一的instance
     *
     * @return
     */
    public static PathUtils getInstance() {
        if (instance == null) {
            instance = new PathUtils();
        }
        return instance;
    }

    private PathUtils() {

    }

    public void initPath(Context mContext) {
        try {
            getExtSDCardPath(mContext, true);
//            boolean b11 = new File(sd_path + "/Android/data/" + mContext.getPackageName() + "/aaa").mkdirs();
//            boolean b11 = new File(sd_path+"/Android/data/aa").mkdirs();
            boolean b1 = new File(getRoot_path()).mkdirs();
            boolean b2 = new File(getMedia_path()).mkdirs();
            boolean b3 = new File(getAudio_path()).mkdirs();
            boolean b4 = new File(getPicture_path()).mkdirs();
//            BcmLog.e("asda", "----" + b11 + b1 + b2 + b3 + b4);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取外置SD卡根路径
     *
     * @param mContext
     * @param is_removable 是否存在外置SD卡
     * @return
     */
    private String getExtSDCardPath(Context mContext, boolean is_removable) {
//        mContext.getExternalFilesDir(null).getAbsolutePath();

        StorageManager storagerManager = (StorageManager) mContext.getSystemService(Context.STORAGE_SERVICE);
        Class<?> storageVolume = null;
        try {
            storageVolume = Class.forName("android.os.storage.StorageVolume");
            Method getVolumeList = storagerManager.getClass().getMethod("getVolumeList");
            Method getPath = storageVolume.getMethod("getPath");
            Method isRemovable = storageVolume.getMethod("isRemovable");
            Object result = getVolumeList.invoke(storagerManager);
            final int length = Array.getLength(result);
            for (int i = 0; i < length; i++) {
                Object storageVolumeElement = Array.get(result, i);
                String path = (String) getPath.invoke(storageVolumeElement);
                boolean removable = (Boolean) isRemovable.invoke(storageVolumeElement);
                if (is_removable == removable) {
//                    sd_path = path;//需要授权
                    sd_path = path+"/Android/data/" + mContext.getPackageName();
                    return sd_path;
                }
            }
        } catch (NoSuchMethodException e1) {
            e1.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return sd_path;
    }

    public String getRoot_path() {
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + root_path;
        if (!TextUtils.isEmpty(sd_path)) {
            path = sd_path + root_path;
        }
        return path;
    }

    public String getMedia_path() {
        String path = getRoot_path() + media_path;
//        if(!new File(media_path).exists()){
//            new File(media_path).mkdirs();
//        }
        Log.e("PathUtils", "----" + path);
        return path;
    }

    public String getAudio_path() {
        String path = getRoot_path() + audio_path;
        Log.e("PathUtils", "----" + path);
        return path;
    }

    public String getPicture_path() {
        String path = getRoot_path() + picture_path;
        Log.e("PathUtils", "----" + path);
        return path;
    }

    /**
     * 查询出符合该时间段的 视频或音视频   1-190730183429-2-lock.mp4
     */
    public synchronized List<File> SearchPictureFile(long stime, long etime, int cameraid, int reason) {
        //视频的命名规则 是cameraid+“-”+时间long
        File f = new File(getPicture_path());
        List<File> filedata = new ArrayList<>();
        Log.i(TAG, "查询照片文件 = " + "--路径--" + getPicture_path());
        if (f.exists()) {
            try {
                File[] fs = f.listFiles();
                Log.i(TAG, "查询照片文件 = " + "--开始--");
                if (fs != null && fs.length > 0) {
                    for (int i = 0; i < fs.length; i++) {
                        File file = fs[i];
                        String name = file.getName();
                        String[] str = name.split("-");
                        if (str.length < 3) continue;//命名过滤   1-190730183429-2.Mp4
                        Log.i(TAG, "查询照片文件 = " + name + "----");
                        if (name.endsWith("jpg")) {
                            int cid = Integer.parseInt(str[0]);
                            int ctype = Integer.parseInt(str[2].replace(".jpg", ""));
                            if (cid == cameraid || cameraid == -1) {//cameraid == -1 搜索全部  通道过滤
                                if (ctype == reason || reason == -1) {// 过滤
                                    //文件开始记录时间
                                    String ctime = str[1].replace(".jpg", "");
                                    long sftime = DateUtil.dateToLong(ctime, "yyMMddHHmmss");
                                    //文件结束记录时间（修改时间）
                                    long eftime = file.lastModified();

                                    Log.i(TAG, "查询照片文件 = " + sftime + "----" + eftime);
                                    //判断文件结束时间与开始时间 结束时间比较
                                    boolean flag = false;
                                    if (sftime >= stime && sftime < etime) {
                                        flag = true;
                                    }
                                    if (eftime > stime && eftime <= etime) {
                                        flag = true;
                                    }
                                    if (flag) {
                                        filedata.add(file);
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return filedata;
    }

    /**
     * 查询出符合该时间段的 视频或音视频   1-190730183429-2-0-lock.mp4
     */
    public synchronized List<File> SearchMp4File(long stime, long etime, int cameraid) {
        return SearchMp4File(stime, etime, cameraid, -1);
    }

    /**
     * 查询出符合该时间段的视频   2：音视频，1：视频，-1：视频或音视频   1-190730183429-2-lock.mp4
     */
    public synchronized List<File> SearchMp4File(long stime, long etime, int cameraid, int type) {
        return SearchMp4File(stime, etime, cameraid, type, -1);
    }

    /**
     * 查询出符合该时间段的视频   2：音视频，1：视频，-1：视频或音视频   1-190730183429-2-lock.mp4
     */
    public synchronized List<File> SearchMp4File(long stime, long etime, int cameraid, int type, int tag) {
        return SearchMp4File(stime, etime, cameraid, type, tag, -1);
    }

    /**
     * 查询出符合该时间段的视频   2：音视频，1：视频，-1：视频或音视频  islock 0:没lock 1：有lock -1：所有  1-190730183429-2-lock.mp4
     */
    public synchronized List<File> SearchMp4File(long stime, long etime, int cameraid, int type, int tag, int islock) {

        //视频的命名规则 是cameraid+“-”+时间long
        File f = new File(getMedia_path());
        List<File> filedata = new ArrayList<>();
//        BcmLog.i(TAG, "查询录像文件 = " + "--路径--" + getMedia_path());
        if (f.exists()) {
            try {
                File[] fs = f.listFiles();
//                BcmLog.i(TAG, "查询录像文件 = " + "--开始--");
                if (fs != null && fs.length > 0) {
                    for (int i = 0; i < fs.length; i++) {
                        File file = fs[i];
                        String name = file.getName();
                        String[] str = name.split("-");
                        if (str.length < 4) continue;//命名过滤   1-190730183429-2.Mp4
//                        BcmLog.i(TAG, "查询录像文件 = " + name + "----");
                        if (name.contains("-lock") && islock == 0) continue;//锁 过滤
                        if (!name.contains("-lock") && islock == 1) continue;//锁 过滤
                        if (name.endsWith("mp4")) {
                            int cid = Integer.parseInt(str[0]);
                            int ctype = Integer.parseInt(str[2].replace(".mp4", ""));
                            int ctag = Integer.parseInt(str[3].replace(".mp4", ""));
                            if (cid == cameraid || cameraid == -1) {//cameraid == -1 搜索全部  通道过滤
                                if (ctype == type || type == -1) {//有声音 无声过滤
                                    if (ctag == tag || tag == -1) {//报警标志
                                        //文件开始记录时间
                                        String ctime = str[1].replace(".mp4", "");
                                        long sftime = DateUtil.dateToLong(ctime, "yyMMddHHmmss");
                                        //文件结束记录时间（修改时间）
                                        long eftime = file.lastModified();
//                                        BcmLog.i(TAG, "查询录像文件 = " + sftime + "----" + eftime);
                                        //判断文件结束时间与开始时间 结束时间比较
                                        boolean flag = false;
                                        if (sftime >= stime && sftime < etime) {
                                            flag = true;
                                        }
                                        if (eftime > stime && eftime <= etime) {
                                            flag = true;
                                        }
                                        if (flag) {
                                            filedata.add(file);
                                        }

                                    }

                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG, "[查询录像]----[符合条件]----文件数目：" + filedata.size());
        return filedata;
    }

    /**
     * 查询出符合该时间段的视频    并且锁定  文件名后面加（-lock）
     */
    public synchronized void LockMp4File(long stime, long etime, int cameraid) {
        try {
            List<File> filedata = SearchMp4File(stime, etime, cameraid);
            for (File file : filedata) {
                if (file.getName().contains("lock")) continue;
                //加锁操作
                String rename = file.getAbsolutePath().replace(".mp4", "-lock.mp4");
                boolean b = file.renameTo(new File(rename));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 查询出符合该时间段的录音文件   0：正常录音；1：乘客不满意（投诉）；2：报警录音  0-190730183429-lock.wav
     */
    public synchronized List<File> SearchWavFile(long stime, long etime, int reason) {
        return SearchWavFile(stime, etime, reason, -1);
    }

    /**
     * 查询出符合该时间段的录音文件   0：正常录音；1：乘客不满意（投诉）；2：报警录音
     * islock 0:没lock 1：有lock -1：所有
     * 0-190730183429-lock.wav
     */
    public synchronized List<File> SearchWavFile(long stime, long etime, int reason, int islock) {

        //视频的命名规则 是cameraid+“-”+时间long
        File f = new File(getAudio_path());
        List<File> filedata = new ArrayList<>();
        Log.i(TAG, "查询录音文件 = " + "--路径--" + getAudio_path());
        if (f.exists()) {
            try {
                File[] fs = f.listFiles();
                Log.i(TAG, "查询录音文件 = " + "--开始--");
                if (fs != null && fs.length > 0) {
                    for (int i = 0; i < fs.length; i++) {
                        File file = fs[i];
                        String name = file.getName();
                        String[] str = name.split("-");
                        if (str.length < 2) continue;//命名过滤
                        if (name.contains("-lock") && islock == 0) continue;//锁 过滤
                        if (!name.contains("-lock") && islock == 1) continue;//锁 过滤
                        Log.i(TAG, "查询录音文件 = " + name + "----");
                        if (name.endsWith("wav")) {
                            int cid = Integer.parseInt(str[0]);
                            if (cid == reason || reason == -1) {//cameraid == -1 搜索全部  通道过滤
                                //文件开始记录时间
                                String ctime = str[1].replace(".wav", "");
                                long sftime = DateUtil.dateToLong(ctime, "yyMMddHHmmss");
                                //文件结束记录时间（修改时间）
                                long eftime = file.lastModified();

                                Log.i(TAG, "查询录音文件 = " + sftime + "----" + eftime);
                                //判断文件结束时间与开始时间 结束时间比较
                                boolean flag = false;
                                if (sftime >= stime && sftime < etime) {
                                    flag = true;
                                }
                                if (eftime > stime && eftime <= etime) {
                                    flag = true;
                                }
                                if (flag) {
                                    filedata.add(file);
                                }

                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return filedata;
    }

    /**
     * 查询出符合该时间段的音频    并且锁定  文件名后面加（-lock）
     */
    public synchronized void LockWavFile(long stime, long etime, int reason) {
        try {
            List<File> filedata = SearchWavFile(stime, etime, reason);
            for (File file : filedata) {
                if (file.getName().contains("lock")) continue;
                //加锁操作
                String rename = file.getAbsolutePath().replace(".wav", "-lock.wav");
                file.renameTo(new File(rename));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 查询出符合该时间段的 某通道号视频或音视频 音频
     */
    public synchronized List<File> SearchAllFile(long stime, long etime, int cameraid) {
        //视频的命名规则 是cameraid+“-”+时间long
        File f = new File(getMedia_path());
        List<File> filedata = SearchMp4File(stime, etime, cameraid);
        List<File> filedata2 = SearchWavFile(stime, etime, -1);
        filedata.addAll(filedata2);
        return filedata;
    }
}
