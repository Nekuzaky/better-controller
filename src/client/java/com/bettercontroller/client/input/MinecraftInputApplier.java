package com.bettercontroller.client.input;

import com.bettercontroller.BetterControllerMod;
import com.bettercontroller.client.mixin.KeyBindingAccessor;
import com.bettercontroller.client.translation.GameplayInputFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.input.Input;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;


public final class MinecraftInputApplier {

    private static boolean warnedMissingBoundKeyAccessor;

    private boolean previousJumpHeld;
    private boolean previousAttackHeld;
    private boolean previousUseHeld;

    public void applyGameplayFrame(MinecraftClient client, GameplayInputFrame frame, boolean debugLogging) {
        if (client == null || client.options == null || client.player == null || frame == null) {
            return;
        }

        setHold(client.options.forwardKey, frame.moveForward());
        setHold(client.options.backKey, frame.moveBackward());
        setHold(client.options.leftKey, frame.moveLeft());
        setHold(client.options.rightKey, frame.moveRight());
        applyJumpKey(client.options.jumpKey, frame.jumpHeld());
        setHold(client.options.sprintKey, frame.sprintHeld());
        setHold(client.options.sneakKey, frame.sneakHeld());
        applyActionKey(client.options.attackKey, frame.attackHeld(), true);
        applyActionKey(client.options.useKey, frame.useHeld(), false);
        setHold(client.options.playerListKey, frame.playerListHeld());

        applyTaps(client, frame);
        applyHotbar(client, frame);
    }

    public void applyLookDelta(MinecraftClient client, double deltaX, double deltaY) {
        if (client == null || client.player == null) {
            return;
        }
        if (deltaX == 0.0D && deltaY == 0.0D) {
            return;
        }
        client.player.changeLookDirection(deltaX, deltaY);
    }

    public void releaseGameplayHolds(MinecraftClient client) {
        if (client == null || client.options == null) {
            return;
        }

        setHold(client.options.forwardKey, false);
        setHold(client.options.backKey, false);
        setHold(client.options.leftKey, false);
        setHold(client.options.rightKey, false);
        setHold(client.options.jumpKey, false);
        setHold(client.options.sprintKey, false);
        setHold(client.options.sneakKey, false);
        setHold(client.options.attackKey, false);
        setHold(client.options.useKey, false);
        setHold(client.options.playerListKey, false);

        previousJumpHeld = false;
        previousAttackHeld = false;
        previousUseHeld = false;
    }

    private void applyTaps(MinecraftClient client, GameplayInputFrame frame) {
        if (frame.inventoryTap()) tap(client.options.inventoryKey);
        if (frame.swapHandsTap()) tap(client.options.swapHandsKey);
        if (frame.dropTap()) tap(client.options.dropKey);
        if (frame.chatTap()) tap(client.options.chatKey);
        if (frame.perspectiveTap()) tap(client.options.togglePerspectiveKey);
        if (frame.pickBlockTap()) tap(client.options.pickItemKey);
        if (frame.pauseTap()) openPauseMenu(client);
    }

    private static void applyHotbar(MinecraftClient client, GameplayInputFrame frame) {
        if (frame.hotbarStep() != 0) {
            cycleSlot(client, frame.hotbarStep());
        }
        if (frame.hotbarSelect() >= 0) {
            selectSlot(client, frame.hotbarSelect());
        }
    }

    /**
     * Applies the stick deflection over the values vanilla just derived from the key bindings.
     *
     * <p>Movement still goes through those key bindings, so the rising-edge behaviour Minecraft
     * relies on - creative double-tap fly - keeps working. This only replaces the magnitude, and it
     * has to run from KeyboardInputMixin rather than from the client tick: KeyboardInput.tick()
     * recomputes both fields from the key bindings later in the same tick, which used to discard
     * the stick's deflection entirely.
     *
     * <p>Sideways is negated because vanilla treats a positive value as strafing left while the
     * stick reports positive to the right, and the sneak slow-down is re-applied because vanilla
     * applies it just before this runs.
     */
    public void applyAnalogMovement(Input input, GameplayInputFrame frame, boolean slowDown, float slowDownFactor) {
        if (input == null || frame == null) {
            return;
        }
        if (!shouldOverrideMovement(frame.processedMoveX(), frame.processedMoveY())) {
            return;
        }
        setMovementVector(input, -frame.processedMoveX(), -frame.processedMoveY(), slowDown, slowDownFactor);
    }

    /**
     * Whether the stick should take over the movement vanilla just computed.
     *
     * <p>This runs after {@code KeyboardInput.tick}, so writing unconditionally would overwrite the
     * keyboard's own movement with the stick's - and a centred stick reads zero. A player with a
     * controller plugged in but their hands on the keyboard would simply stop moving. Inside the
     * deadzone the translator produces exactly zero, so an idle stick leaves vanilla alone.
     */
    static boolean shouldOverrideMovement(float moveX, float moveY) {
        return moveX != 0.0F || moveY != 0.0F;
    }

    private static void cycleSlot(MinecraftClient client, int offset) {
        if (client.player == null) {
            return;
        }
        int currentSlot = client.player.getInventory().selectedSlot;
        int nextSlot = Math.floorMod(currentSlot + offset, 9);
        selectSlot(client, nextSlot);
    }

    private static void selectSlot(MinecraftClient client, int slot) {
        if (client.player == null) {
            return;
        }
        int nextSlot = Math.floorMod(slot, 9);
        client.player.getInventory().selectedSlot = nextSlot;
        if (client.player.networkHandler != null) {
            client.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(nextSlot));
        }
    }

    private static void openPauseMenu(MinecraftClient client) {
        if (client.currentScreen == null) {
            client.setScreen(new GameMenuScreen(true));
        }
    }

    private static void tap(KeyBinding keyBinding) {
        InputUtil.Key boundKey = readBoundKey(keyBinding);
        if (boundKey != null) {
            KeyBinding.onKeyPressed(boundKey);
            return;
        }
        keyBinding.setPressed(true);
        keyBinding.setPressed(false);
    }

    private static void setHold(KeyBinding keyBinding, boolean pressed) {
        keyBinding.setPressed(pressed);
    }

    private void applyJumpKey(KeyBinding keyBinding, boolean held) {
        if (held && !previousJumpHeld) {
            tap(keyBinding);
        }
        setHold(keyBinding, held);
        previousJumpHeld = held;
    }

    private void applyActionKey(KeyBinding keyBinding, boolean held, boolean isAttack) {
        boolean wasHeld = isAttack ? previousAttackHeld : previousUseHeld;
        if (held && !wasHeld) {
            tap(keyBinding);
        }
        setHold(keyBinding, held);
        if (isAttack) {
            previousAttackHeld = held;
        } else {
            previousUseHeld = held;
        }
    }

    private static InputUtil.Key readBoundKey(KeyBinding keyBinding) {
        if (keyBinding instanceof KeyBindingAccessor accessor) {
            return accessor.bettercontroller$getBoundKey();
        }
        if (!warnedMissingBoundKeyAccessor) {
            warnedMissingBoundKeyAccessor = true;
            BetterControllerMod.LOGGER.warn("KeyBinding accessor unavailable; controller tap fallback path is active.");
        }
        return null;
    }

    private static void setMovementVector(Input input, float moveX, float moveY, boolean slowDown, float slowDownFactor) {
        if (input == null) {
            return;
        }

        float clampedX = Math.max(-1.0F, Math.min(1.0F, moveX));
        float clampedY = Math.max(-1.0F, Math.min(1.0F, moveY));
        float lengthSquared = (clampedX * clampedX) + (clampedY * clampedY);
        if (lengthSquared > 1.0F) {
            float scale = (float) (1.0D / Math.sqrt(lengthSquared));
            clampedX *= scale;
            clampedY *= scale;
        }

        if (slowDown) {
            clampedX *= slowDownFactor;
            clampedY *= slowDownFactor;
        }

        input.movementSideways = clampedX;
        input.movementForward = clampedY;
    }
}
