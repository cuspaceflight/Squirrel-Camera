package uk.ac.cam.cusf.squirrelcamera;

import java.io.File;
import java.io.IOException;

import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

public class Storage {

    public final static String TAG = "SquirrelCamera";

    public static final long NO_STORAGE_ERROR = -1L;
    public static final long CANNOT_STAT_ERROR = -2L;

    public static final long MIN_DISK_SPACE = 104857600; // 100MB

    public static class DiskException extends IOException {

        public DiskException() {
            generateStatusLog();
        }

        private static final long serialVersionUID = 1L;
    }

    public static long getAvailableStorage() {
        try {
            if (!hasStorage()) {
                return NO_STORAGE_ERROR;
            } else {
                String storageDirectory = Environment
                        .getExternalStorageDirectory().getPath();
                StatFs stat = new StatFs(storageDirectory);
                return (long) stat.getAvailableBlocks()
                        * (long) stat.getBlockSize();
            }
        } catch (Exception ex) {
            // if we can't stat the filesystem then we don't know how many
            // free bytes exist. It might be zero but just leave it
            // blank since we really don't know.
            Log.e(TAG, "Fail to access sdcard", ex);
            return CANNOT_STAT_ERROR;
        }
    }

    private static boolean checkFsWritable() {
        // Create a temporary file to see whether a volume is really writeable.
        // It's important not to put it in the root directory which may have a
        // limit on the number of files.
        String directoryName = Environment.getExternalStorageDirectory()
                .toString()
                + "/DCIM";
        File directory = new File(directoryName);
        if (!directory.isDirectory()) {
            if (!directory.mkdirs()) {
                return false;
            }
        }
        return directory.canWrite();
    }

    public static boolean hasStorage() {
        return hasStorage(true);
    }

    public static boolean hasStorage(boolean requireWriteAccess) {
        String state = Environment.getExternalStorageState();

        if (Environment.MEDIA_MOUNTED.equals(state)) {
            if (requireWriteAccess) {
                boolean writable = checkFsWritable();
                return writable;
            } else {
                return true;
            }
        } else if (!requireWriteAccess
                && Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    public static boolean belowMinimumDiskSpace() {
        long available = Storage.getAvailableStorage();
        if (available == Storage.CANNOT_STAT_ERROR
                || available == Storage.NO_STORAGE_ERROR) {
            // We can't be sure, so return false
            return false;
        } else if (available - Storage.MIN_DISK_SPACE <= 0) {
            return true;
        } else {
            return false;
        }
    }

    private static void generateStatusLog() {
        Log.i(TAG, "Storage details:");
        Log.i(TAG, "    SD present? " + (hasStorage(false) ? "Yes" : "No"));
        Log.i(TAG, "    Can write? " + (checkFsWritable() ? "Yes" : "No"));
        Log.i(TAG, "    Available storage: " + getAvailableStorage());
        Log.i(TAG, "    Minimum disk space: " + MIN_DISK_SPACE);
        Log.i(TAG, "    Below minimum disk space? "
                + (belowMinimumDiskSpace() ? "Yes" : "No"));
    }

}
