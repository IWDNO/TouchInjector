import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.MotionEvent;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class Main {
    private static Object inputManager;
    private static Method injectInputEventMethod;

    private static final int EMU_SCREEN_WIDTH = 720;
    private static final int EMU_SCREEN_HEIGHT = 1520;
    private static final String SOCKET_NAME = "touch_socket";

    private static class PointerData {
        int id;
        float x;
        float y;

        PointerData(int id, float x, float y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    public static void main(String[] args) {
        try {
            initializeInputManager();
            startClientSocket();
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

    private static void startClientSocket() {
        try {
            LocalSocket socket = new LocalSocket();
            socket.connect(new LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT));

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            System.out.println("we're in");
            String line;
            List<PointerData> activePointers = new ArrayList<>();
            long downTime = 0;

            while ((line = reader.readLine()) != null) {
                System.out.println("Received: " + line);
                try {
                    JSONObject json = new JSONObject(line);
                    int pointerId = json.getInt("pointerId");
                    float sourceX = (float) json.getDouble("x");
                    float sourceY = (float) json.getDouble("y");
                    int action = json.getInt("action");
                    int srcWidth = json.getInt("source_width");
                    int srcHeight = json.getInt("source_height");

                    float x = sourceX * EMU_SCREEN_WIDTH / srcWidth;
                    float y = sourceY * EMU_SCREEN_HEIGHT / srcHeight;

                    PointerData pd = null;
                    for (PointerData p : activePointers) {
                        if (p.id == pointerId) {
                            pd = p;
                            break;
                        }
                    }

                    if (action == 0) { // down
                        if (pd != null) {
                            System.out.println("Error: pointerId " + pointerId + " already exists");
                            continue;
                        }
                        pd = new PointerData(pointerId, x, y);
                        if (activePointers.isEmpty()) {
                            downTime = SystemClock.uptimeMillis();
                        }
                        activePointers.add(pd);
                        int pointerCount = activePointers.size();
                        if (pointerCount == 1) {
                            // ACTION_DOWN
                            MotionEvent event = createMotionEvent(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_DOWN, activePointers);
                            injectEvent(event);
                        } else {
                            // ACTION_POINTER_DOWN
                            int index = pointerCount - 1;
                            int actionCode = MotionEvent.ACTION_POINTER_DOWN | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
                            MotionEvent event = createMotionEvent(downTime, SystemClock.uptimeMillis(), actionCode, activePointers);
                            injectEvent(event);
                        }
                    } else if (action == 1) { // move
                        if (pd == null) {
                            System.out.println("Error: pointerId " + pointerId + " not found for move");
                            continue;
                        }
                        pd.x = x;
                        pd.y = y;
                        MotionEvent event = createMotionEvent(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_MOVE, activePointers);
                        injectEvent(event);
                    } else if (action == 2) { // up
                        if (pd == null) {
                            System.out.println("Error: pointerId " + pointerId + " not found for up");
                            continue;
                        }
                        int index = activePointers.indexOf(pd);
                        int pointerCount = activePointers.size();
                        if (pointerCount == 1) {
                            // ACTION_UP
                            MotionEvent event = createMotionEvent(downTime, SystemClock.uptimeMillis(), MotionEvent.ACTION_UP, activePointers);
                            injectEvent(event);
                            activePointers.remove(pd);
                        } else {
                            // ACTION_POINTER_UP
                            int actionCode = MotionEvent.ACTION_POINTER_UP | (index << MotionEvent.ACTION_POINTER_INDEX_SHIFT);
                            MotionEvent event = createMotionEvent(downTime, SystemClock.uptimeMillis(), actionCode, activePointers);
                            injectEvent(event);
                            activePointers.remove(pd);
                        }
                    } else {
                        System.out.println("Unknown action: " + action);
                    }
                } catch (Exception e) {
                    System.out.println("Failed to parse or inject event:");
                    e.printStackTrace();
                }
            }
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static MotionEvent createMotionEvent(long downTime, long eventTime, int action, List<PointerData> pointers) {
        int pointerCount = pointers.size();
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[pointerCount];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointerCount];

        for (int i = 0; i < pointerCount; i++) {
            PointerData pd = pointers.get(i);
            MotionEvent.PointerProperties pp = new MotionEvent.PointerProperties();
            pp.id = pd.id;
            pp.toolType = MotionEvent.TOOL_TYPE_FINGER;
            props[i] = pp;

            MotionEvent.PointerCoords pc = new MotionEvent.PointerCoords();
            pc.x = pd.x;
            pc.y = pd.y;
            pc.pressure = 1.0f;
            pc.size = 1.0f;
            coords[i] = pc;
        }

        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, pointerCount, props, coords, 0, 0, 1.0f, 1.0f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0);
        _setDisplayId(event, 0);
        return event;
    }

    private static boolean injectEvent(MotionEvent event) throws Exception {
        return (boolean) injectInputEventMethod.invoke(inputManager, event, 0);
    }

    private static void _setDisplayId(MotionEvent event, int displayId) {
        try {
            Method setDisplayIdMethod = event.getClass().getMethod("setDisplayId", int.class);
            setDisplayIdMethod.invoke(event, displayId);
        } catch (Exception e) {
            System.out.println("setDisplayId not available");
        }
    }
}