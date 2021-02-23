package com.pspdfkit.views;


import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A layout that we will use to intercept touches. It needs to have an id of pspdf__activity_fragment_container, as you can see in the
 * activity_overlay.xml. The PdfFragment will be displayed in it, which will allow us to intercept touches before the page is touched.
 */
public class TouchInterceptorLayout extends FrameLayout {

    // Down event for long-press will happen before we know it's the long press
    // and before we start intercepting, but we still want to propagate it down.
    @Nullable private MotionEvent lastDownEvent;

    // Listener for intercepted touches.
    @Nullable private OnMotionInterceptedListener onMotionInterceptedListener = null;

    public interface OnMotionInterceptedListener {
        boolean onMotionIntercepted(MotionEvent motionEvent);
    }

    private boolean intercept = false;

    public TouchInterceptorLayout(@NonNull Context context) {
        super(context);
    }

    public TouchInterceptorLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public TouchInterceptorLayout(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    /**
     * Notify the interceptor to start intercepting. The intercepting process will automatically be stopped when the touch is released.
     */
    public void startInterception() {
        intercept = true;
        if (lastDownEvent != null && onMotionInterceptedListener != null) {
            onMotionInterceptedListener.onMotionIntercepted(lastDownEvent);
            // After we emitted it we can recycle it again.
            recycleLastDownEvent();
        }
    }

    /**
     * Setter for the listener.
     */
    public void setOnMotionInterceptedListener(@Nullable OnMotionInterceptedListener listener) {
        onMotionInterceptedListener = listener;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            recycleLastDownEvent();
            // Since the MotionEvent will be recycled we need to create a copy.
            lastDownEvent = MotionEvent.obtain(ev);
        }
        return intercept || super.onInterceptTouchEvent(ev);
    }

    private void recycleLastDownEvent() {
        if (lastDownEvent != null) {
            lastDownEvent.recycle();
        }
        lastDownEvent = null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            intercept = false;
            recycleLastDownEvent();
        }
        return onMotionInterceptedListener != null && onMotionInterceptedListener.onMotionIntercepted(event);
    }

}