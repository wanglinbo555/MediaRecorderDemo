package com.android.media;


import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CleanSDService extends IntentService {
    private String TAG = "CleanSDService";

    public CleanSDService() {
        super("CleanSDService");
    }

    public CleanSDService(String name) {
        super(name);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "CleanSDService----onCreate");
    }

    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        Log.d(TAG, "CleanSDService----onStart");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "CleanSDService----onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "CleanSDService----onDestroy");
        super.onDestroy();
    }

    //是否正在清除sd卡
    public static boolean isCleanSD = false;
    //是否正在清除sd卡中lock文件
    private boolean isCleanLock = false;

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "CleanSDService----onHandleIntent");
        long freeSize = FileOper.getSDFreeSize(PathUtils.getInstance().getRoot_path());//MB
        long totalSize = FileOper.getSDAllSize(PathUtils.getInstance().getRoot_path());//MB
        Log.d(TAG, "[摄像头]----[内置存储]----[剩余容量]----" + freeSize + "/" + totalSize);
        if (freeSize < CameraConstants.SDCard_MinSize && !isCleanSD) {//删除数据
            isCleanSD = true;
            try {
                int islock = isCleanLock ? 1 : 0;
                List<File> mp4List = PathUtils.getInstance().SearchMp4File(0L, System.currentTimeMillis(), -1, -1, -1, islock);
                List<File> wavList = PathUtils.getInstance().SearchWavFile(0L, System.currentTimeMillis(), -1, islock);
                List<File> picList = PathUtils.getInstance().SearchPictureFile(0L, System.currentTimeMillis(), -1, -1);

                sortList(mp4List);
                sortList(wavList);
                sortList(picList);

                int count1 = 0;
                int count2 = 0;
                int count3 = 0;

                //lastModified 按最后修改时间为序
                for (int i = 0; i < mp4List.size(); i++) {//int i = mp4List.size()-1;i>=0;i--
                    File f = mp4List.get(i);
                    if (f.getName().contains("-lock") && !isCleanLock) continue;
                    if (count1 < 8 && f.exists()) {
                        Log.e(TAG, "[摄像头]----[内置存储]----[删除容量]----视频删除：" + f.getName());
                        f.delete();
                    } else {
                        break;
                    }
//                    Thread.sleep(50);
                    count1++;
                    if (count1 >= mp4List.size()) break;
                }
                Log.d(TAG, "[摄像头]----[内置存储]----[删除容量]----视频完毕：" + mp4List.size());
                for (int i = 0; i < wavList.size(); i++) {
                    File f = wavList.get(i);
                    if (f.getName().contains("-lock") && !isCleanLock) continue;
                    if (count2 < 8 && f.exists()) {
                        f.delete();
                    } else {
                        break;
                    }
//                    Thread.sleep(50);
                    count2++;
                    if (count2 >= wavList.size()) break;
                }
                Log.d(TAG, "[摄像头]----[内置存储]----[删除容量]----音频完毕：" + wavList.size());
                for (int i = 0; i < picList.size(); i++) {
                    File f = picList.get(i);
                    if (f.getName().contains("-lock") && !isCleanLock) continue;
                    if (count3 < 8 && f.exists()) {
                        f.delete();
                    } else {
                        break;
                    }
//                    Thread.sleep(50);
                    count3++;
                    if (count3 >= picList.size()) break;
                }
                Log.d(TAG, "[摄像头]----[内置存储]----[删除容量]----图片完毕：" + picList.size());
                if (count1 + count2 + count3 < 4) {
                    //lock 文件太多开始删除lock文件
                    isCleanLock = true;
                } else {
                    isCleanLock = false;
                }
                Log.d(TAG, "[摄像头]----[内置存储]----[删除容量]----删除数目" + (count1 + count2 + count3));
                isCleanSD = false;

//                Thread.sleep(5000);

            } catch (Exception e) {
                isCleanSD = false;
                e.printStackTrace();
            }

        }


    }

    private void sortList(List<File> mp4List) {
        Collections.sort(mp4List, new Comparator<File>() {
            @Override
            public int compare(File o1, File o2) {
                long diff = o1.lastModified() - o2.lastModified();
                if (diff > 0) {
                    return 1;
                } else if (diff < 0) {
                    return -1;
                }
                return 0;
            }
        });
    }


}
