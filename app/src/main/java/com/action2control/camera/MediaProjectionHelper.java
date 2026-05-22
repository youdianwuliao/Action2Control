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

import java.lang.reflect.Method;

public class MediaProjectionHelper {
    private static final String TAG = "MediaProjectionHelper";

    public static VirtualDisplay createVirtualDisplay(
            MediaProjection projection,
            DisplayMetrics displayMetrics,
            Surface surface) {

        if (Build.VERSION.SDK_INT >= 34) {
            try {
                // API 34+ method signature:
                // createVirtualDisplay(String name, int width, int height, int displayDensityDpi,
                //                      int flags, Surface surface, MediaProjection.Callback callback, Handler handler)
                MediaProjection.Callback callback = new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        Log.d(TAG, "MediaProjection callback: onStop");
                    }
                };

                Method method = projection.getClass().getMethod(
                        "createVirtualDisplay",
                        String.class,
                        int.class,
                        int.class,
                        int.class,
                        int.class,
                        Surface.class,
                        MediaProjection.Callback.class,
                        Handler.class
                );

                Log.d(TAG, "Found API 34 createVirtualDisplay method via reflection");

                return (VirtualDisplay) method.invoke(
                        projection,
                        "ScreenRecorder",
                        displayMetrics.widthPixels,
                        displayMetrics.heightPixels,
                        displayMetrics.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        surface,
                        callback,
                        new Handler(Looper.getMainLooper())
                );
            } catch (Exception e) {
                Log.e(TAG, "Failed to create VirtualDisplay via reflection", e);
                return null;
            }
        } else {
            return projection.createVirtualDisplay(
                    "ScreenRecorder",
                    displayMetrics.widthPixels,
                    displayMetrics.heightPixels,
                    displayMetrics.densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    surface,
                    null,
                    null
            );
        }
    }
}
