package com.bettercontroller.client.polling;

import com.bettercontroller.BetterControllerMod;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to GLFW's gamepad API from inside a game that predates it.
 *
 * <p>Minecraft 1.12.2 runs on LWJGL 2, whose only controller support is JInput: no standardised
 * button layout, no SDL mapping database, no device GUID. Everything this mod does is built on the
 * GLFW gamepad API instead, so 1.12.2 gets GLFW brought along rather than a lesser mod.
 *
 * <p>The two libraries cannot share a classpath. Both define {@code org.lwjgl.BufferUtils} and
 * {@code org.lwjgl.PointerBuffer}, and LWJGL 3's PointerBuffer is a different class with a
 * different API - whichever jar loads first wins, and if that is LWJGL 3 then the game's own
 * rendering breaks. LWJGL 2 also seals the {@code org.lwjgl} package, and in Java 8
 * {@code ClassLoader.getPackage} delegates to the parent, so even a child-first loader inherits
 * that seal and refuses to define the class.
 *
 * <p>So LWJGL 3 is loaded by a classloader whose parent is the bootstrap loader. It sees
 * {@code java.*} and nothing else - not Minecraft, not Forge, not LWJGL 2 - and nothing it defines
 * can leak back the other way. Everything below therefore goes through reflection.
 *
 * <p>GLFW never creates a window here. {@code glfwInit} alone is enough for joystick polling, and
 * the game's own LWJGL 2 window is left completely alone.
 */
public final class GlfwBridge {

    /** Bundled inside the mod jar and unpacked on first use; a URLClassLoader needs real files. */
    private static final String[] BUNDLED_LIBRARIES = {
        "lwjgl.jar",
        "lwjgl-glfw.jar",
        "lwjgl-natives-windows.jar",
        "lwjgl-glfw-natives-windows.jar",
        "lwjgl-natives-linux.jar",
        "lwjgl-glfw-natives-linux.jar",
        "lwjgl-natives-macos.jar",
        "lwjgl-glfw-natives-macos.jar",
    };

    private static final String LIBRARY_RESOURCE_ROOT = "/bettercontroller-lib/";

    public static final int JOYSTICK_LAST = 15;
    public static final int AXIS_COUNT = 6;
    public static final int BUTTON_COUNT = 15;

    private static GlfwBridge instance;

    private final ClassLoader loader;
    private final Class<?> glfw;
    private final Method joystickPresent;
    private final Method joystickIsGamepad;
    private final Method joystickName;
    private final Method joystickGuid;
    private final Method getGamepadState;
    private final Method updateGamepadMappings;
    private final Method terminate;
    private final Method stateCreate;
    private final Method stateAxis;
    private final Method stateButton;

    private Object reusableState;
    private boolean initialised;

    private GlfwBridge(ClassLoader loader) throws ReflectiveOperationException {
        this.loader = loader;
        this.glfw = loader.loadClass("org.lwjgl.glfw.GLFW");
        Class<?> gamepadState = loader.loadClass("org.lwjgl.glfw.GLFWGamepadState");
        this.joystickPresent = glfw.getMethod("glfwJoystickPresent", int.class);
        this.joystickIsGamepad = glfw.getMethod("glfwJoystickIsGamepad", int.class);
        this.joystickName = glfw.getMethod("glfwGetJoystickName", int.class);
        this.joystickGuid = glfw.getMethod("glfwGetJoystickGUID", int.class);
        this.getGamepadState = glfw.getMethod("glfwGetGamepadState", int.class, gamepadState);
        this.updateGamepadMappings = glfw.getMethod("glfwUpdateGamepadMappings", ByteBuffer.class);
        this.terminate = glfw.getMethod("glfwTerminate");
        this.stateCreate = gamepadState.getMethod("create");
        this.stateAxis = gamepadState.getMethod("axes", int.class);
        this.stateButton = gamepadState.getMethod("buttons", int.class);
    }

    /**
     * Brings GLFW up, or returns null if it cannot be had on this machine. A null return is a
     * normal outcome rather than an error: the mod stays dormant and the game plays as vanilla.
     */
    public static synchronized GlfwBridge createOrNull(Path modConfigDirectory) {
        if (instance != null) {
            return instance;
        }
        try {
            URL[] urls = unpackLibraries(modConfigDirectory.resolve("lib"));
            if (urls.length == 0) {
                BetterControllerMod.logger().warn(
                    "BetterController: no bundled GLFW libraries in the mod jar; controller support is off.");
                return null;
            }
            // Bootstrap parent. See the class javadoc - this is what keeps LWJGL 3 away from the
            // game's LWJGL 2, in both directions.
            GlfwBridge bridge = new GlfwBridge(new URLClassLoader(urls, null));
            if (!bridge.init()) {
                return null;
            }
            instance = bridge;
            return bridge;
        } catch (Throwable failure) {
            BetterControllerMod.logger().warn(
                "BetterController: could not start GLFW; controller support is off.", failure);
            return null;
        }
    }

    private boolean init() throws ReflectiveOperationException {
        initialised = Boolean.TRUE.equals(glfw.getMethod("glfwInit").invoke(null));
        if (!initialised) {
            BetterControllerMod.logger().warn("BetterController: glfwInit() failed; controller support is off.");
            return false;
        }
        reusableState = stateCreate.invoke(null);
        BetterControllerMod.logger().info("BetterController: GLFW up for controller input, no window created.");
        return true;
    }

    public boolean joystickPresent(int slot) {
        return Boolean.TRUE.equals(invoke(joystickPresent, slot));
    }

    public boolean joystickIsGamepad(int slot) {
        return Boolean.TRUE.equals(invoke(joystickIsGamepad, slot));
    }

    public String joystickName(int slot) {
        Object name = invoke(joystickName, slot);
        return name == null ? "" : name.toString();
    }

    public String joystickGuid(int slot) {
        Object guid = invoke(joystickGuid, slot);
        return guid == null ? "" : guid.toString();
    }

    /**
     * Reads one pad into the caller's arrays. The state struct is reused between calls because
     * this runs every frame, and allocating an off-heap struct per frame would be wasteful.
     */
    public boolean readGamepad(int slot, float[] axes, boolean[] buttons) {
        if (!initialised || reusableState == null) {
            return false;
        }
        try {
            if (!Boolean.TRUE.equals(getGamepadState.invoke(null, slot, reusableState))) {
                return false;
            }
            for (int axis = 0; axis < Math.min(axes.length, AXIS_COUNT); axis++) {
                axes[axis] = ((Number) stateAxis.invoke(reusableState, axis)).floatValue();
            }
            for (int button = 0; button < Math.min(buttons.length, BUTTON_COUNT); button++) {
                buttons[button] = ((Number) stateButton.invoke(reusableState, button)).intValue() == 1;
            }
            return true;
        } catch (ReflectiveOperationException failure) {
            return false;
        }
    }

    /**
     * Feeds SDL mapping lines to GLFW so pads it does not recognise become usable.
     *
     * <p>The string has to be encoded off-heap by LWJGL 3's own MemoryUtil, which lives in the
     * isolated loader, so that goes through reflection too.
     */
    public boolean updateGamepadMappings(String mappings) {
        if (!initialised || mappings == null || mappings.isEmpty()) {
            return false;
        }
        try {
            Class<?> memoryUtil = loader.loadClass("org.lwjgl.system.MemoryUtil");
            Object encoded = memoryUtil.getMethod("memUTF8", CharSequence.class, boolean.class)
                .invoke(null, mappings, true);
            try {
                return Boolean.TRUE.equals(updateGamepadMappings.invoke(null, encoded));
            } finally {
                memoryUtil.getMethod("memFree", Buffer.class).invoke(null, encoded);
            }
        } catch (ReflectiveOperationException failure) {
            BetterControllerMod.logger().warn("BetterController: could not apply gamepad mappings.", failure);
            return false;
        }
    }

    public void shutdown() {
        if (!initialised) {
            return;
        }
        try {
            terminate.invoke(null);
        } catch (ReflectiveOperationException ignored) {
            // Nothing useful to do while the game is closing.
        }
        initialised = false;
    }

    private Object invoke(Method method, int slot) {
        if (!initialised) {
            return null;
        }
        try {
            return method.invoke(null, slot);
        } catch (ReflectiveOperationException failure) {
            return null;
        }
    }

    /**
     * Copies the bundled jars out of the mod jar so a URLClassLoader can read them. Natives for
     * other platforms are simply absent from the jar on those builds; a missing entry is not a
     * failure.
     */
    private static URL[] unpackLibraries(Path libraryDirectory) throws IOException {
        Files.createDirectories(libraryDirectory);
        List<URL> urls = new ArrayList<URL>();
        for (String library : BUNDLED_LIBRARIES) {
            InputStream source = GlfwBridge.class.getResourceAsStream(LIBRARY_RESOURCE_ROOT + library);
            if (source == null) {
                continue;
            }
            try {
                Path target = libraryDirectory.resolve(library);
                if (!Files.exists(target)) {
                    OutputStream sink = Files.newOutputStream(target);
                    try {
                        copy(source, sink);
                    } finally {
                        sink.close();
                    }
                }
                urls.add(target.toUri().toURL());
            } finally {
                source.close();
            }
        }
        return urls.toArray(new URL[0]);
    }

    private static void copy(InputStream source, OutputStream sink) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = source.read(buffer)) != -1) {
            sink.write(buffer, 0, read);
        }
    }
}
