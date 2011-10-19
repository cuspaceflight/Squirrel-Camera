package uk.ac.cam.cusf.squirrelcamera;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;

public class SquirrelCamera extends Activity {

    public final static String TAG = "SquirrelCamera";

    private final static String AUTO_START = "uk.ac.cam.cusf.squirrelcamera.AUTO_START";

    public final static int LOW_BATTERY = 15; // Enters low power mode at the
                                              // level
    public final static int MIN_BATTERY = 10; // Stop recording when battery
                                              // level is 10%

    private SurfaceView sv;
    private Scheduler scheduler;

    private Button action;
    private CheckBox confirm;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private BroadcastReceiver batteryListener;

    private PowerManager powerManager;
    private PowerManager.WakeLock wakeLock;

    private CameraManager cameraManager = new CameraManager();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        // Only let the screen be turned off if the Power button is pressed
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                    @Override
                    public void uncaughtException(Thread thread, Throwable ex) {
                        Log.e(TAG, "UncaughtExceptionHandler", ex);

                        /*
                         * All uncaught exceptions would result in the Force
                         * Close dialog being displayed, and the app crashing.
                         * We can bypass this by simply calling finish()
                         * ourselves.
                         */

                        finish();

                        /*
                         * If we have the REBOOT permission in the manifest, and
                         * it has been granted (this requires the Android ROM to
                         * be signed with the same key as this app), then we can
                         * reboot the device. This is the safest thing to do in
                         * this situation.
                         */

                        if (powerManager != null)
                            powerManager.reboot(null); // Doesn't work!

                    }
                });

        sv = (SurfaceView) findViewById(R.id.SurfaceView01);
        sv.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onStatusChanged(String provider, int status,
                    Bundle extras) {
            }
        };

        scheduler = new Scheduler(cameraManager);

        confirm = (CheckBox) findViewById(R.id.CheckBox01);
        action = (Button) findViewById(R.id.Button01);

        action.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (confirm.isChecked()) {
                    confirm.setChecked(false);

                    if (scheduler.isRunning()) {
                        stopScheduler();
                    } else {
                        startScheduler();
                    }
                }
            }
        });

        batteryListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                    int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL,
                            -1);
                    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE,
                            -1);
                    double percent = (100.0 * level) / scale;
                    if (percent < MIN_BATTERY) {
                        Toast
                                .makeText(
                                        getApplicationContext(),
                                        "Stopping camera - battery level lower than MIN_BATTERY!",
                                        5000).show();
                        stopScheduler();
                    } else if (percent < LOW_BATTERY) {
                        Toast
                                .makeText(
                                        getApplicationContext(),
                                        "Entering low power mode - battery level lower than LOW_BATTERY!",
                                        5000).show();
                        scheduler.lowPower(true);
                    }
                }
            }
        };

        // Set ringer mode to silent so that the shutter sound doesn't play
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);

    }

    private void startScheduler() {
        if (scheduler.start()) {
            action.setText("Stop");
            IntentFilter actionChanged = new IntentFilter();
            actionChanged.addAction(Intent.ACTION_BATTERY_CHANGED);
            registerReceiver(batteryListener, actionChanged);
        }
    }

    private void stopScheduler() {
        scheduler.stop();
        cameraManager.stopVideo();
        unregisterReceiver(batteryListener);
        action.setText("Start");
    }

    @Override
    public void onStart() {
        super.onStart();

        wakeLock = powerManager
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        wakeLock.acquire();

        Intent intent = getIntent();
        if (intent.getBooleanExtra(AUTO_START, false))
            startScheduler();

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                10000, 0, locationListener);
    }

    @Override
    public void onStop() {
        super.onStop();

        if (scheduler.isRunning())
            stopScheduler();
        cameraManager.stopVideo();
        cameraManager.closeCamera();

        locationManager.removeUpdates(locationListener);

        if (wakeLock != null && wakeLock.isHeld())
            wakeLock.release();
    }

    @Override
    public void onBackPressed() {
        if (!scheduler.isRunning())
            super.onBackPressed();
    }

    private class CameraManager implements CameraRunnable {

        private Camera camera;
        private Camera.Parameters parameters;
        private DebugMediaRecorder recorder;

        private boolean recording = false;

        private MediaRecorder.OnInfoListener infoListener = new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                Log.i(TAG, "MediaRecorder.onInfoListener (What: " + what
                        + ", Extra: " + extra + ")");
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED
                        || what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                    if (recording)
                        stopVideo();
                    scheduler.advance();
                }
            }
        };

        private MediaRecorder.OnErrorListener errorListener = new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mr, int what, int extra) {
                Log.e(TAG, "MediaRecorder.OnErrorListener (What: " + what
                        + ", Extra: " + extra + ")");
                if (recording)
                    stopVideo();
                scheduler.advance();
            }
        };

        public void openCamera() {

            if (camera == null)
                camera = Camera.open();

            if (parameters == null) {
                parameters = camera.getParameters();
                parameters.setPictureFormat(PixelFormat.JPEG);

                List<Camera.Size> pictureSizes = parameters
                        .getSupportedPictureSizes();
                Camera.Size defaultSize = pictureSizes.get(0);
                parameters
                        .setPictureSize(defaultSize.width, defaultSize.height);

                parameters.setJpegQuality(85);

            }

        }

        public void closeCamera() {
            if (camera != null) {
                camera.lock();
                camera.release();
                camera = null;
            }
        }

        @Override
        public void takePhoto() throws Storage.DiskException {

            if (!Storage.hasStorage() || Storage.belowMinimumDiskSpace()) {
                Log
                        .e(TAG,
                                "Storage error (either no SD, or below minimum disk space)");
                throw new Storage.DiskException();
            }

            openCamera();

            parameters.remove("gps-latitude");
            parameters.remove("gps-longitude");
            parameters.remove("gps-altitude");
            parameters.remove("gps-timestamp");

            Location location = locationManager
                    .getLastKnownLocation(LocationManager.GPS_PROVIDER);

            if (location != null) {
                parameters.set("gps-latitude", String.valueOf(location
                        .getLatitude()));
                parameters.set("gps-longitude", String.valueOf(location
                        .getLongitude()));
                parameters.set("gps-altitude", String.valueOf(location
                        .getAltitude()));
                parameters.set("gps-timestamp", String.valueOf(location
                        .getTime()));
            }

            camera.setParameters(parameters);

            try {
                camera.setPreviewDisplay(sv.getHolder());
            } catch (IOException e) {
                Log.e(TAG, "takePhoto() setPreviewDisplay failed", e);
            }

            camera.startPreview();

            camera.takePicture(null, null, new Camera.PictureCallback() {
                public void onPictureTaken(byte[] data, Camera camera) {
                    Log.i(TAG, "onPictureTaken");
                    new SavePhotoTask().execute(data);
                    camera.stopPreview();
                    closeCamera();
                }
            });

        }

        @Override
        public void startVideo() throws Storage.DiskException,
                RuntimeException, IOException {

            if (!Storage.hasStorage() || Storage.belowMinimumDiskSpace()) {
                Log
                        .e(TAG,
                                "Storage error (either no SD, or below minimum disk space)");
                throw new Storage.DiskException();
            }

            openCamera();

            camera.unlock();

            recorder = new DebugMediaRecorder();

            String path = Environment.getExternalStorageDirectory()
                    .getAbsolutePath()
                    + "/SquirrelCamera/" + System.currentTimeMillis() + ".mp4";
            File directory = new File(path).getParentFile();
            if (!directory.exists())
                directory.mkdirs();

            recorder.setOnInfoListener(infoListener);
            recorder.setOnErrorListener(errorListener);

            recorder.setPreviewDisplay(sv.getHolder().getSurface());

            recorder.setCamera(camera);

            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
            recorder.setProfile(CamcorderProfile
                    .get(CamcorderProfile.QUALITY_HIGH));
            recorder.setVideoFrameRate(25);

            recorder.setOutputFile(path);
            recorder.setMaxDuration(-1); // Disable duration limit

            long maxFileSize = Storage.getAvailableStorage()
                    - Storage.MIN_DISK_SPACE;

            try {
                recorder.setMaxFileSize(maxFileSize);
            } catch (RuntimeException exception) {
                // Ignore failure of setMaxFileSize (good reasons!)
            }

            try {
                recorder.prepare();
                recorder.start();
            } catch (IllegalStateException e) {
                Log.e(TAG, "IllegalStateException", e);
                videoRelease();
                throw e;
            } catch (IOException e) {
                Log.e(TAG, "IOException", e);
                videoRelease();
                throw e;
            }

            recording = true;

        }

        @Override
        public void stopVideo() {
            if (recording) {
                if (recorder != null) {
                    recorder.setOnErrorListener(null);
                    recorder.setOnInfoListener(null);
                    try {
                        recorder.stop();
                    } catch (RuntimeException e) {
                        Log.e(TAG, "RuntimeException in stop()", e);
                    } catch (ExecutionException e) {
                        Log.e(TAG, "ExecutionException in stop()", e);
                    }
                }
                recording = false;
            }
            videoRelease();
        }

        private void videoRelease() {
            if (recorder != null) {
                recorder.reset();
                recorder.release();
                recorder = null;
            }
            if (camera != null) {
                try {
                    Log.i(TAG, "BEFORE camera.lock()");
                    camera.lock();
                } catch (RuntimeException e) {
                    Log.e(TAG, "RuntimeException in lock()", e);
                }
                try {
                    Log.i(TAG, "BEFORE camera.reconnect()");
                    camera.reconnect();
                } catch (IOException e) {
                    Log.e(TAG, "IOException in reconnect()", e);
                }
            }

            closeCamera();
        }

    };

    public class SavePhotoTask extends AsyncTask<byte[], Void, Void> {

        @Override
        protected Void doInBackground(byte[]... jpeg) {

            String filename = System.currentTimeMillis() + ".jpg";
            String directory = "SquirrelCamera";

            File exportDir = new File(
                    Environment.getExternalStorageDirectory(), directory);
            File photo = new File(Environment.getExternalStorageDirectory(),
                    directory + "/" + filename);

            if (!exportDir.exists())
                exportDir.mkdirs();
            if (photo.exists())
                photo.delete();

            try {
                FileOutputStream fos = new FileOutputStream(photo.getPath());
                fos.write(jpeg[0]);
                fos.close();
            } catch (IOException e) {
                Log.e(TAG, "IOException in SavePhotoTask", e);
            }

            return null;
        }
    }

}