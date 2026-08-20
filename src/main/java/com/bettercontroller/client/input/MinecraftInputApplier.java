package com.bettercontroller.client.input;

import com.bettercontroller.client.translation.GameplayInputFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.play.client.CPacketHeldItemChange;
import net.minecraft.util.MovementInput;

/**
 * Pushes a translated controller frame into the game.
 *
 * <p>Movement goes through the vanilla key bindings rather than being written straight onto the
 * player, so rising-edge behaviour like creative double-tap fly keeps working. The analog
 * magnitude is then laid over the top by {@link #applyAnalogMovement}.
 */
public final class MinecraftInputApplier {

    /**
     * The factor {@code MovementInputFromOptions.updatePlayerMoveState} applies while sneaking.
     * Hardcoded in vanilla rather than passed in, so it has to be repeated here - see
     * {@link #applyAnalogMovement} for why it has to be applied at all.
     */
    private static final float SNEAK_SLOWDOWN = 0.3F;

    private boolean previousJumpHeld;
    private boolean previousAttackHeld;
    private boolean previousUseHeld;

    public void applyGameplayFrame(Minecraft client, GameplayInputFrame frame) {
        if (client == null || client.gameSettings == null || client.player == null || frame == null) {
            return;
        }

        setHold(client.gameSettings.keyBindForward, frame.moveForward());
        setHold(client.gameSettings.keyBindBack, frame.moveBackward());
        setHold(client.gameSettings.keyBindLeft, frame.moveLeft());
        setHold(client.gameSettings.keyBindRight, frame.moveRight());
        applyJumpKey(client.gameSettings.keyBindJump, frame.jumpHeld());
        setHold(client.gameSettings.keyBindSprint, frame.sprintHeld());
        setHold(client.gameSettings.keyBindSneak, frame.sneakHeld());
        applyActionKey(client.gameSettings.keyBindAttack, frame.attackHeld(), true);
        applyActionKey(client.gameSettings.keyBindUseItem, frame.useHeld(), false);
        setHold(client.gameSettings.keyBindPlayerList, frame.playerListHeld());

        applyTaps(client, frame);
        applyHotbar(client, frame);
    }

    /**
     * Lays the stick's deflection over the movement vanilla just derived from those key bindings.
     *
     * <p>Called from {@code InputUpdateEvent}, which Forge fires immediately after
     * {@code updatePlayerMoveState} - the first point where the values survive until
     * {@code EntityPlayerSP} reads them. Writing earlier, during the client tick, is what made the
     * headline feature digital on every other version: vanilla recomputes the vector from the key
     * bindings afterwards and the stick's magnitude was thrown away.
     *
     * <p>Strafe is negated because {@code keyBindLeft} adds +1 to {@code moveStrafe} while the
     * stick reports positive to the right.
     *
     * <p>The sneak slowdown has to be re-applied. Vanilla scales by 0.3 at the end of
     * {@code updatePlayerMoveState}, which has already run by the time this fires, so overwriting
     * the values would silently restore full speed while sneaking. The item-use slowdown does not
     * need this: {@code EntityPlayerSP} applies that one after the event, on whatever is there.
     */
    public void applyAnalogMovement(MovementInput input, GameplayInputFrame frame, boolean sneaking) {
        if (input == null || frame == null) {
            return;
        }
        if (!shouldOverrideMovement(frame.processedMoveX(), frame.processedMoveY())) {
            return;
        }

        float strafe = clampUnit(-frame.processedMoveX());
        float forward = clampUnit(-frame.processedMoveY());

        float lengthSquared = (strafe * strafe) + (forward * forward);
        if (lengthSquared > 1.0F) {
            float scale = (float) (1.0D / Math.sqrt(lengthSquared));
            strafe *= scale;
            forward *= scale;
        }

        if (sneaking) {
            strafe *= SNEAK_SLOWDOWN;
            forward *= SNEAK_SLOWDOWN;
        }

        input.moveStrafe = strafe;
        input.moveForward = forward;
    }

    /**
     * Whether the stick should take over the movement vanilla just computed.
     *
     * <p>This runs after vanilla has built its own vector, so writing unconditionally would
     * overwrite keyboard movement with the stick's - and a centred stick reads zero. A player with
     * a controller merely plugged in would stop moving. Inside the deadzone the translator
     * produces exactly zero, so an idle stick leaves vanilla alone.
     */
    static boolean shouldOverrideMovement(float moveX, float moveY) {
        return moveX != 0.0F || moveY != 0.0F;
    }

    public void applyLookDelta(Minecraft client, double deltaX, double deltaY) {
        if (client == null || client.player == null) {
            return;
        }
        if (deltaX == 0.0D && deltaY == 0.0D) {
            return;
        }
        // Entity.turn takes floats here and applies vanilla's own 0.15 factor internally, the same
        // as the mouse path, so the tuning carries over from the other versions unchanged.
        client.player.turn((float) deltaX, (float) deltaY);
    }

    public void releaseGameplayHolds(Minecraft client) {
        if (client == null || client.gameSettings == null) {
            return;
        }

        setHold(client.gameSettings.keyBindForward, false);
        setHold(client.gameSettings.keyBindBack, false);
        setHold(client.gameSettings.keyBindLeft, false);
        setHold(client.gameSettings.keyBindRight, false);
        setHold(client.gameSettings.keyBindJump, false);
        setHold(client.gameSettings.keyBindSprint, false);
        setHold(client.gameSettings.keyBindSneak, false);
        setHold(client.gameSettings.keyBindAttack, false);
        setHold(client.gameSettings.keyBindUseItem, false);
        setHold(client.gameSettings.keyBindPlayerList, false);

        previousJumpHeld = false;
        previousAttackHeld = false;
        previousUseHeld = false;
    }

    private void applyTaps(Minecraft client, GameplayInputFrame frame) {
        if (frame.inventoryTap()) {
            tap(client.gameSettings.keyBindInventory);
        }
        if (frame.swapHandsTap()) {
            tap(client.gameSettings.keyBindSwapHands);
        }
        if (frame.dropTap()) {
            tap(client.gameSettings.keyBindDrop);
        }
        if (frame.chatTap()) {
            tap(client.gameSettings.keyBindChat);
        }
        if (frame.perspectiveTap()) {
            tap(client.gameSettings.keyBindTogglePerspective);
        }
        if (frame.pickBlockTap()) {
            tap(client.gameSettings.keyBindPickBlock);
        }
        if (frame.pauseTap()) {
            openPauseMenu(client);
        }
    }

    private static void applyHotbar(Minecraft client, GameplayInputFrame frame) {
        if (frame.hotbarStep() != 0) {
            cycleSlot(client, frame.hotbarStep());
        }
        if (frame.hotbarSelect() >= 0) {
            selectSlot(client, frame.hotbarSelect());
        }
    }

    private static void cycleSlot(Minecraft client, int offset) {
        if (client.player == null) {
            return;
        }
        selectSlot(client, Math.floorMod(client.player.inventory.currentItem + offset, 9));
    }

    private static void selectSlot(Minecraft client, int slot) {
        if (client.player == null) {
            return;
        }
        int nextSlot = Math.floorMod(slot, 9);
        client.player.inventory.currentItem = nextSlot;
        if (client.player.connection != null) {
            client.player.connection.sendPacket(new CPacketHeldItemChange(nextSlot));
        }
    }

    private static void openPauseMenu(Minecraft client) {
        if (client.currentScreen == null) {
            client.displayGuiScreen(new GuiIngameMenu());
        }
    }

    /**
     * One press-and-release. {@code KeyBinding.onTick} bumps the binding's press counter, which is
     * what the game drains for discrete actions - setting the held state alone would not register.
     */
    private static void tap(KeyBinding keyBinding) {
        if (keyBinding == null) {
            return;
        }
        KeyBinding.onTick(keyBinding.getKeyCode());
    }

    private static void setHold(KeyBinding keyBinding, boolean pressed) {
        if (keyBinding == null) {
            return;
        }
        KeyBinding.setKeyBindState(keyBinding.getKeyCode(), pressed);
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

    private static float clampUnit(float value) {
        return Math.max(-1.0F, Math.min(1.0F, value));
    }
}
