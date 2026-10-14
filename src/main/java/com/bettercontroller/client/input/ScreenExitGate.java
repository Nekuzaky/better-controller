package com.bettercontroller.client.input;

import com.bettercontroller.client.translation.GameplayInputFrame;

/**
 * Keeps the button that closed a screen from acting in the world.
 *
 * <p>The gameplay translator is not run while a screen is open, so on the first tick back in the
 * world its idea of what was held is whatever it was before the screen opened. Every button
 * that is down at that moment therefore reads as a fresh press: the B that closed the pause menu
 * drops the held item, and the A on "Back to Game" jumps.
 *
 * <p>So the first tick back is only observed. Its taps are dropped - the translator has updated
 * its edge state by then, so the next tick reports only genuine presses - and any hold that is
 * down is recorded and kept off until it is let go. Movement and the camera are left alone: a
 * stick still pushed when a menu closes is the player walking, not a misfire.
 */
final class ScreenExitGate {
    private boolean armed;
    private boolean jump;
    private boolean sprint;
    private boolean sneak;
    private boolean attack;
    private boolean use;
    private boolean playerList;

    /** The next frame is the first one back from a screen. */
    void arm() {
        armed = true;
    }

    void filter(GameplayInputFrame frame) {
        if (frame == null) {
            return;
        }
        if (armed) {
            armed = false;
            jump = frame.jumpHeld();
            sprint = frame.sprintHeld();
            sneak = frame.sneakHeld();
            attack = frame.attackHeld();
            use = frame.useHeld();
            playerList = frame.playerListHeld();
            frame.setTaps(false, false, false, false, false, false, false);
            frame.setHotbar(0, -1);
        } else {
            // A blocked hold is released for good the first tick it reads up.
            jump &= frame.jumpHeld();
            sprint &= frame.sprintHeld();
            sneak &= frame.sneakHeld();
            attack &= frame.attackHeld();
            use &= frame.useHeld();
            playerList &= frame.playerListHeld();
        }
        frame.setHolds(
            frame.jumpHeld() && !jump,
            frame.sprintHeld() && !sprint,
            frame.sneakHeld() && !sneak,
            frame.attackHeld() && !attack,
            frame.useHeld() && !use,
            frame.playerListHeld() && !playerList
        );
    }
}
