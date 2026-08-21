package sushi.hardcore.droidfs.widgets;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

public class ZoomableImageView extends androidx.appcompat.widget.AppCompatImageView implements GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener {

    Matrix matrix;

    // We can be in one of these 3 states
    static final int NONE = 0;
    static final int DRAG = 1;
    static final int ZOOM = 2;
    int mode = NONE;

    // Remember some things for zooming
    PointF last = new PointF();
    PointF start = new PointF();
    static final float minScale = 1f;
    static final float maxScale = 3f;

    int viewWidth, viewHeight;
    static final int CLICK = 3;
    static final int SWIPE_MIN_DISTANCE = 150;
    float saveScale = 1f;
    protected float origWidth, origHeight;
    private int rotationAngle = 0;
    private boolean multiTouch = false;
    private final RectF contentBounds = new RectF();

    ScaleGestureDetector mScaleDetector;

    public interface OnInteractionListener {
        void onSingleTap(MotionEvent event);
        void onSwipe(float deltaX);
    }

    OnInteractionListener onInteractionListener = null;

    public ZoomableImageView(Context context) {
        super(context);
        sharedConstructing(context);
    }

    public ZoomableImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        sharedConstructing(context);
    }

    GestureDetector mGestureDetector;

    private void sharedConstructing(Context context) {
        super.setClickable(true);
        mGestureDetector = new GestureDetector(context, this);
        mGestureDetector.setOnDoubleTapListener(this);

        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
        matrix = new Matrix();
        setImageMatrix(matrix);
        setScaleType(ScaleType.MATRIX);

        setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                mScaleDetector.onTouchEvent(event);
                mGestureDetector.onTouchEvent(event);

                PointF curr = new PointF(event.getX(), event.getY());

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        last.set(curr);
                        start.set(last);
                        mode = DRAG;
                        multiTouch = false;
                        break;

                    case MotionEvent.ACTION_POINTER_DOWN:
                        multiTouch = true;
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (mode == DRAG) {
                            float deltaX = curr.x - last.x;
                            float deltaY = curr.y - last.y;
                            float fixTransX = getFixDragTrans(deltaX, viewWidth,
                                    origWidth * saveScale);
                            float fixTransY = getFixDragTrans(deltaY, viewHeight,
                                    origHeight * saveScale);
                            matrix.postTranslate(fixTransX, fixTransY);
                            fixTrans();
                            last.set(curr.x, curr.y);
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        mode = NONE;
                        int xDiff = (int) Math.abs(curr.x - start.x);
                        int yDiff = (int) Math.abs(curr.y - start.y);
                        if (xDiff < CLICK && yDiff < CLICK)
                            performClick();
                        else if (!multiTouch && saveScale <= minScale
                                && yDiff < xDiff && xDiff > SWIPE_MIN_DISTANCE
                                && onInteractionListener != null)
                            onInteractionListener.onSwipe(curr.x - start.x);
                        break;

                    case MotionEvent.ACTION_POINTER_UP:
                        mode = NONE;
                        break;
                }

                setImageMatrix(matrix);
                invalidate();
                return true; // indicate event was handled
            }

        });
    }

    private void resetZoomFactor() {
        saveScale = minScale;
    }

    public void restoreZoomNormal(){
        resetZoomFactor();
        fitContentToView();
    }

    public void setRotationAngle(int angle) {
        rotationAngle = angle;
        resetZoomFactor();
        fitContentToView();
    }

    public void setOnInteractionListener(OnInteractionListener listener){
        onInteractionListener = listener;
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        if (onInteractionListener != null){
            onInteractionListener.onSingleTap(e);
        }
        return false;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        if (saveScale >= maxScale) {
            restoreZoomNormal();
        } else {
            float origScale = saveScale;
            saveScale *= 1.5;
            float mScaleFactor = saveScale / origScale;
            matrix.postScale(mScaleFactor, mScaleFactor, e.getX(), e.getY());
            fixTrans();
        }

        return false;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onDown(MotionEvent e) {
        return false;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {
        return false;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        return false;
    }

    @Override
    public void onLongPress(MotionEvent e) {

    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        return false;
    }

    private class ScaleListener extends
            ScaleGestureDetector.SimpleOnScaleGestureListener {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            mode = ZOOM;
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float mScaleFactor = detector.getScaleFactor();
            float origScale = saveScale;
            saveScale *= mScaleFactor;
            if (saveScale < minScale) {
                saveScale = minScale;
                mScaleFactor = minScale / origScale;
            }

            if (origWidth * saveScale <= viewWidth
                    || origHeight * saveScale <= viewHeight)
                matrix.postScale(mScaleFactor, mScaleFactor, viewWidth / 2,
                        viewHeight / 2);
            else
                matrix.postScale(mScaleFactor, mScaleFactor,
                        detector.getFocusX(), detector.getFocusY());

            fixTrans();
            return true;
        }
    }

    void fixTrans() {
        Drawable drawable = getDrawable();
        if (drawable == null) {
            return;
        }
        contentBounds.set(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        matrix.mapRect(contentBounds);

        float fixTransX = getFixTrans(contentBounds.left, viewWidth, contentBounds.width());
        float fixTransY = getFixTrans(contentBounds.top, viewHeight, contentBounds.height());

        if (fixTransX != 0 || fixTransY != 0)
            matrix.postTranslate(fixTransX, fixTransY);
    }

    float getFixTrans(float trans, float viewSize, float contentSize) {
        float minTrans, maxTrans;

        if (contentSize <= viewSize) {
            minTrans = 0;
            maxTrans = viewSize - contentSize;
        } else {
            minTrans = viewSize - contentSize;
            maxTrans = 0;
        }

        if (trans < minTrans)
            return -trans + minTrans;
        if (trans > maxTrans)
            return -trans + maxTrans;
        return 0;
    }

    float getFixDragTrans(float delta, float viewSize, float contentSize) {
        if (contentSize <= viewSize) {
            return 0;
        }
        return delta;
    }

    private void fitContentToView() {
        Drawable drawable = getDrawable();
        if (drawable == null || drawable.getIntrinsicWidth() <= 0 || drawable.getIntrinsicHeight() <= 0 || viewWidth == 0 || viewHeight == 0) {
            return;
        }

        // Fit to screen.
        int bmWidth = drawable.getIntrinsicWidth();
        int bmHeight = drawable.getIntrinsicHeight();

        // Quarter turns swap the on-screen content dimensions.
        boolean swapped = (rotationAngle % 180) != 0;
        float effWidth = swapped ? bmHeight : bmWidth;
        float effHeight = swapped ? bmWidth : bmHeight;

        float scale = Math.min((float) viewWidth / effWidth, (float) viewHeight / effHeight);
        matrix.setScale(scale, scale);

        // Rotate the scaled content about its own center, then center it on the view
        matrix.postRotate(rotationAngle, (bmWidth * scale) / 2, (bmHeight * scale) / 2);
        matrix.postTranslate(viewWidth / 2f - (bmWidth * scale) / 2, viewHeight / 2f - (bmHeight * scale) / 2);

        origWidth = effWidth * scale;
        origHeight = effHeight * scale;
        setImageMatrix(matrix);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        viewWidth = MeasureSpec.getSize(widthMeasureSpec);
        viewHeight = MeasureSpec.getSize(heightMeasureSpec);
        if (viewWidth == 0 || viewHeight == 0) {
            return;
        }
        if (saveScale == 1) {
            fitContentToView();
        }
        fixTrans();
    }
}
