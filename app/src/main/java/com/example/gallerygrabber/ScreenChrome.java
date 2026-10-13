package com.example.gallerygrabber;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;

/** Extend app wallpaper behind transparent Android status and navigation bars. */
public final class ScreenChrome {
    private ScreenChrome() { }
    @SuppressWarnings("deprecation")
    public static void edgeToEdge(Activity activity) {
        Window window = activity.getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        window.setNavigationBarContrastEnforced(false);
        window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    @SuppressWarnings("deprecation")
    public static void padControlsForSystemBars(View controls) {
        int left = controls.getPaddingLeft();
        int top = controls.getPaddingTop();
        int right = controls.getPaddingRight();
        int bottom = controls.getPaddingBottom();
        controls.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(left + insets.getSystemWindowInsetLeft(),
                    top + insets.getSystemWindowInsetTop(),
                    right + insets.getSystemWindowInsetRight(),
                    bottom + insets.getSystemWindowInsetBottom());
            return insets;
        });
        controls.requestApplyInsets();
    }
}
