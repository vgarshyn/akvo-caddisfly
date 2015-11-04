package org.akvo.akvoqr;

import android.content.Context;
import android.hardware.Camera;
import android.os.Build;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import org.akvo.akvoqr.detector.CameraConfigurationUtils;

import java.util.List;

/**
 * Created by linda on 7/7/15.
 */
public class BaseCameraView extends SurfaceView implements SurfaceHolder.Callback{

    /** A basic Camera preview class */

    private SurfaceHolder mHolder;
    private Camera mCamera;
    private CameraActivity activity;
    private Camera.Parameters parameters;

    public BaseCameraView(Context context, Camera camera) {
        super(context);
        mCamera = camera;

        activity = (CameraActivity) context;

        // Install a SurfaceHolder.Callback so we get notified when the
        // underlying surface is created and destroyed.
        mHolder = getHolder();
        mHolder.addCallback(this);
        // deprecated setting, but required on Android versions prior to 3.0
        mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);


    }

    public void surfaceCreated(SurfaceHolder holder) {
        // The Surface has been created, now tell the camera where to draw the preview.
        try {
            mCamera.setPreviewDisplay(holder);

        } catch (Exception e) {
            Log.d("", "Error setting camera preview: " + e.getMessage());
        }
    }


    public void surfaceDestroyed(SurfaceHolder holder) {

    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
        // If your preview can change or rotate, take care of those events here.
        // Make sure to stop the preview before resizing or reformatting it.

        if (holder.getSurface() == null){
            // preview surface does not exist
            return;
        }

        if(mCamera == null)
        {
            //Camera was released
            return;
        }

        // stop preview before making changes
        try {
            mCamera.stopPreview();
        } catch (Exception e){
            // ignore: tried to stop a non-existent preview
        }

        // set preview size and make any resize, rotate or
        // reformatting changes here

        try {
            parameters = mCamera.getParameters();
        }
        catch (Exception e)
        {
            e.printStackTrace();

        }
        if(parameters == null)
        {
            return;
        }


        Camera.Size bestSize = null;
        List<Camera.Size> sizes = mCamera.getParameters().getSupportedPreviewSizes();
        int maxWidth = 0;
        for(Camera.Size size: sizes) {
            System.out.println("***supported preview sizes w, h: " + size.width + ", " + size.height);
            if(size.width>1300)
                continue;
            if (size.width > maxWidth) {
                bestSize = size;
                maxWidth = size.width;
            }
        }

        //portrait mode
        mCamera.setDisplayOrientation(90);

        //preview size
        parameters.setPreviewSize(bestSize.width, bestSize.height);

        //parameters.setPreviewFormat(ImageFormat.NV21);

        boolean canAutoFocus = false;
        boolean disableContinuousFocus = true;
        List<String> modes = mCamera.getParameters().getSupportedFocusModes();
        for(String s: modes) {

            if(s.equals(Camera.Parameters.FOCUS_MODE_AUTO))
            {
                canAutoFocus = true;
            }
            if(s.equals(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
            {
                disableContinuousFocus = false;
            }
        }

        CameraConfigurationUtils.setFocus(parameters, canAutoFocus, disableContinuousFocus, false);

        //flashmode
        //switchFlashMode();

        //white balance
        if(parameters.getWhiteBalance()!=null)
        {
            //TODO check if this optimise the code
            parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_DAYLIGHT);
        }

        // start preview with new settings
        try {

            mCamera.setParameters(parameters);
            mCamera.setPreviewDisplay(holder);

            activity.getMessage(0);

        } catch (Exception e){
            Log.d("", "Error starting camera preview: " + e.getMessage());
        }

    }

    public void switchFlashMode()
    {
        if(mCamera==null)
            return;
        parameters = mCamera.getParameters();

        String flashmode = mCamera.getParameters().getFlashMode().equals(Camera.Parameters.FLASH_MODE_OFF)?
                Camera.Parameters.FLASH_MODE_TORCH: Camera.Parameters.FLASH_MODE_OFF;
        parameters.setFlashMode(flashmode);

        mCamera.setParameters(parameters);
    }

    //exposure compensation
    private static int direction = 1;
    public void adjustExposure(int goOnInSameDirection)
    {
        if(mCamera==null)
            return;
        parameters = mCamera.getParameters();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            parameters.setAutoExposureLock(true);
            mCamera.setParameters(parameters);
        }


        int ec = parameters.getExposureCompensation();
        if( ec == parameters.getMinExposureCompensation() || ec == parameters.getMaxExposureCompensation())
        {
            direction = -direction;
        }

        int compPlus = Math.min(parameters.getMaxExposureCompensation(), parameters.getExposureCompensation() + 1);
        int compMinus = Math.max(parameters.getMinExposureCompensation(), parameters.getExposureCompensation() - 1);
        int currentDirection = direction==1? compPlus: compMinus;
        int differentDirection = currentDirection==compMinus? compPlus: compMinus;

        if(goOnInSameDirection > 0)
        {
            parameters.setExposureCompensation(currentDirection);
        }
        else if(goOnInSameDirection < 0)
        {
            parameters.setExposureCompensation(differentDirection);
        }
        else if(goOnInSameDirection == 0) {
            parameters.setExposureCompensation(0);
        }

//        System.out.println("***min Exposure compensation: " + parameters.getMinExposureCompensation());
//        System.out.println("***max Exposure compensation: " + parameters.getMaxExposureCompensation());
        System.out.println("***Exposure compensation direction: " + goOnInSameDirection);
        System.out.println("***Exposure compensation: " + parameters.getExposureCompensation());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            parameters.setAutoExposureLock(false);
        }
        mCamera.setParameters(parameters);
    }
}

