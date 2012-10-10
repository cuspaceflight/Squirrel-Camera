package uk.ac.cam.cusf.squirrelcamera;

public class CameraStatus {

    private static boolean activity = false;
    private static boolean running = false;
    private static long time = System.currentTimeMillis();
    
    public static void setRunning(boolean run) {
        running = run;
        time = System.currentTimeMillis();
    }
    
    public static void setActivity(boolean act) {
        activity = act;
    }
    
    public static boolean isRunning() {
        return running;
    }
    
    public static long getTime() {
        return time;
    }
    
    public static boolean isActivity() {
        return activity;
    }
    
}
