package com.action2control.camera;

import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;

public class MediaProjectionHelper {
    private static final String TAG = "MediaProjectionHelper";

    @SuppressWarnings("deprecation")
    public static VirtualDisplay createVirtualDisplay(
            MediaProjection projection,
            DisplayMetrics displayMetrics,
            Surface surface) {

        Log.d(TAG, "Creating VirtualDisplay: " + displayMetrics.widthPixels + "x" + displayMetrics.heightPixels);
        Log.d(TAG, "Surface valid: " + surface.isValid());

        try {
            // Use legacy VirtualDisplay.Callback (works on all APIs when targetSdk < 34)
            VirtualDisplay.Callback callback = new VirtualDisplay.Callback() {
                // No need to override methods, empty implementation is sufficient
            };

            VirtualDisplay vd = projection.createVirtualDisplay(
                    "ScreenRecorder",
                    displayMetrics.widthPixels,
                    displayMetrics.heightPixels,
                    displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    callback,
                    new Handler(Looper.getMainLooper())
            );
            Log.d(TAG, "VirtualDisplay created: " + (vd != null));
            return vd;
        } catch (Exception e) {
            Log.e(TAG, "Failed to create VirtualDisplay", e);
            return null;
        }
    }
}
