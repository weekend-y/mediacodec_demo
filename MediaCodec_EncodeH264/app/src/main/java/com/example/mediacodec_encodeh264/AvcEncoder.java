package com.example.mediacodec_encodeh264;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;



public class AvcEncoder
{
    private static final String TAG = "weekend";

    private int TIMEOUT_USEC = 12000;

    private MediaCodec mediaCodec;
    int m_width;
    int m_height;
    int m_framerate;
    byte[] m_info = null;

    public byte[] configbyte;


    @SuppressLint("NewApi")
    public AvcEncoder(int width, int height, int framerate, int bitrate, String path) {

        m_width  = width;
        m_height = height;
        m_framerate = framerate;

        MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", width, height);
        mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar);        //颜色格式
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, width*height*5);                                                     //码率
        mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);                                                               //帧率
        mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);                                                          //I 帧间隔
        try {
            // 创建 MediaCodec，此时是 Uninitialized 状态
            mediaCodec = MediaCodec.createEncoderByType("video/avc");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        // 调用 configure 进入 Configured 状态
        mediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        // 调用 start 进入 Executing 状态，开始编解码工作
        mediaCodec.start();
        createfile(path);
    }

    private BufferedOutputStream outputStream;
    FileOutputStream outStream;
    private void createfile(String path){
        File file = new File(path);
        Log.d(TAG,"createfile path = "+path);
        if(file.exists()){
            file.delete();
        }
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(file));
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @SuppressLint("NewApi")
    private void StopEncoder() {
        try {
            // 调用 stop 方法进入 Uninitialized 状态
            mediaCodec.stop();
            // 调用 release 方法释放，结束操作
            mediaCodec.release();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    ByteBuffer[] inputBuffers;
    ByteBuffer[] outputBuffers;

    public boolean isRuning = false;

    public void StopThread(){
        isRuning = false;
        try {
            StopEncoder();
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    int count = 0;

    //编码子线程
    public void StartEncoderThread(){
        Thread EncoderThread = new Thread(new Runnable() {

            @SuppressLint("NewApi")
            @Override
            public void run() {
                isRuning = true;
                byte[] input = null;
                long pts =  0;
                long generateIndex = 0;

                while (isRuning) {
                    if (MainActivity.YUVQueue.size() >0){
                        input = MainActivity.YUVQueue.poll();
                        byte[] yuv420sp = new byte[m_width*m_height*3/2];
                        NV21ToNV12(input,yuv420sp,m_width,m_height);
                        input = yuv420sp;
                    }
                    if (input != null) {
                        try {
                            long startMs = System.currentTimeMillis();
                            // 输入缓冲区
                            ByteBuffer[] inputBuffers = mediaCodec.getInputBuffers();
                            // 输出缓冲区
                            ByteBuffer[] outputBuffers = mediaCodec.getOutputBuffers();
                            // 从输入缓冲区队列中取出可用缓冲区，并填充数据
                            int inputBufferIndex = mediaCodec.dequeueInputBuffer(-1);
                            if (inputBufferIndex >= 0) {
                                // 计算时间戳
                                pts = computePresentationTime(generateIndex);
                                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                                inputBuffer.clear();
                                inputBuffer.put(input);
                                mediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, pts, 0);
                                generateIndex += 1;
                            }

                            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                            // 从输出缓冲区队列中拿到编解码后的内容，进行相应操作（这里是写入output h264文件）后释放，供下一次使用
                            int outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                            while (outputBufferIndex >= 0) {
                                //Log.d(TAG, "Get H264 Buffer Success! flag = "+bufferInfo.flags+",pts = "+bufferInfo.presentationTimeUs+"");
                                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                                byte[] outData = new byte[bufferInfo.size];
                                outputBuffer.get(outData);
                                // flags 判断
                                if(bufferInfo.flags == 2){   // 配置相关的内容，也就是 SPS，PPS
                                    configbyte = new byte[bufferInfo.size];
                                    configbyte = outData;
                                }else if(bufferInfo.flags == 1){   //关键帧
                                    byte[] keyframe = new byte[bufferInfo.size + configbyte.length];
                                    System.arraycopy(configbyte, 0, keyframe, 0, configbyte.length);
                                    System.arraycopy(outData, 0, keyframe, configbyte.length, outData.length);

                                    outputStream.write(keyframe, 0, keyframe.length);
                                }else{      // 非关键帧和SPS、PPS,直接写入文件，可能是B帧或者P帧
                                    outputStream.write(outData, 0, outData.length);
                                }
                                mediaCodec.releaseOutputBuffer(outputBufferIndex, false);
                                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
                            }

                        } catch (Throwable t) {
                            t.printStackTrace();
                        }
                    } else {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        EncoderThread.start();

    }

    private void NV21ToNV12(byte[] nv21,byte[] nv12,int width,int height){
        if(nv21 == null || nv12 == null) {
            return;
        }
        int framesize = width*height;
        int i = 0,j = 0;
        System.arraycopy(nv21, 0, nv12, 0, framesize);
        for(i = 0; i < framesize; i++){
            nv12[i] = nv21[i];
        }
        for (j = 0; j < framesize/2; j+=2)
        {
            nv12[framesize + j-1] = nv21[j+framesize];
        }
        for (j = 0; j < framesize/2; j+=2)
        {
            nv12[framesize + j] = nv21[j+framesize-1];
        }
    }

    /**
     * Generates the presentation time for frame N, in microseconds.
     */
    private long computePresentationTime(long frameIndex) {
        return 132 + frameIndex * 1000000 / m_framerate;
    }
}

