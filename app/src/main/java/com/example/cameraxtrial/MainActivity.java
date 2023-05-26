package com.example.cameraxtrial;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.core.VideoCapture;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.core.DbxException;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.WriteMode;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    PreviewView previewView;

    Button bTakePicture, bRecording;
    private ImageCapture imageCapture;
    private VideoCapture videoCapture;
    private File outputDirectory;
    private String currentVideoPath;
    public static int questionIndex = 0;
    public static String[] questions = {"Tell me something interesting that happened to you today..","How are you feeling?","Are you excited about today?"};
    public static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bTakePicture = findViewById(R.id.bCapture);
        bRecording = findViewById(R.id.bRecord);
        previewView = findViewById(R.id.previewView);

        bTakePicture.setOnClickListener(this);
        bRecording.setOnClickListener(this);
        outputDirectory =  getOutputDirectory();
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                startCameraX(cameraProvider);
            } catch (ExecutionException e) {
                e.printStackTrace();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

        }, getExecutor());

    }

    public void getNextQuestion(View view) {
        int nextIndex;
        if (MainActivity.questionIndex == (MainActivity.questions.length - 1)) {
            nextIndex = MainActivity.questions.length - 1;
        } else {
            nextIndex = Math.abs((MainActivity.questionIndex + 1)) % (MainActivity.questions.length);
        }
        TextView tv1 = (TextView)findViewById(R.id.questionTextView);
        tv1.setText(questions[nextIndex]);
        MainActivity.questionIndex = nextIndex;
    }

    public void getPreviousQuestion(View view) {
        int previousIndex;
        if (MainActivity.questionIndex == 0) {
            previousIndex = 0;
        } else {
            previousIndex = Math.abs((MainActivity.questionIndex - 1)) % (MainActivity.questions.length);
        }
        TextView tv1 = (TextView)findViewById(R.id.questionTextView);
        tv1.setText(questions[previousIndex]);
        MainActivity.questionIndex = previousIndex;
    }
    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    @SuppressLint("RestrictedApi")
    private void startCameraX(ProcessCameraProvider cameraProvider) {

        cameraProvider.unbindAll();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        Preview preview = new Preview.Builder().build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build();

        videoCapture = new VideoCapture.Builder()
                .setVideoFrameRate(30)
                .build();

        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, videoCapture);
    }


    @SuppressLint("RestrictedApi")
    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.bRecord: {
                if (bRecording.getText() == "RECORD") {
                    bRecording.setText("STOP");
                    recordVideo();
                } else {
                    bRecording.setText("RECORD");
                    videoCapture.stopRecording();
                }
                break;
            }
            case R.id.bCapture: {
                capturePhoto();
                break;
            }

        }


    }

    @SuppressLint("RestrictedApi")
    private void recordVideo() {
        if (videoCapture != null) {
            long timeStamp = System.currentTimeMillis();
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timeStamp);
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            String filename = Long.toString(timeStamp) + ".mp4";

            currentVideoPath = getVideoFilePath();
            File videoFile = new File(currentVideoPath);
            VideoCapture.OutputFileOptions outputFileOptions = new VideoCapture.OutputFileOptions.Builder(videoFile).build();

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }

            //This is the code that works properly
            videoCapture.startRecording(
                    outputFileOptions,
                    getExecutor(),
                    new VideoCapture.OnVideoSavedCallback() {
                        @Override
                        public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                            Toast.makeText(MainActivity.this,"Saving your video...",Toast.LENGTH_SHORT).show();
                            Executor executor = Executors.newSingleThreadExecutor();
                            executor.execute(() -> uploadVideoToDropbox(videoFile));
                        }

                        @Override
                        public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                            Toast.makeText(MainActivity.this,"Error: "+ message ,Toast.LENGTH_SHORT).show();
                        }
                    }

            );
            readData(filename);

        }
    }


    private void readData(String filename)
    {
        System.out.println("INSIDE THE READ DATA METHOD 1");
        try
        {
            FileInputStream fin = openFileInput(filename);
            int a;
            StringBuilder temp = new StringBuilder();
            while ((a = fin.read()) != -1)
            {
                temp.append((char)a);
            }
            System.out.println("INSIDE READ DATA");
            System.out.println(temp);

            // setting text from the file.
            fin.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }

    private void capturePhoto() {
        long timeStamp = System.currentTimeMillis();
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, timeStamp);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");


        imageCapture.takePicture(
                new ImageCapture.OutputFileOptions.Builder(
                        getContentResolver(),
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        contentValues
                ).build(),
                getExecutor(),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        Toast.makeText(MainActivity.this,"Saving...",Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        Toast.makeText(MainActivity.this,"Error: "+exception.getMessage(),Toast.LENGTH_SHORT).show();


                    }
                });

    }
    private void uploadVideoToDropbox(File videoFile) {
        String accessToken = "sl.BdEImRPQ2PRsXrCODTJteb-1DqyBx_M9L9wBCORc5mQjbv0t1dVtALWlMtlYWacGO_tuP7H2rAlo6tgaoxemAo365wAnhaKvJ5E0_LAqrNFnQF13mstO5hLP14S8zbjWgoHkKuCCZ1Y";
        Executor executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try (InputStream inputStream = new FileInputStream(videoFile)) {
                DbxRequestConfig config = DbxRequestConfig.newBuilder("dropbox/java-tutorial").build();
                DbxClientV2 client = new DbxClientV2(config, accessToken);
                String remotePath = "/Videos/" + videoFile.getName();
                FileMetadata metadata = client.files().uploadBuilder(remotePath).uploadAndFinish(inputStream);
                Log.i(TAG, "Video uploaded to Dropbox: " + metadata.getPathLower());
            } catch (DbxException | IOException e) {
                Log.e(TAG, "Error uploading video to Dropbox", e);
            }
        });
    }
    private File getOutputDirectory() {
        File mediaDir = getExternalMediaDirs()[0];
        File appDir = new File(mediaDir, "CameraXApp");
        appDir.mkdirs();
        return appDir;
    }

    private String getVideoFilePath() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
        return outputDirectory.getAbsolutePath() + File.separator + "VIDEO_" + timeStamp + ".mp4";
    }
}
