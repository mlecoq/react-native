/*
 * ReactPdfUiFragment.java
 *
 *   PSPDFKit
 *
 *   Copyright © 2021 PSPDFKit GmbH. All rights reserved.
 *
 *   THIS SOURCE CODE AND ANY ACCOMPANYING DOCUMENTATION ARE PROTECTED BY INTERNATIONAL COPYRIGHT LAW
 *   AND MAY NOT BE RESOLD OR REDISTRIBUTED. USAGE IS BOUND TO THE PSPDFKIT LICENSE AGREEMENT.
 *   UNAUTHORIZED REPRODUCTION OR DISTRIBUTION IS SUBJECT TO CIVIL AND CRIMINAL PENALTIES.
 *   This notice may not be removed from this file.
 */

package com.pspdfkit.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.pspdfkit.document.PdfDocument;
import com.pspdfkit.react.R;
import com.pspdfkit.ui.PdfFragment;
import com.pspdfkit.ui.PdfUiFragment;
import com.pspdfkit.ui.overlay.OverlayLayoutParams;
import com.pspdfkit.ui.overlay.OverlayViewProvider;
import com.pspdfkit.utils.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;



/**
 * This {@link PdfUiFragment} provides additional callbacks to improve integration into react native.
 * <p/>
 * <ul>
 * <li>A callback when the configuration was changed.</li>
 * <li>A method to show and hide the navigation button in the toolbar, as well as a callback for when it is clicked.</li>
 * </ul>
 */
public class ReactPdfUiFragment extends PdfUiFragment implements TouchInterceptorLayout.OnMotionInterceptedListener{

    @Nullable private ReactPdfUiFragmentListener reactPdfUiFragmentListener;

    private TouchInterceptorLayout touchInterceptorLayout;
    private MyViewProvider viewProvider;

    private final FragmentManager.FragmentLifecycleCallbacks fragmentLifecycleCallbacks = new FragmentManager.FragmentLifecycleCallbacks() {
        @Override
        public void onFragmentCreated(@NonNull FragmentManager fm, @NonNull Fragment f, @Nullable Bundle savedInstanceState) {
            super.onFragmentCreated(fm, f, savedInstanceState);
            // Whenever a new PdfFragment is created that means the configuration has changed.
            if (f instanceof PdfFragment) {
                if (reactPdfUiFragmentListener != null) {
                    reactPdfUiFragmentListener.onConfigurationChanged(ReactPdfUiFragment.this);
                }
            }
        }
    };

    void setReactPdfUiFragmentListener(@Nullable ReactPdfUiFragmentListener listener) {
        this.reactPdfUiFragmentListener = listener;
    }

    /** When set to true will add a navigation arrow to the toolbar. */
    void setShowNavigationButtonInToolbar(final boolean showNavigationButtonInToolbar) {
        if (getView() == null) {
            return;
        }
        Toolbar toolbar = getView().findViewById(R.id.pspdf__toolbar_main);
        if (showNavigationButtonInToolbar) {
            toolbar.setNavigationIcon(R.drawable.pspdf__ic_navigation_arrow);
            toolbar.setNavigationOnClickListener(v -> {
                if (reactPdfUiFragmentListener != null) {
                    reactPdfUiFragmentListener.onNavigationButtonClicked(this);
                }
            });
        } else {
            toolbar.setNavigationIcon(null);
            toolbar.setNavigationOnClickListener(null);
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewProvider = new MyViewProvider();

        touchInterceptorLayout = getActivity().findViewById(R.id.pspdf__activity_fragment_container);
        touchInterceptorLayout.setOnMotionInterceptedListener(this);

        // We start intercepting once the long-press on the fragment is registered.
        getPdfFragment().setOnDocumentLongPressListener(
                (document, pageIndex, event, pagePosition, longPressedAnnotation) -> {
                    touchInterceptorLayout.startInterception();
                    return true;
                });

        // Add our custom view provider.
        getPdfFragment().addOverlayViewProvider(viewProvider);
    }

    @Override
    public boolean onMotionIntercepted(MotionEvent motionEvent) {
        return viewProvider.onTouch(motionEvent);
    }


    @Override
    public void onStart() {
        super.onStart();
        // We want to get notified when a child PdfFragment is created so we can reattach our listeners.
        getChildFragmentManager().registerFragmentLifecycleCallbacks(fragmentLifecycleCallbacks, false);
    }

    @Override
    public void onStop() {
        super.onStop();
        getChildFragmentManager().unregisterFragmentLifecycleCallbacks(fragmentLifecycleCallbacks);
    }

    /**
     * Listener that notifies of actions taken directly in the PdfUiFragment.
     */
    public interface ReactPdfUiFragmentListener {

        /** Called when the configuration changed, reset your {@link com.pspdfkit.ui.PdfFragment} and {@link PdfUiFragment} listeners in here. */
        void onConfigurationChanged(@NonNull PdfUiFragment pdfUiFragment);

        /** Called when the back navigation button was clicked. */
        void onNavigationButtonClicked(@NonNull PdfUiFragment pdfUiFragment);
    }


      /**
     * View provider used to provide views for the overlay on each page.
     */
    public class MyViewProvider extends OverlayViewProvider {

        // We keep track of the visible views, because we need to
        // propagate only touches that are above one of those visible views,
        // since we're doing it manually.
        @NonNull
        List<View> visibleViews = new ArrayList<>();

        @Nullable
        @Override
        public List<View> getViewsForPage(@NonNull Context context,
                                          @NonNull PdfDocument document,
                                          int pageIndex) {
            // Here we provide views for the given page.
            // In this example this will be a so called CircleDragView which can add
            // and drag circles, as per specifications for this example.
            Size pageSize = document.getPageSize(pageIndex);
            CircleDragView circleDragView = new CircleDragView(context);
            circleDragView.setLayoutParams(
                    new OverlayLayoutParams(
                            new RectF(0f, pageSize.height, pageSize.width, 0f),
                            OverlayLayoutParams.SizingMode.LAYOUT));

            return Collections.singletonList(circleDragView);
        }

        @Override
        public void onViewsShown(int pageIndex, @NonNull List<View> views) {
            visibleViews.addAll(views);
        }

        @Override
        public void onViewsHidden(int pageIndex, @NonNull List<View> views) {
            visibleViews.removeAll(views);
        }

        Rect outRect = new Rect();
        int[] location = new int[2];

        public boolean onTouch(MotionEvent motionEvent) {

            // We look for the visible circle above which the touch is occurring.
            for (View v : visibleViews) {
                if (isViewInBounds(v, (int) motionEvent.getX(), (int) motionEvent.getY())) {
                    motionEvent.setLocation(motionEvent.getRawX() - outRect.left, motionEvent.getRawY() - outRect.top);

                    return ((CircleDragView) v).onTouch(motionEvent);
                }
            }
            return false;
        }

        // Check if the (x,y) is inside the view.
        private boolean isViewInBounds(View view, int x, int y){
            view.getDrawingRect(outRect);
            view.getLocationOnScreen(location);
            outRect.offset(location[0], location[1]);
            return outRect.contains(x, y);
        }
    }

    // A view that will be used as a layout in which we will place the circles.
    // It contains logic for adding and moving circles around.
    public class CircleDragView extends FrameLayout {

        private static final int CIRCLE_RADIUS_PX = 90;

        // Represents a circle.
        class Circle extends View {

            private Paint circlePaint = new Paint();

            // Used for storing the circle center. Note: this are relative coordinates.
            // Values are between 0.0 and 1.0.
            private float x = 0f;
            private float y = 0f;

            public Circle(Context context) {
                super(context);
                circlePaint.setColor(Color.BLUE);
            }

            public void setCenter(float x, float y) {
                this.x = x;
                this.y = y;
            }

            public float getCenterX() {
                return x;
            }

            public float getCenterY() {
                return y;
            }

            public int getRadius() {
                return CIRCLE_RADIUS_PX;
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                // Here we define size of the circle.
                int widthSpec = MeasureSpec.makeMeasureSpec(2 * CIRCLE_RADIUS_PX, MeasureSpec.EXACTLY);
                int heightSpec = MeasureSpec.makeMeasureSpec(2 * CIRCLE_RADIUS_PX, MeasureSpec.EXACTLY);
                setMeasuredDimension(widthSpec, heightSpec);
            }

            @Override
            protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                canvas.drawCircle(CIRCLE_RADIUS_PX, CIRCLE_RADIUS_PX, CIRCLE_RADIUS_PX, circlePaint);
            }
        }

        public List<Circle> addedCircles = new ArrayList<>();
        @Nullable public Circle currentlyAddedCircle = null;

        public CircleDragView(@NonNull Context context) {
            super(context);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            for (Circle c : addedCircles) {
                int centerX = (int) (c.getCenterX() * getWidth());
                int centerY = (int) (c.getCenterY() * getHeight());
                int radius = c.getRadius();
                c.layout(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
            }

            if (currentlyAddedCircle != null) {
                int centerX = (int) (currentlyAddedCircle.getCenterX() * getWidth());
                int centerY = (int) (currentlyAddedCircle.getCenterY() * getHeight());
                int radius = currentlyAddedCircle.getRadius();
                currentlyAddedCircle.layout(centerX - radius, centerY - radius, centerX + radius, centerY + radius);
            }
        }


        public Circle getCircle(float x, float y){
            for(Circle item : addedCircles){
                if(Math.pow((x - item.getCenterX()) * getWidth(), 2) + Math.pow((y - item.getCenterY()) * getHeight(), 2) < Math.pow(CIRCLE_RADIUS_PX, 2)){
                    return item;
                }
            }
            return null;
        }



        public boolean onTouch(MotionEvent event) {

            int width = getWidth();
            int height = getHeight();

            float relativeCenterX =   Math.max(CIRCLE_RADIUS_PX, Math.min( event.getX(), width - CIRCLE_RADIUS_PX ))  / getWidth();
            float relativeCenterY =  Math.max(CIRCLE_RADIUS_PX, Math.min( event.getY(), height - CIRCLE_RADIUS_PX ))  / getHeight();


            switch (event.getAction()){
                case ACTION_DOWN:
                    // Down is pressed, create a new circle and start moving it.
                    currentlyAddedCircle = getCircle(relativeCenterX, relativeCenterY);
                    if(currentlyAddedCircle == null) {
                        currentlyAddedCircle = new Circle(getContext());
                        currentlyAddedCircle.setCenter(relativeCenterX, relativeCenterY);
                        addView(currentlyAddedCircle);
                    }
                    break;
                case ACTION_MOVE:
                    // On move, update the location of the currently moved circle.
                    if (currentlyAddedCircle != null) {
                        currentlyAddedCircle.setCenter(relativeCenterX, relativeCenterY);
                        requestLayout();
                    }
                    break;
                case ACTION_UP:
                case ACTION_CANCEL:
                default:
                    // When the action is done, add the moved circle to the list of added circles.
                    if (currentlyAddedCircle != null) {
                        addedCircles.add(currentlyAddedCircle);
                        currentlyAddedCircle = null;
                    }
                    return false;
            }
            return true;
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            return false;
        }
    }
}
