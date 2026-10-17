package org.admute;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

final class ScreenCover {
    private final Context context;
    private final WindowManager manager;
    private View view;
    private Rect[] labelWindows = new Rect[0];
    void setLabelWindows(Rect[] windows) {
        labelWindows = windows.clone();
        if (view instanceof DarkTextureView) ((DarkTextureView) view).setLabelWindows(labelWindows);
    }
    ScreenCover(Context context) { this.context = context; manager = context.getSystemService(WindowManager.class); }
    void show() {
        if (view != null) return;
        DarkTextureView next = new DarkTextureView(context);
        next.setLabelWindows(labelWindows);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.setTitle("ADMute comfort cover");
        manager.addView(next, params); view = next;
    }
    void hide() {
        if (view == null) return;
        try { manager.removeViewImmediate(view); view = null; }
        catch (RuntimeException error) {
            // Retain an attached window so the next release can retry its removal.
            if (!view.isAttachedToWindow()) view = null;
            else throw error;
        }
    }
    boolean isShowing() { return view != null; }
}
