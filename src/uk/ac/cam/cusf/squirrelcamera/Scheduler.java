package uk.ac.cam.cusf.squirrelcamera;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class Scheduler {

    public final static String TAG = "SquirrelCamera";

    // The length in milliseconds to wait after a DiskException before
    // proceeding
    public final static long PAUSE_DURATION = 120000; // 2 minutes

    public final static int VIDEO_START = 0;
    public final static int VIDEO_STOP = 1;
    public final static int PHOTO = 2;

    private Handler handler;

    private Iterator<Task> iterator;
    private List<Task> taskList;

    private boolean RUNNING = false;

    private boolean lowPower = false;

    public Scheduler(final CameraRunnable camera) {

        taskList = new ArrayList<Task>();
        for (int i = 0; i < 12; i++) {
            taskList.add(Task.photo(20000));
        }
        taskList.add(Task.video(20000, 60000));

        handler = new Handler(new Handler.Callback() {

            @Override
            public boolean handleMessage(Message msg) {

                switch (msg.what) {

                case VIDEO_START:
                    Log.i(TAG, "VIDEO_START");
                    try {
                        camera.startVideo();
                        Message message = handler.obtainMessage(VIDEO_STOP,
                                msg.arg1, 0);
                        handler.sendMessageDelayed(message, msg.arg2);
                    } catch (Storage.DiskException e) {
                        pause();
                    } catch (Exception e) {
                        // Proceed to next action, with delay to let
                        // CameraService restart, for example
                        handler.sendMessageDelayed(getNextMessage(), 15000);
                    }
                    break;
                case VIDEO_STOP:
                    Log.i(TAG, "VIDEO_STOP");
                    camera.stopVideo();
                    handler.sendMessageDelayed(getNextMessage(), msg.arg1
                            * (lowPower ? 2 : 1));
                    break;
                case PHOTO:
                    Log.i(TAG, "PHOTO");
                    try {
                        camera.takePhoto();
                        handler.sendMessageDelayed(getNextMessage(), msg.arg1
                                * (lowPower ? 2 : 1));
                    } catch (Storage.DiskException e) {
                        pause();
                    }
                    break;
                }

                return false;
            }

        });

    }

    private Message getNextMessage() {
        if (iterator == null || !iterator.hasNext()) {
            iterator = taskList.iterator();
        }
        Task task = iterator.next();
        return handler.obtainMessage(task.what, task.arg1, task.arg2);
    }

    public void lowPower(boolean enable) {
        lowPower = enable;
    }

    public void advance() {
        Log.i(TAG, "Scheduler advance()");
        handler.removeMessages(VIDEO_START);
        handler.removeMessages(VIDEO_STOP);
        handler.removeMessages(PHOTO);
        handler.sendMessageDelayed(getNextMessage(), 15000);
    }

    public boolean isRunning() {
        return RUNNING;
    }

    private void pause() {
        Log.i(TAG, "Scheduler pausing for " + PAUSE_DURATION + " milliseconds");
        handler.sendMessageDelayed(getNextMessage(), PAUSE_DURATION);
    }

    public boolean start() {
        if (RUNNING == false && !taskList.isEmpty()) {
            RUNNING = true;
            handler.sendMessageDelayed(getNextMessage(), 5000);
            return true;
        } else {
            return false;
        }
    }

    public void stop() {
        handler.removeMessages(VIDEO_START);
        handler.removeMessages(VIDEO_STOP);
        handler.removeMessages(PHOTO);

        RUNNING = false;
    }

    private static class Task {

        int what;
        int arg1;
        int arg2;

        protected Task(int what, int arg1, int arg2) {
            this.what = what;
            this.arg1 = arg1;
            this.arg2 = arg2;
        }

        public static Task photo(int delay) {
            return new Task(PHOTO, delay, 0);
        }

        public static Task video(int delay, int length) {
            return new Task(VIDEO_START, delay, length);
        }

    }

}
