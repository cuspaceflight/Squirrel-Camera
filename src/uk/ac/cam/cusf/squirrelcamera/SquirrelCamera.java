package uk.ac.cam.cusf.squirrelcamera;

import java.io.File;
import java.io.FileNotFoundException;
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

    private final static String AUTO_START =
        "uk.ac.cam.cusf.squirrelcamera.AUTO_START";
    
    public final static String DESCENT_BROADCAST = "uk.ac.cam.cusf.intent.action.DESCENT";
    public final static String CAMERA_START = "uk.ac.cam.cusf.intent.CAMERA_START";

    // Enters low power mode when battery level is LOW_BATTERY %
    public final static int LOW_BATTERY = 15;
    public final static String LOW_BATTERY_MSG =
        "Entering low power mode - battery level lower than LOW_BATTERY!";
    
    // Stop recording when battery level falls below MIN_BATTERY %
    public final static int MIN_BATTERY = 10;
    public final static String MIN_BATTERY_MSG =
        "Stopping camera - battery level lower than MIN_BATTERY!";

    private SurfaceView sv;
    private Scheduler scheduler;

    private Button action;
    private CheckBox confirm;

    private LocationManager locationManager;
    private LocationListener locationListener;
    private BroadcastReceiver batteryListener;
    private BroadcastReceiver descentListener;
    
    private BroadcastReceiver scheduleStart;

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

                        Log.e(TAG, "Rebooting - see you later...");
                        
                        try {
                            Runtime.getRuntime().exec(new String[]{"/system/bin/su","-c","reboot now"});
                        } catch (IOException e) {
                            // We've run out of luck at this point...
                            finish();
                        }

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
                    int level =
                        intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    int scale =
                        intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                    double percent = (100.0 * level) / scale;
                    if (percent < MIN_BATTERY) {
                        Toast.makeText(getApplicationContext(), MIN_BATTERY_MSG,
                                5000).show();
                        Log.i(TAG, MIN_BATTERY_MSG);
                        stopScheduler();
                    } else if (percent < LOW_BATTERY) {
                        Toast.makeText(getApplicationContext(), LOW_BATTERY_MSG,
                                5000).show();
                        Log.i(TAG, LOW_BATTERY_MSG);
                        scheduler.lowPower(true);
                    }
                }
            }
        };

        descentListener = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals(DESCENT_BROADCAST)) {
                    Log.i(TAG, "DESCENT_BROADCAST received!");
                    Bundle extras = intent.getExtras();
                    if (extras.getBoolean("landed", false)) {
                        Log.i(TAG, "Squirrel has landed - stopping recording");
                        stopScheduler();
                    }
                }
            }
        };
        
        scheduleStart = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                boolean start = intent.getBooleanExtra("start", true);
                if (start) {
                    startScheduler();
                } else {
                    stopScheduler();
                }
            }
            
        };
        
        // Set ringer mode to silent so that the shutter sound doesn't play
        AudioManager audioManager =
            (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);

    }

    private void startScheduler() {
        if (scheduler.start()) {
            action.setText("Stop");
            IntentFilter actionChanged = new IntentFilter();
            actionChanged.addAction(Intent.ACTION_BATTERY_CHANGED);
            registerReceiver(batteryListener, actionChanged);
            actionChanged = new IntentFilter();
            actionChanged.addAction(DESCENT_BROADCAST);
            registerReceiver(descentListener, actionChanged);
        }
    }

    private void stopScheduler() {
        scheduler.stop();
        cameraManager.stopVideo();
        unregisterReceiver(batteryListener);
        unregisterReceiver(descentListener);
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
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(CAMERA_START);
        registerReceiver(scheduleStart, filter);
        
        CameraStatus.setActivity(true);
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
        
        unregisterReceiver(scheduleStart);
        
        CameraStatus.setActivity(false);
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

        private MediaRecorder.OnInfoListener infoListener =
            new MediaRecorder.OnInfoListener() {
                @Override
                public void onInfo(MediaRecorder mr, int what, int extra) {
                    Log.i(TAG, "MediaRecorder.onInfoListener (What: " + what
                            + ", Extra: " + extra + ")");
                    if (what == MediaRecorder
                            .MEDIA_RECORDER_INFO_MAX_DURATION_REACHED
                            || what == MediaRecorder
                            .MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) {
                        if (recording) stopVideo();
                        scheduler.advance();
                    }
                }
            };

        private MediaRecorder.OnErrorListener errorListener =
            new MediaRecorder.OnErrorListener() {
                @Override
                public void onError(MediaRecorder mr, int what, int extra) {
                    Log.e(TAG, "MediaRecorder.OnErrorListener (What: " + what
                            + ", Extra: " + extra + ")");
                    if (recording) stopVideo();
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
                Log.e(TAG, "Storage error (no SD, or below min. disk space)");
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
                Log.e(TAG, "Storage error (no SD, or below min. disk space)");
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
            
            // Write to internal phone memory for SSTV access
            try {
                filename = "sstv.jpg";
                SquirrelCamera.this.deleteFile(filename); // Delete existing file
                FileOutputStream fos = SquirrelCamera.this.openFileOutput(filename, 3);
                fos.write(jpeg[0]);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.e(TAG, "FileNotFoundException in SavePhotoTask (internal)", e);
            } catch (IOException e) {
                Log.e(TAG, "IOException in SavePhotoTask (internal)", e);
            }

            return null;
        }
    }

}