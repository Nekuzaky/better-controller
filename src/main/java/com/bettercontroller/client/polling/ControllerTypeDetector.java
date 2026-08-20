package com.bettercontroller.client.polling;

import java.util.Locale;

public final class ControllerTypeDetector {
    private ControllerTypeDetector() {
    }

    public static ControllerType detect(String joystickName, String joystickGuid) {
        String name = joystickName == null ? "" : joystickName.toLowerCase(Locale.ROOT);
        String guid = joystickGuid == null ? "" : joystickGuid.toLowerCase(Locale.ROOT);
        String combined = name + " " + guid;

        if (containsAny(combined, "xbox", "xinput", "x-box")) {
            return ControllerType.XBOX;
        }
        if (containsAny(combined, "dualshock", "dualsense", "playstation", "ps4", "ps5", "sony")) {
            return ControllerType.PLAYSTATION;
        }
        if (containsAny(combined, "switch", "joy-con", "joycon", "nintendo", "pro controller")) {
            return ControllerType.SWITCH;
        }
        return ControllerType.GENERIC;
    }

    private static boolean containsAny(String text, String... needles) {
        for (String needle : needles) {
            if (text.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
