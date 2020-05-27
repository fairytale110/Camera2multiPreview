package com.afei.camera2getpreview.util;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class Camera2Proxy {

    private static final String TAG = "Camera2Proxy";

    private Activity mActivity;

    // camera
    private int mCameraId = 0; // 要打开的摄像头ID
    private Size mPreviewSize = new Size(1024, 768); // 固定640*480演示
    private CameraDevice mCameraDevice; // 相机对象
    private CameraCaptureSession mCaptureSession;

    // handler
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    // output
    private Surface mPreviewSurface; // 输出到屏幕的预览
    private ImageReader mImageReader; // 预览回调的接收者
    private ImageReader.OnImageAvailableListener mOnImageAvailableListener;

    public void setmCameraId(int mCameraId) {
        this.mCameraId = mCameraId;
    }

    /**
     * 打开摄像头的回调
     */
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            Log.d(TAG, "onOpened");
            mCameraDevice = camera;
            initPreviewRequest();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            Log.d(TAG, "onDisconnected");
            releaseCamera();
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            Log.e(TAG, "Camera Open failed, error: " + error);
            releaseCamera();
        }
    };

    @TargetApi(Build.VERSION_CODES.M)
    public Camera2Proxy(Activity activity) {
        mActivity = activity;

    }

    @SuppressLint("MissingPermission")
    public void openCamera() {
        Log.v(TAG, "openCamera");
        startBackgroundThread(); // 对应 releaseCamera() 方法中的 stopBackgroundThread()
        try {
            CameraManager cameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(""+mCameraId);
            StreamConfigurationMap streamConfigurationMap =  characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size[] sizes = streamConfigurationMap.getOutputSizes(ImageFormat.JPEG);
            for (int i = 0; i < sizes.length; i++) {
                System.out.println("支持的size：w="+sizes[i].getWidth()+" h="+sizes[i].getHeight());
            }
            Log.d(TAG, "preview size: " + mPreviewSize.getWidth() + "*" + mPreviewSize.getHeight());
            mImageReader = ImageReader.newInstance(mPreviewSize.getWidth(), mPreviewSize.getHeight(), ImageFormat.YUV_420_888, 2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);
            // 打开摄像头
            cameraManager.openCamera(Integer.toString(mCameraId), mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void releaseCamera() {
        Log.v(TAG, "releaseCamera");
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        if (mCaptureSession != null) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
        stopBackgroundThread(); // 对应 openCamera() 方法中的 startBackgroundThread()
    }

    public void setImageAvailableListener(ImageReader.OnImageAvailableListener onImageAvailableListener) {
        mOnImageAvailableListener = onImageAvailableListener;
    }

    public void setPreviewSurface(SurfaceTexture surfaceTexture) {
        // mPreviewSize必须先初始化完成
        surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
        mPreviewSurface = new Surface(surfaceTexture);
    }

    private void initPreviewRequest() {
        try {
            final CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            // 添加输出到屏幕的surface
            builder.addTarget(mPreviewSurface);
            // 添加输出到ImageReader的surface。然后我们就可以从ImageReader中获取预览数据了
            builder.addTarget(mImageReader.getSurface());
            mCameraDevice.createCaptureSession(Arrays.asList(mPreviewSurface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            mCaptureSession = session;
                            // 设置连续自动对焦和自动曝光
                            builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                            CaptureRequest captureRequest = builder.build();
                            try {
                                // 一直发送预览请求
                                mCaptureSession.setRepeatingRequest(captureRequest, null, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            Log.e(TAG, "ConfigureFailed. session: mCaptureSession");
                        }
                    }, mBackgroundHandler); // handle 传入 null 表示使用当前线程的 Looper
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public Size getPreviewSize() {
        return mPreviewSize;
    }

    public void switchCamera() {
        mCameraId ^= 1;
        Log.d(TAG, "switchCamera: mCameraId: " + mCameraId);
        releaseCamera();
        openCamera();
    }

    private void startBackgroundThread() {
        if (mBackgroundThread == null || mBackgroundHandler == null) {
            Log.v(TAG, "startBackgroundThread");
            mBackgroundThread = new HandlerThread("CameraBackground");
            mBackgroundThread.start();
            mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
        }
    }

    private void stopBackgroundThread() {
        Log.v(TAG, "stopBackgroundThread");
        try {
            mBackgroundThread.quitSafely();
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

}


//2020-05-12 09:28:43.243 26177-26177/? I/System.out:         支持的size：w=1920 h=1080
//        2020-05-12 09:28:43.243 26177-26177/? I/System.out: 支持的size：w=352 h=288
//        2020-05-12 09:28:43.243 26177-26177/? I/System.out: 支持的size：w=432 h=240
//        2020-05-12 09:28:43.243 26177-26177/? I/System.out: 支持的size：w=320 h=184
//        2020-05-12 09:28:43.243 26177-26177/? I/System.out: 支持的size：w=176 h=144
//        2020-05-12 09:28:43.243 26177-26177/? I/System.out: 支持的size：w=160 h=120
//        2020-05-12 09:28:43.244 26177-26177/? I/System.out: 支持的size：w=320 h=240
//        2020-05-12 09:28:43.244 26177-26177/? I/System.out: 支持的size：w=640 h=360
//        2020-05-12 09:28:43.244 26177-26177/? I/System.out: 支持的size：w=640 h=480
//        2020-05-12 09:28:43.244 26177-26177/? I/System.out: 支持的size：w=800 h=600
//        2020-05-12 09:28:43.244 26177-26177/? I/System.out: 支持的size：w=960 h=720
//        2020-05-12 09:28:43.244 26177-26177/? I/System.out: 支持的size：w=1024 h=768
//        2020-05-12 09:28:43.244 26177-26177/? I/System.out: 支持的size：w=1280 h=720
//        2020-05-12 09:28:43.244 26177-26177/? I/System.out: 支持的size：w=1280 h=960