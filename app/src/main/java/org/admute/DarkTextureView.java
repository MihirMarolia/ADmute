package org.admute;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

/** Static low-contrast weave drawn as vectors; no flashing, animation or image downloads. */
final class DarkTextureView extends View {
    private final Paint paint = new Paint();
    DarkTextureView(Context context) {
        super(context);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(Color.rgb(8, 11, 13));
        paint.setColor(Color.rgb(12, 15, 17));
        paint.setStrokeWidth(1);
        float spacing = 24 * getResources().getDisplayMetrics().density;
        for (float x = -getHeight(); x < getWidth(); x += spacing)
            canvas.drawLine(x, 0, x + getHeight(), getHeight(), paint);
    }
}
