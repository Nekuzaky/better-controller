package com.bettercontroller.client.input;

import com.bettercontroller.BetterControllerMod;
import com.bettercontroller.client.mixin.InputAccessor;
import com.bettercontroller.client.mixin.KeyBindingAccessor;
import com.bettercontroller.client.translation.GameplayInputFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.phys.Vec2;

public final class MinecraftInputApplier {

    private static boolean warnedMissingBoundKeyAccessor;
    private static boolean warnedMissingMovementVectorAccessor;

    private boolean previousJumpHeld;
    private boolean previousAttackHeld;
    private boolean previousUseHeld;

    public void applyGameplayFrame(Minecraft client, GameplayInputFrame frame, boolean debugLogging) {
        if (client == null || client.options == null || client.player == null || frame == null) {
            return;
        }

        setHold(client.options.keyUp, frame.moveForward());
        setHold(client.options.keyDown, frame.moveBackward());
        setHold(client.options.keyLeft, frame.moveLeft());
        setHold(client.options.keyRight, frame.moveRight());
        applyJumpKey(client.options.keyJump, frame.jumpHeld());
        setHold(client.options.keySprint, frame.sprintHeld());
        setHold(client.options.keyShift, frame.sneakHeld());
        applyActionKey(client.options.keyAttack, frame.attackHeld(), true);
        applyActionKey(client.options.keyUse, frame.useHeld(), false);
        setHold(client.options.keyPlayerList, frame.playerListHeld());

        applyTaps(client, frame);
        applyHotbar(client, frame);
    }

    public void applyLookDelta(Minecraft client, double deltaX, double deltaY) {
        if (client == null || client.player == null) {
            return;
        }
        if (deltaX == 0.0D && deltaY == 0.0D) {
            return;
        }
        client.player.turn(deltaX, deltaY);
    }

    public void releaseGameplayHolds(Minecraft client) {
        if (client == null || client.options == null) {
            return;
        }

        setHold(client.options.keyUp, false);
        setHold(client.options.keyDown, false);
        setHold(client.options.keyLeft, false);
        setHold(client.options.keyRight, false);
        setHold(client.options.keyJump, false);
        setHold(client.options.keySprint, false);
        setHold(client.options.keyShift, false);
        setHold(client.options.keyAttack, false);
        setHold(client.options.keyUse, false);
        setHold(client.options.keyPlayerList, false);

        previousJumpHeld = false;
        previousAttackHeld = false;
        previousUseHeld = false;
    }

    private void applyTaps(Minecraft client, GameplayInputFrame frame) {
        if (frame.inventoryTap()) tap(client.options.keyInventory);
        if (frame.swapHandsTap()) tap(client.options.keySwapOffhand);
        if (frame.dropTap()) tap(client.options.keyDrop);
        if (frame.chatTap()) tap(client.options.keyChat);
        if (frame.perspectiveTap()) tap(client.options.keyTogglePerspective);
        if (frame.pickBlockTap()) tap(client.options.keyPickItem);
        if (frame.pauseTap()) openPauseMenu(client);
    }

    private static void applyHotbar(Minecraft client, GameplayInputFrame frame) {
        if (frame.hotbarStep() != 0) {
            cycleSlot(client, frame.hotbarStep());
        }
        if (frame.hotbarSelect() >= 0) {
            selectSlot(client, frame.hotbarSelect());
        }
    }

    /**
     * Applies the stick deflection over the vector vanilla just derived from the key bindings.
     *
     * <p>Movement still goes through those key bindings, so rising-edge behaviour like creative
     * double-tap fly keeps working. This only replaces the magnitude, and it has to run from
     * KeyboardInputMixin rather than from the client tick: KeyboardInput.tick() rebuilds the vector
     * from those same bindings and normalises it, which used to discard the stick's deflection
     * entirely - every push past the deadzone moved at full speed.
     *
     * <p>Sideways is negated because vanilla builds Vec2(sideways, forward) with a positive value
     * meaning left, while the stick reports positive to the right. Unlike 1.20.1 there is no
     * slow-down to re-apply: LocalPlayer.aiStep scales the vector after this runs.
     */
    public void applyAnalogMovement(ClientInput input, GameplayInputFrame frame) {
        if (input == null || frame == null) {
            return;
        }
        if (!shouldOverrideMovement(frame.processedMoveX(), frame.processedMoveY())) {
            return;
        }
        setMovementVector(input, -frame.processedMoveX(), -frame.processedMoveY());
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

    private static void cycleSlot(Minecraft client, int offset) {
        if (client.player == null) {
            return;
        }
        int currentSlot = client.player.getInventory().getSelectedSlot();
        int nextSlot = Math.floorMod(currentSlot + offset, 9);
        selectSlot(client, nextSlot);
    }

    private static void selectSlot(Minecraft client, int slot) {
        if (client.player == null) {
            return;
        }
        int nextSlot = Math.floorMod(slot, 9);
        client.player.getInventory().setSelectedSlot(nextSlot);
        if (client.player.connection != null) {
            client.player.connection.send(new ServerboundSetCarriedItemPacket(nextSlot));
        }
    }

    private static void openPauseMenu(Minecraft client) {
        if (client.gui.screen() == null) {
            client.gui.setScreen(new PauseScreen(true));
        }
    }

    private static void tap(KeyMapping keyBinding) {
        InputConstants.Key boundKey = readBoundKey(keyBinding);
        if (boundKey != null) {
            KeyMapping.click(boundKey);
            return;
        }
        keyBinding.setDown(true);
        keyBinding.setDown(false);
    }

    private static void setHold(KeyMapping keyBinding, boolean pressed) {
        keyBinding.setDown(pressed);
    }

    private void applyJumpKey(KeyMapping keyBinding, boolean held) {
        if (held && !previousJumpHeld) {
            tap(keyBinding);
        }
        setHold(keyBinding, held);
        previousJumpHeld = held;
    }

    private void applyActionKey(KeyMapping keyBinding, boolean held, boolean isAttack) {
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

    private static InputConstants.Key readBoundKey(KeyMapping keyBinding) {
        if (keyBinding instanceof KeyBindingAccessor accessor) {
            return accessor.bettercontroller$getBoundKey();
        }
        if (!warnedMissingBoundKeyAccessor) {
            warnedMissingBoundKeyAccessor = true;
            BetterControllerMod.LOGGER.warn("KeyMapping accessor unavailable; controller tap fallback path is active.");
        }
        return null;
    }

    private static void setMovementVector(ClientInput input, float moveX, float moveY) {
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

        if (input instanceof InputAccessor accessor) {
            accessor.bettercontroller$setMovementVector(new Vec2(clampedX, clampedY));
            return;
        }
        if (!warnedMissingMovementVectorAccessor) {
            warnedMissingMovementVectorAccessor = true;
            BetterControllerMod.LOGGER.warn("ClientInput accessor unavailable; analog movement vector could not be applied.");
        }
    }
}
