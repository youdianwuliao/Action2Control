package com.action2control.camera;

import android.hardware.display.DisplayManager;
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

    public static android.hardware.display.VirtualDisplay createVirtualDisplay(
            MediaProjection projection,
            DisplayMetrics displayMetrics,
            Surface surface) {
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                // Use reflection to call the API 34 specific overload to avoid compiler ambiguity
                MediaProjection.Callback callback = new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        Log.d(TAG, "MediaProjection callback: onStop");
                    }
                };
                
                Method method = MediaProjection.class.getMethod(
                        "createVirtualDisplay",
                        String.class, int.class, int.class, int.class, int.class,
                        Surface.class, MediaProjection.Callback.class, Handler.class
                );
                
                return (android.hardware.display.VirtualDisplay) method.invoke(
                        projection,
                        "ScreenRecorder",
                        displayMetrics.widthPixels,
                        displayMetrics.heightPixels,
                        displayMetrics.densityDpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
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
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC,
                    surface,
                    null,
                    null
            );
        }
    }
}
