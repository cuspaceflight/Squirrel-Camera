package uk.ac.cam.cusf.squirrelcamera;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;

public class DebugMediaRecorder {

    private static final String TAG = "SquirrelCamera";

    private android.media.MediaRecorder recorder;
    private static PrintStream out;

    private ExecutorService executor;
    private Callable<Void> task;

    private boolean ERROR = false;

    public void openFile() {
        if (out != null) {
            out.close();
        }
        File exportDir = new File(Environment.getExternalStorageDirectory(),
                "DebugMediaRecorder");
        if (!exportDir.exists()) {
            exportDir.mkdirs();
        }
        File file = new File(exportDir, "debuglog" + System.currentTimeMillis()
                + ".txt");
        try {
            if (!file.exists())
                file.createNewFile();
            out = new PrintStream(file);
        } catch (IOException e) {
            Log.e("DebugMediaRecorder", "IOException!", e);
        }
    }

    private void log(String msg, Exception e) {
        out.print(msg + "\n");
        e.printStackTrace(out);
        out.flush();
    }

    private void log(String msg) {
        out.print(msg + "\n");
        out.flush();
    }

    public DebugMediaRecorder() {
        openFile();

        executor = Executors.newCachedThreadPool();

        task = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                Log.i(TAG, "BEFORE recorder.stop");
                recorder.stop();
                Log.i(TAG, "AFTER recorder.stop");
                return null;
            }
        };

        log("MediaRecorder()");
        recorder = new android.media.MediaRecorder();
    }

    public void start() throws IllegalStateException {
        try {
            log("start()");
            recorder.start();
        } catch (IllegalStateException e) {
            log("IllegalStateException in start()", e);
            throw e;
        }
    }

    public void stop() throws IllegalStateException, ExecutionException {

        /*
         * Why do we use a Future/Executor here? - In testing under 2.3.3,
         * stop() sometimes blocked indefinitely - Future attempts to connect to
         * the camera will fail, so not really a solution
         */

        // Perhaps we should throw a fatal exception here and restart app,
        // or even reboot the device (if on rooted ROM)?

        Future<Void> future = executor.submit(task);
        try {
            ERROR = true;
            future.get(5, TimeUnit.SECONDS);
            ERROR = false;
        } catch (ExecutionException e) {
            Log.i(TAG, "ExecutionException", e);
            log("ExecutionException", e);
            throw e;
        } catch (InterruptedException e) {
            Log.i(TAG, "InterruptedException", e);
            log("InterruptedException", e);
        } catch (TimeoutException e) {
            Log.i(TAG, "TimeoutException", e);
            log("TimeoutException", e);
        } finally {
            future.cancel(true);
        }
    }

    public void reset() {
        log("reset()");
        Log.i(TAG, "reset()");
        if (!ERROR)
            recorder.reset();
    }

    public void release() {
        log("release()");
        Log.i(TAG, "release()");
        if (!ERROR)
            recorder.release();
    }

    public void setOnInfoListener(
            android.media.MediaRecorder.OnInfoListener listener) {
        log("setOnInfoListener()");
        recorder.setOnInfoListener(listener);
    }

    public void setOnErrorListener(
            android.media.MediaRecorder.OnErrorListener listener) {
        log("setOnErrorListener()");
        recorder.setOnErrorListener(listener);
    }

    public void setPreviewDisplay(Surface surface) {
        log("setPreviewDisplay()");
        recorder.setPreviewDisplay(surface);
    }

    public void setCamera(Camera camera) {
        log("setCamera()");
        recorder.setCamera(camera);
    }

    public void setAudioSource(int audio_source) throws IllegalStateException {
        try {
            log("setAudioSource()");
            recorder.setAudioSource(audio_source);
        } catch (IllegalStateException e) {
            log("IllegalStateException in setAudioSource()", e);
            throw e;
        }
    }

    public void setVideoSource(int video_source) throws IllegalStateException {
        try {
            log("setVideoSource()");
            recorder.setVideoSource(video_source);
        } catch (IllegalStateException e) {
            log("IllegalStateException in setVideoSource()", e);
            throw e;
        }
    }

    public void setProfile(CamcorderProfile profile) {
        log("setProfile()");
        recorder.setProfile(profile);
    }

    public void setOutputFile(String path) throws IllegalStateException {
        try {
            log("setOutputFile()");
            recorder.setOutputFile(path);
        } catch (IllegalStateException e) {
            log("IllegalStateException in setOutputFile()", e);
            throw e;
        }
    }

    public void setMaxDuration(int duration) throws IllegalArgumentException {
        try {
            log("setMaxDuration()");
            recorder.setMaxDuration(duration);
        } catch (IllegalArgumentException e) {
            log("IllegalArgumentException in setMaxDuration()", e);
            throw e;
        }
    }

    public void setMaxFileSize(long max_filesize_bytes)
            throws IllegalArgumentException {
        try {
            log("setMaxFileSize()");
            recorder.setMaxFileSize(max_filesize_bytes);
        } catch (IllegalArgumentException e) {
            log("IllegalArgumentException in setMaxFileSize()", e);
            throw e;
        }
    }

    public void prepare() throws IllegalStateException, IOException {
        try {
            log("prepare()");
            recorder.prepare();
        } catch (IllegalStateException e) {
            log("IllegalStateException in prepare()", e);
            throw e;
        } catch (IOException e) {
            log("IOException in prepare()", e);
            throw e;
        }
    }

    public void setVideoFrameRate(int rate) {
        recorder.setVideoFrameRate(rate);
    }

}
