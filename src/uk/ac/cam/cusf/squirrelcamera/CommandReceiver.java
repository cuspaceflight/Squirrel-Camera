package uk.ac.cam.cusf.squirrelcamera;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class CommandReceiver extends BroadcastReceiver {

    public final static String TAG = "SquirrelCamera";
    
    public final static String SMS_RECEIVED = "uk.ac.cam.cusf.intent.SMS_RECEIVED";
    public final static String SMS_SEND = "uk.ac.cam.cusf.intent.SMS_SEND";
    public final static String CAMERA_START = "uk.ac.cam.cusf.intent.CAMERA_START";
    
    public final static String CAMERA_ACTION = "uk.ac.cam.cusf.squirrelcamera.CAMERA_ACTIVITY";
    private final static String AUTO_START = "uk.ac.cam.cusf.squirrelcamera.AUTO_START";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        
        if (intent.getAction().equals(SMS_RECEIVED)) {
            
            Log.i(TAG, "SMS_RECEIVED");
            
            String phoneNumber = intent.getStringExtra("phoneNumber");
            String command = intent.getStringExtra("command");
            
            Log.i(TAG, phoneNumber + ": " + command);
            
            String message = "SquirrelCamera: ";
            
            if (command == null) {
                Log.e(TAG, "No command received");
                return;
            } else if (command.equals("status")) {
                
                message += CameraStatus.isActivity() ? "Activity, " : "No Activity, ";
                message += CameraStatus.isRunning() ? "Running, " : "Not Running, ";
                
                Date date = new Date(CameraStatus.getTime());
                SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
                dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                
                message += dateFormat.format(date);
                
            } else if (command.equals("start")) {
                
                if (CameraStatus.isRunning()) {
                    message += "Already running!";
                } else if (CameraStatus.isActivity()) {
                    Intent start = new Intent();
                    start.setAction(CAMERA_START);
                    start.putExtra("start", true);
                    context.sendBroadcast(start);
                    message += "Activity running, schedule started";
                } else {
                    Intent launch = new Intent(CAMERA_ACTION);
                    launch.putExtra(AUTO_START, true);
                    launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(launch);
                    message += "Not running, now launched";
                }
                                
            } else if (command.equals("stop")) {
                
                if (CameraStatus.isRunning()) {
                    Intent stop = new Intent();
                    stop.setAction(CAMERA_START);
                    stop.putExtra("start", false);
                    context.sendBroadcast(stop);
                    message += "Camera schedule running, now stopped";
                } else {
                    message += "Camera schedule already stopped";
                }

            }
            
            intent = new Intent();
            intent.setAction(SMS_SEND);
            intent.putExtra("phoneNumber", phoneNumber);
            intent.putExtra("message", message);
            
            context.sendBroadcast(intent);
            
        }
        
    }

}
