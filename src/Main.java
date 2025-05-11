import android.os.SystemClock;
import android.view.InputEvent;
import android.view.MotionEvent;
import java.lang.reflect.Method;

public class Main {
    private static Object inputManager;
    private static Method injectInputEventMethod;

    public static void main(String[] args) {
        try {
            initializeInputManager();

            float startX = 400;
            float startY = 1000;
            float endX = 500;
            float endY = 200;
            int steps = 800;

            long downTime = SystemClock.uptimeMillis();
            performSwipe(startX, startY, endX, endY, steps);
            System.out.println(SystemClock.uptimeMillis() - downTime);
        } catch (Exception e) {
            System.out.println("Exception occurred:");
            e.printStackTrace(System.out);
        }
    }

    private static void initializeInputManager() throws Exception {
        Class<?> inputManagerClass;
        try {
            inputManagerClass = Class.forName("android.hardware.input.InputManagerGlobal");
        } catch (ClassNotFoundException e) {
            inputManagerClass = Class.forName("android.hardware.input.InputManager");
        }

        Method getInstanceMethod = inputManagerClass.getDeclaredMethod("getInstance");
        inputManager = getInstanceMethod.invoke(null);

        injectInputEventMethod = inputManagerClass.getMethod("injectInputEvent", InputEvent.class, int.class);
    }

    private static void performSwipe(float startX, float startY, float endX, float endY, int steps) throws Exception {
        injectDownEvent(startX, startY);

        for (int i = 1; i <= steps; i++) {
            float currentX = startX + (endX - startX) * i / steps;
            float currentY = startY + (endY - startY) * i / steps;
            injectMoveEvent(currentX, currentY);
        }

        injectUpEvent(endX, endY);
    }

    private static boolean injectDownEvent(float x, float y) throws Exception {
        MotionEvent motionEvent = _createMotionEvent(MotionEvent.ACTION_DOWN, x, y);
        return _injectEvent(motionEvent);
    }

    private static boolean injectMoveEvent(float x, float y) throws Exception {
        MotionEvent motionEvent = _createMotionEvent(MotionEvent.ACTION_MOVE, x, y);
        return _injectEvent(motionEvent);
    }

    private static boolean injectUpEvent(float x, float y) throws Exception {
        MotionEvent motionEvent = _createMotionEvent(MotionEvent.ACTION_UP, x, y);
        return _injectEvent(motionEvent);
    }

    private static MotionEvent _createMotionEvent(int action, float x, float y) {
        long downTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(downTime, downTime, action, x, y, 0);
        event.setSource(4098); // SOURCE_TOUCHSCREEN
        _setDisplayId(event, 0);
        return event;
    }

    private static boolean _injectEvent(MotionEvent event) throws Exception {
        return (boolean) injectInputEventMethod.invoke(inputManager, event, 0);
    }


    private static void _setDisplayId(InputEvent event, int displayId) {
        try {
            Method setDisplayIdMethod = event.getClass().getMethod("setDisplayId", int.class);
            setDisplayIdMethod.invoke(event, displayId);
        } catch (Exception e) {
            System.out.println("setDisplayId not available");
        }
    }
}