package uk.ac.cam.cusf.squirrelcamera;

import java.io.IOException;

public interface CameraRunnable {

    public void takePhoto() throws Storage.DiskException;

    public void startVideo() throws RuntimeException, IOException;

    public void stopVideo();

}
