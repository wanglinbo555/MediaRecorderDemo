package com.android.media.PreviewCallBack;

/**
 * Created by xa on 2018/3/27.
 */

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.nio.ByteBuffer;
import java.util.Date;

public class AvcEncoder {

    private static final String TAG = "AvcEncoder";
    private MediaCodec mediaCodec;
    private MediaFormat mediaFormat;
    private MediaMuxer mediaMuxer;
    private int videoTrackIndex;
    private String ouputPath = "/sdcard/preview_recorder.mp4";

    private boolean mMuxerStarted;
    private MediaCodec.BufferInfo mBufferInfo;
    private boolean endOfStream = true;
    private boolean stoped = false;

    int m_width;
    int m_height;
    byte[] m_info = null;

    private int mColorFormat;
    private MediaCodecInfo codecInfo;
    private static final String MIME_TYPE = "video/avc"; // H.264 Advanced Video
    private byte[] yuv420 = null;
    private long presentationTimeUs;
    @SuppressLint("NewApi")
    public AvcEncoder(int width, int height, int framerate, int bitrate, String path) {

        m_width  = width;
        m_height = height;
        if(path != null && !path.equals("")){
            ouputPath = path;
        }
        Log.v("xmc", "AvcEncoder:"+m_width+"+"+m_height);
        yuv420 = new byte[width*height*3/2];
        mBufferInfo = new MediaCodec.BufferInfo();
        MediaCodecInfo mediaCodecInfo = selectCodec("video/avc");
        mColorFormat = getColorFormat(mediaCodecInfo);
        mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
        /*byte[] header_sps = {0, 0, 0, 1, 103, 100, 0, 31, -84, -76, 2, -128, 45, -56};
        byte[] header_pps = {0, 0, 0, 1, 104, -18, 60, 97, 15, -1, -16, -121, -1, -8, 67, -1, -4, 33, -1, -2, 16, -1, -1, 8, 127, -1, -64};
        mediaFormat.setByteBuffer("csd-0", ByteBuffer.wrap(header_sps));
        mediaFormat.setByteBuffer("csd-1", ByteBuffer.wrap(header_pps));*/
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, framerate);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, mColorFormat);
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);//关键帧间隔时间 单位s
        try {
            mediaCodec = MediaCodec.createEncoderByType("video/avc");
        }catch(Exception e){
            e.printStackTrace();
        }
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        presentationTimeUs = new Date().getTime() * 1000;
        try {
            mediaMuxer = new MediaMuxer(ouputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        }catch(Exception e){
            e.printStackTrace();
        }

        mediaCodec.start();
        videoTrackIndex = -1;
        endOfStream = false;
        stoped = false;
       // mMuxerStarted = true;
    }

    public void stop(){
        stoped = false;
        endOfStream = true;
    }

    @SuppressLint("NewApi")
    public void close() {
        try {
            Log.d(TAG, "sending EOS to encoder");
           // mediaCodec.signalEndOfInputStream();
            mMuxerStarted = false;
            mediaCodec.stop();
            mediaCodec.release();
            mediaMuxer.stop();
            mediaMuxer.release();
            stoped = true;
        } catch (Exception e){
            e.printStackTrace();
        }finally {
            stoped = true;
        }
    }

    public boolean isStoped(){
        return stoped;
    }

    @SuppressLint("NewApi")
    public int offerEncoder(byte[] input/*, byte[] output*/) {
        //Log.v("xmc", "offerEncoder:"+input.length+"+"+output.length);
        byte[] dstByte = new byte[calculateLength(ImageFormat.YV12)];
        if (input != null) {
            //Log.v("xmc", "offerEncoder:"+input.length+"+"+output.length);
            // input 是Nv21
            if (mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar) {
                Log.d(TAG, "COLOR_FormatYUV420SemiPlanar");
                Yuv420Util.Nv21ToYuv420SP(input, dstByte, m_width, m_height);
               // System.arraycopy(input, 0, dstByte, 0, input.length);
            } else if (mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar) {
                Log.d(TAG, "COLOR_FormatYUV420Planar");
                Yuv420Util.Nv21ToI420(input, dstByte, m_width, m_height);

            } else if (mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible) {
                // Yuv420_888
                Log.d(TAG, "COLOR_FormatYUV420Flexible");
            } else if (mColorFormat == MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar) {
                Log.d(TAG, "COLOR_FormatYUV420PackedPlanar");
                // Yuv420packedPlannar 和 yuv420sp很像
                // 区别在于 加入 width = 4的话 y1,y2,y3 ,y4公用 u1v1
                // 而 yuv420dp 则是 y1y2y5y6 共用 u1v1
                //http://blog.csdn.net/jumper511/article/details/21719313

                //这样处理的话颜色核能会有些失真。
                Yuv420Util.Nv21ToYuv420SP(input, dstByte, m_width, m_height);
            } else {
                Log.d(TAG, "else color format");
                System.arraycopy(input, 0, dstByte, 0, input.length);

            }
           // camera.addCallbackBuffer(data);
        } else {
           // camera.addCallbackBuffer(new byte[calculateLength(ImageFormat.NV21)]);
        }


        int pos = 0;
       // swapYV12toI420(input, yuv420, m_width, m_height);
        try {
            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);

            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                inputBuffer.clear();
                //inputBuffer.put(input);
                inputBuffer.put(dstByte,0,dstByte.length);
                long pts = new Date().getTime() * 1000 - presentationTimeUs;
                if(endOfStream){
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                }else{
                    mediaCodec.queueInputBuffer(inputBufferIndex, 0, dstByte.length, pts, 0);
                }

            }

            final long TIMEOUT_USEC = 10000;
           // int outputBufferIndex = mediaCodec.dequeueOutputBuffer(mBufferInfo,TIMEOUT_USEC);


                while (true) {
                    int encoderStatus = mediaCodec.dequeueOutputBuffer(mBufferInfo, TIMEOUT_USEC);
                    if (encoderStatus == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        // no output available yet
                        Log.d(TAG, "no output available, spinning to await EOS");
                        if(mMuxerStarted)
                            break;

                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        // not expected for an encoder
                        Log.d(TAG, "MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED");
                        break;
                    } else if (encoderStatus == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        Log.d(TAG, "MediaCodec.INFO_OUTPUT_FORMAT_CHANGED");
                        // should happen before receiving buffers, and should only happen once
                        if (mMuxerStarted) {
                            throw new RuntimeException("format changed twice");
                        }
                        MediaFormat newFormat = mediaCodec.getOutputFormat();
                        Log.d(TAG, "encoder output format changed: " + newFormat);

                        // now that we have the Magic Goodies, start the muxer
                        videoTrackIndex = mediaMuxer.addTrack(newFormat);
                        mediaMuxer.start();
                        mMuxerStarted = true;
                    } else if (encoderStatus < 0) {
                        Log.w(TAG, "unexpected result from encoder.dequeueOutputBuffer: " +
                                encoderStatus);
                        // let's ignore it
                        break;
                    } else {
                        ByteBuffer encodedData = outputBuffers[encoderStatus];
                        if (encodedData == null) {
                            throw new RuntimeException("encoderOutputBuffer " + encoderStatus +
                                    " was null");
                        }

                        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            // The codec config data was pulled out and fed to the muxer when we got
                            // the INFO_OUTPUT_FORMAT_CHANGED status.  Ignore it.
                            Log.d(TAG, "ignoring BUFFER_FLAG_CODEC_CONFIG");
                            mBufferInfo.size = 0;
                        }

                        if (mBufferInfo.size != 0) {
                            if (!mMuxerStarted) {
                                throw new RuntimeException("muxer hasn't started");
                            }

                            // adjust the ByteBuffer values to match BufferInfo (not needed?)
                            encodedData.position(mBufferInfo.offset);
                            encodedData.limit(mBufferInfo.offset + mBufferInfo.size);

                            mediaMuxer.writeSampleData(videoTrackIndex, encodedData, mBufferInfo);
                            Log.d(TAG, "sent " + mBufferInfo.size + " bytes to muxer; endOfStream = " + endOfStream);
                        }

                        mediaCodec.releaseOutputBuffer(encoderStatus, false);

                        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                                Log.d(TAG, "end of stream reached");
                                close();
                            break;      // out of while
                        }
                    }
                }

        } catch (Throwable t) {
            t.printStackTrace();
        }
        Log.v("xmc", "offerEncoder+pos:"+pos);
        return pos;
    }

    //网友提供的，如果swapYV12toI420方法颜色不对可以试下这个方法，不同机型有不同的转码方式
    private void NV21toI420SemiPlanar(byte[] nv21bytes, byte[] i420bytes, int width, int height) {
        Log.v("xmc", "NV21toI420SemiPlanar:::"+width+"+"+height);
        final int iSize = width * height;
        System.arraycopy(nv21bytes, 0, i420bytes, 0, iSize);

        for (int iIndex = 0; iIndex < iSize / 2; iIndex += 2) {
            i420bytes[iSize + iIndex / 2 + iSize / 4] = nv21bytes[iSize + iIndex]; // U
            i420bytes[iSize + iIndex / 2] = nv21bytes[iSize + iIndex + 1]; // V
        }
    }

    //yv12 转 yuv420p  yvu -> yuv
    private void swapYV12toI420(byte[] yv12bytes, byte[] i420bytes, int width, int height) {
        Log.v("xmc", "swapYV12toI420:::"+width+"+"+height);
        Log.v("xmc", "swapYV12toI420:::"+yv12bytes.length+"+"+i420bytes.length+"+"+width * height);
        System.arraycopy(yv12bytes, 0, i420bytes, 0, width*height);
        System.arraycopy(yv12bytes, width*height+width*height/4, i420bytes, width*height,width*height/4);
        System.arraycopy(yv12bytes, width*height, i420bytes, width*height+width*height/4,width*height/4);
    }
    //public static void arraycopy(Object src,int srcPos,Object dest,int destPos,int length)
    //src:源数组；	srcPos:源数组要复制的起始位置；
    //dest:目的数组；	destPos:目的数组放置的起始位置；	length:复制的长度。


    private  MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }

    private int getColorFormat(MediaCodecInfo mediaCodecInfo) {
        int matchedFormat = 0;
        MediaCodecInfo.CodecCapabilities codecCapabilities =
                mediaCodecInfo.getCapabilitiesForType("video/avc");
        for (int i = 0; i < codecCapabilities.colorFormats.length; i++) {
            int format = codecCapabilities.colorFormats[i];
            if (format >= codecCapabilities.COLOR_FormatYUV420Planar &&
                    format <= codecCapabilities.COLOR_FormatYUV420PackedSemiPlanar) {
                if (format >= matchedFormat) {
                    matchedFormat = format;
                    logColorFormatName(format);
                    break;
                }
            }
        }
        return matchedFormat;
    }
    private void logColorFormatName(int format) {
        switch (format) {
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible:
                Log.d(TAG, "COLOR_FormatYUV420Flexible");
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
                Log.d(TAG, "COLOR_FormatYUV420PackedPlanar");
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
                Log.d(TAG, "COLOR_FormatYUV420Planar");
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
                Log.d(TAG, "COLOR_FormatYUV420PackedSemiPlanar");
                break;
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
                Log.d(TAG, "COLOR_FormatYUV420SemiPlanar");
                break;
        }
    }
    private int calculateLength(int format) {
        return m_width * m_height
                * ImageFormat.getBitsPerPixel(format) / 8;
    }

}
