package app.zen.launcher;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/**
 * Home-screen touch surface. While gestures are on, the whole screen is one
 * gesture — taps are hit-tested by the activity, swipes are reported here.
 */
public class InterceptLayout extends FrameLayout {
    public interface SwipeListener {
        void onSwipe(int direction);
    }

    public static final int SWIPE_UP = 0;
    public static final int SWIPE_DOWN = 1;
    public static final int SWIPE_LEFT = 2;
    public static final int SWIPE_RIGHT = 3;

    public GestureDetector detector;
    private boolean gesturesEnabled = true;
    private SwipeListener swipeListener;
    private float downX;
    private float downY;
    private boolean moved;
    private boolean longFired;

    public InterceptLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setGesturesEnabled(boolean enabled) {
        gesturesEnabled = enabled;
    }

    public void setSwipeListener(SwipeListener listener) {
        swipeListener = listener;
    }

    public void markLongPress() {
        longFired = true;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!gesturesEnabled || detector == null) {
            return super.dispatchTouchEvent(ev);
        }
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = ev.getX();
            downY = ev.getY();
            moved = false;
            longFired = false;
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dx = ev.getX() - downX;
            float dy = ev.getY() - downY;
            if (dx * dx + dy * dy > slop() * slop()) moved = true;
        }
        detector.onTouchEvent(ev);
        if (action == MotionEvent.ACTION_UP && moved && !longFired && swipeListener != null) {
            float dx = ev.getX() - downX;
            float dy = ev.getY() - downY;
            float threshold = 56f * getResources().getDisplayMetrics().density;
            if (Math.abs(dx) > threshold || Math.abs(dy) > threshold) {
                if (Math.abs(dy) > Math.abs(dx)) {
                    swipeListener.onSwipe(dy < 0 ? SWIPE_UP : SWIPE_DOWN);
                } else {
                    swipeListener.onSwipe(dx < 0 ? SWIPE_LEFT : SWIPE_RIGHT);
                }
            }
        }
        return true;
    }

    private float slop() {
        return 14f * getResources().getDisplayMetrics().density;
    }
}
