package com.example.mediacodec_decodemp4;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    //目前是使用了内置的视频，在raw目录下，然后先拷贝复制到了如下的MP4_PLAY_PATH本地存储中
    public static final String MP4_PLAY_PATH = Environment.getExternalStorageDirectory().getAbsolutePath() + "/TestInputV.mp4";
    private static final String TAG = "weekend";
    private boolean mWorking = false;
    public static SurfaceView surfaceView;
    private Button mStartBtn;
    private static final int INIT_MANAGER_MSG = 0x01;
    private static final int INIT_MANAGER_DELAY = 500;
    private final static int CAMERA_OK = 10001;
    private static String[] PERMISSIONS_STORAGE = {
            "android.permission.CAMERA",
            "android.permission.WRITE_EXTERNAL_STORAGE" };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        copyResourceToMemory(R.raw.video, MP4_PLAY_PATH);

        surfaceView = findViewById(R.id.surfaceview);
        if (Build.VERSION.SDK_INT>22) {
            if (!checkPermissionAllGranted(PERMISSIONS_STORAGE)){
                ActivityCompat.requestPermissions(MainActivity.this,
                        PERMISSIONS_STORAGE, CAMERA_OK);
            }
        }
        mStartBtn = findViewById(R.id.btnStartPlay);

        mStartBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(mWorking){
                    stopWork();
                    mWorking = false;
                    mStartBtn.setText("start");
                }else{
                    mHandler.sendEmptyMessageDelayed(INIT_MANAGER_MSG, INIT_MANAGER_DELAY);
                    mWorking = true;
                    mStartBtn.setText("stop");
                }
            }
        });
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == INIT_MANAGER_MSG) {
                startWork();
            }
        }
    };

    private boolean checkPermissionAllGranted(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                // 只要有一个权限没有被授予, 则直接返回 false
                return false;
            }
        }
        return true;
    }

    private void startWork() {
        DecoderManager.getInstance().startMP4Decode();
    }

    private void stopWork() {
        DecoderManager.getInstance().close();
    }

    private void copyResourceToMemory(int srcPath, String destPath) {
        InputStream fileInputStream = null;
        FileOutputStream fileOutputStream = null;
        try {
            fileInputStream = getResources().openRawResource(srcPath);
            File file = new File(destPath);
            if (file.exists()) {
                return;
            }
            file.createNewFile();
            fileOutputStream = new FileOutputStream(file);
            byte[] bytes = new byte[1024];
            while ((fileInputStream.read(bytes)) > 0) {
                fileOutputStream.write(bytes);
            }

        } catch (FileNotFoundException e) {
            Log.e(TAG, "copyVideoResourceToMemory FileNotFoundException : " + e);
        } catch (IOException e) {
            Log.e(TAG, "copyVideoResourceToMemory IOException : " + e);
        } finally {
            try {
                if(fileInputStream!=null){
                    fileInputStream.close();
                }
                if(fileOutputStream!=null){
                    fileOutputStream.close();
                }

            } catch (IOException e) {
                Log.e(TAG, "close stream IOException : " + e);
            }
        }
    }

    public static Surface getSurface() {
        return surfaceView.getHolder().getSurface();
    }
}