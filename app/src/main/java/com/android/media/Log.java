package com.android.media;


public class Log {
    private static boolean b = true;

    public static void e(String TAG, String tex) {
        if(b){
            android.util.Log.e(TAG, tex);
        }

    }

    public static void i(String TAG, String tex) {
        if(b){
            android.util.Log.i(TAG, tex);
        }

    }

    public static void d(String TAG, String tex) {
        if(b){
            android.util.Log.d(TAG, tex);
        }

    }
}
