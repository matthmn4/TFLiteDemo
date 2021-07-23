package com.example.tflitepractice;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.common.model.LocalModel;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.ShapeDrawable;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.mlkit.vision.objects.custom.CustomObjectDetectorOptions;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {

    PreviewView prevView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    TextView textView;
    GraphicsOverlay graphicsOverlay;

    private int REQUEST_CODE_PERMISSIONS = 101;
    private String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA"};

    private class YourAnalyzer implements ImageAnalysis.Analyzer {

            private boolean needUpdateGraphicOverlayImageSourceInfo = true;

            @Override
            @androidx.camera.core.ExperimentalGetImage
            public void analyze(ImageProxy imageProxy) {

                if (needUpdateGraphicOverlayImageSourceInfo) {
                    int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                    if (rotationDegrees == 0 || rotationDegrees == 180) {
                        graphicsOverlay.setImageSourceInfo(imageProxy.getWidth(), imageProxy.getHeight(), false);
                    } else {
                        graphicsOverlay.setImageSourceInfo(imageProxy.getHeight(), imageProxy.getWidth(), false);
                    }
                    needUpdateGraphicOverlayImageSourceInfo = false;

                }

                Image mediaImage = imageProxy.getImage();
                if (mediaImage != null) {
                    InputImage image =
                            InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

                    LocalModel localModel =
                            new LocalModel.Builder()
                                    .setAssetFilePath("lite-model_object_detection_mobile_object_labeler_v1_1.tflite")
                                    .build();

                    CustomObjectDetectorOptions customObjectDetectorOptions =
                            new CustomObjectDetectorOptions.Builder(localModel)
                                    .setDetectorMode(CustomObjectDetectorOptions.SINGLE_IMAGE_MODE)
                                    .enableMultipleObjects()
                                    .enableClassification()
                                    .setClassificationConfidenceThreshold(0.5f)
                                    .setMaxPerObjectLabelCount(3)
                                    .build();

                    ObjectDetector objectDetector =
                            ObjectDetection.getClient(customObjectDetectorOptions);

                    objectDetector
                            .process(image)
                            .addOnFailureListener(new OnFailureListener() {
                                @Override
                                public void onFailure(@NonNull Exception e) {
                                    imageProxy.close();
                                }
                            })
                            .addOnSuccessListener(new OnSuccessListener<List<DetectedObject>>() {
                                @Override
                                public void onSuccess(List<DetectedObject> results) {

                                    for (DetectedObject detectedObject : results) {
                                        //Rect boundingBox = detectedObject.getBoundingBox();
                                        graphicsOverlay.clear();

                                        for (DetectedObject.Label label : detectedObject.getLabels()) {
                                            graphicsOverlay.add(new ObjectGraphic(graphicsOverlay, detectedObject));
                                            Log.d("hello", graphicsOverlay.toString());

                                            String text = label.getText();
                                            int index = label.getIndex();
                                            float confidence = label.getConfidence();
                                            Log.d("hello", text);
                                            textView.setText(text);
                                        }
                                        graphicsOverlay.postInvalidate();
                                    }
                                    imageProxy.close();
                                }
                            });
                }
            }
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_main);

            prevView = findViewById(R.id.previewView);
            textView = findViewById(R.id.scan_button);
            graphicsOverlay = findViewById(R.id.graphic_overlay);
            if (graphicsOverlay == null) {
                Log.d("mainactivity", "graphicOverlay is null");
            }

            if(allPermissionsGranted()){
                startCamera();
            }else{
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            }

        }

        private void startCamera() {
            cameraProviderFuture = ProcessCameraProvider.getInstance(this);
            cameraProviderFuture.addListener(new Runnable() {
                @Override
                public void run() {
                    try {
                        ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                        bindPreview(cameraProvider);
                    } catch (ExecutionException | InterruptedException e) { }
                }
            }, ContextCompat.getMainExecutor(this));


        }

        void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {

            Preview preview = new Preview.Builder()
                    .build();

            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                    .build();

            preview.setSurfaceProvider(prevView.getSurfaceProvider());

            ImageAnalysis imageAnalysis =
                    new ImageAnalysis.Builder()
                            .setTargetResolution(new Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build();
            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), new YourAnalyzer());

            Camera camera = cameraProvider.bindToLifecycle((LifecycleOwner)this, cameraSelector, preview, imageAnalysis);


        }



        private boolean allPermissionsGranted() {
            for(String permission: REQUIRED_PERMISSIONS){
                if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                    return false;
                }
            }
            return true;
        }


    }