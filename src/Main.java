import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.os.SystemClock;
import android.view.InputEvent;
import android.view.MotionEvent;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;

public class Main {
    private static Object inputManager;
    private static Method injectInputEventMethod;

    private static final int EMU_SCREEN_WIDTH = 1440;
    private static final int EMU_SCREEN_HEIGHT = 3120;
    private static final String SOCKET_NAME = "touch_socket";

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
            System.out.println("1");
            LocalSocket socket = new LocalSocket();
            socket.connect(new LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT));  // :contentReference[oaicite:2]{index=2}

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), "UTF-8"));
            System.out.println("we're in");
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Received: " + line);
                try {
                    JSONObject json = new JSONObject(line);
                    float sourceX = (float) json.getDouble("x");
                    float sourceY = (float) json.getDouble("y");
                    int action = json.getInt("action");
                    int srcWidth = json.getInt("source_width");
                    int srcHeight = json.getInt("source_height");

                    float x = sourceX * EMU_SCREEN_WIDTH / srcWidth;
                    float y = sourceY * EMU_SCREEN_HEIGHT / srcHeight;

                    switch (action) {
                        case 0:
                            injectDownEvent(x, y);
                            break;
                        case 1:
                            injectMoveEvent(x, y);
                            break;
                        case 2:
                            injectUpEvent(x, y);
                            break;
                        default:
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