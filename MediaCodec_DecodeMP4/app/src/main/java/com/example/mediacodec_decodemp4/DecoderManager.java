package com.example.mediacodec_decodemp4;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;
import java.io.IOException;
import java.nio.ByteBuffer;
import android.media.MediaCodec.BufferInfo;
import android.view.Surface;

public class DecoderManager {

    private static final String TAG = "weekend";

    private static DecoderManager instance;
    private MediaCodec mediaCodec;
    private MediaFormat mediaFormat;
    private volatile boolean isDecodeFinish = false;
    private MediaExtractor mediaExtractor;
    private SpeedManager mSpeedController = new SpeedManager();
    private DecoderMP4Thread mDecodeMp4Thread;

    private DecoderManager() {
    }

    public static DecoderManager getInstance() {
        if (instance == null) {
            instance = new DecoderManager();
        }
        return instance;
    }

    /**
     * * Synchronized callback decoding
     */
    private void initMediaCodecSys() {
        try {
            mediaCodec = MediaCodec.createDecoderByType("video/avc");
            mediaFormat = MediaFormat.createVideoFormat("video/avc", 1280, 720);
            mediaExtractor = new MediaExtractor();
            //MP4 文件存放位置
            mediaExtractor.setDataSource(MainActivity.MP4_PLAY_PATH);
            Log.d(TAG, "getTrackCount: " + mediaExtractor.getTrackCount());
            for (int i = 0; i < mediaExtractor.getTrackCount(); i++) {
                MediaFormat format = mediaExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                Log.d(TAG, "mime: " + mime);
                if (mime.startsWith("video")) {
                    mediaFormat = format;
                    mediaExtractor.selectTrack(i);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Surface surface = MainActivity.getSurface();
        mediaCodec.configure(mediaFormat, surface, null, 0);
        mediaCodec.start();
    }

    /**
     * Play the MP4 file Thread
     * 解码主流程
     */
    private class DecoderMP4Thread extends Thread {
        long pts = 0;

        @Override
        public void run() {
            super.run();
            while (!isDecodeFinish) {
                int inputIndex = mediaCodec.dequeueInputBuffer(-1);
                Log.d(TAG, "inputIndex: " + inputIndex);
                if (inputIndex >= 0) {
                    ByteBuffer byteBuffer = mediaCodec.getInputBuffer(inputIndex);
                    //读取一片或者一帧数据
                    int sampSize = mediaExtractor.readSampleData(byteBuffer, 0);
                    //读取时间戳
                    long time = mediaExtractor.getSampleTime();
                    if (sampSize > 0 && time > 0) {
                        mediaCodec.queueInputBuffer(inputIndex, 0, sampSize, time, 0);
                        //读取一帧后必须调用，提取下一帧
                        //控制帧率在30帧左右
                        mSpeedController.preRender(time);
                        mediaExtractor.advance();
                    }
                }
                BufferInfo bufferInfo = new BufferInfo();
                int outIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0);
                if (outIndex >= 0) {
                    mediaCodec.releaseOutputBuffer(outIndex, true);
                }
            }
        }
    }

    public void close() {
        try {
            Log.d(TAG, "close start");
            if (mediaCodec != null) {
                isDecodeFinish = true;
                try {
                    if (mDecodeMp4Thread != null) {
                        mDecodeMp4Thread.join(2000);
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "InterruptedException " + e);
                }
                boolean isAlive = mDecodeMp4Thread.isAlive();
                Log.d(TAG, "close end isAlive :" + isAlive);
                mediaCodec.stop();
                mediaCodec.release();
                mediaCodec = null;
                mSpeedController.reset();
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
        instance = null;
    }


    public void startMP4Decode() {
        initMediaCodecSys();
        mDecodeMp4Thread = new DecoderMP4Thread();
        mDecodeMp4Thread.setName("DecoderMP4Thread");
        mDecodeMp4Thread.start();

    }

}

