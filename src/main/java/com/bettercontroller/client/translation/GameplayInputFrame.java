package com.bettercontroller.client.translation;

/**
 * Mutable, single-instance frame reused every tick to avoid allocations in the hot path.
 * Callers must treat the frame as ephemeral - the same instance is reused next tick.
 */
public final class GameplayInputFrame {
    private boolean moveLeft;
    private boolean moveRight;
    private boolean moveForward;
    private boolean moveBackward;
    private boolean jumpHeld;
    private boolean sprintHeld;
    private boolean sneakHeld;
    private boolean attackHeld;
    private boolean useHeld;
    private boolean playerListHeld;
    private boolean inventoryTap;
    private boolean swapHandsTap;
    private boolean dropTap;
    private boolean chatTap;
    private boolean perspectiveTap;
    private boolean pauseTap;
    private boolean pickBlockTap;
    private int hotbarStep;
    private int hotbarSelect;
    private double lookDeltaX;
    private double lookDeltaY;
    private float processedMoveX;
    private float processedMoveY;
    private float processedLookX;
    private float processedLookY;

    public void clear() {
        moveLeft = moveRight = moveForward = moveBackward = false;
        jumpHeld = sprintHeld = sneakHeld = false;
        attackHeld = useHeld = playerListHeld = false;
        inventoryTap = swapHandsTap = dropTap = chatTap = false;
        perspectiveTap = pauseTap = pickBlockTap = false;
        hotbarStep = 0;
        hotbarSelect = -1;
        lookDeltaX = lookDeltaY = 0.0D;
        processedMoveX = processedMoveY = 0.0F;
        processedLookX = processedLookY = 0.0F;
    }

    public void setMovement(boolean left, boolean right, boolean forward, boolean backward, float rawX, float rawY) {
        this.moveLeft = left;
        this.moveRight = right;
        this.moveForward = forward;
        this.moveBackward = backward;
        this.processedMoveX = rawX;
        this.processedMoveY = rawY;
    }

    public void setLook(double deltaX, double deltaY, float rawX, float rawY) {
        this.lookDeltaX = deltaX;
        this.lookDeltaY = deltaY;
        this.processedLookX = rawX;
        this.processedLookY = rawY;
    }

    public void setHolds(boolean jump, boolean sprint, boolean sneak, boolean attack, boolean use, boolean playerList) {
        this.jumpHeld = jump;
        this.sprintHeld = sprint;
        this.sneakHeld = sneak;
        this.attackHeld = attack;
        this.useHeld = use;
        this.playerListHeld = playerList;
    }

    public void setTaps(boolean inventory, boolean swapHands, boolean drop, boolean chat, boolean perspective, boolean pause, boolean pickBlock) {
        this.inventoryTap = inventory;
        this.swapHandsTap = swapHands;
        this.dropTap = drop;
        this.chatTap = chat;
        this.perspectiveTap = perspective;
        this.pauseTap = pause;
        this.pickBlockTap = pickBlock;
    }

    public void setHotbar(int step, int select) {
        this.hotbarStep = step;
        this.hotbarSelect = select;
    }

    public boolean moveLeft() { return moveLeft; }
    public boolean moveRight() { return moveRight; }
    public boolean moveForward() { return moveForward; }
    public boolean moveBackward() { return moveBackward; }
    public boolean jumpHeld() { return jumpHeld; }
    public boolean sprintHeld() { return sprintHeld; }
    public boolean sneakHeld() { return sneakHeld; }
    public boolean attackHeld() { return attackHeld; }
    public boolean useHeld() { return useHeld; }
    public boolean playerListHeld() { return playerListHeld; }
    public boolean inventoryTap() { return inventoryTap; }
    public boolean swapHandsTap() { return swapHandsTap; }
    public boolean dropTap() { return dropTap; }
    public boolean chatTap() { return chatTap; }
    public boolean perspectiveTap() { return perspectiveTap; }
    public boolean pauseTap() { return pauseTap; }
    public boolean pickBlockTap() { return pickBlockTap; }
    public int hotbarStep() { return hotbarStep; }
    public int hotbarSelect() { return hotbarSelect; }
    public double lookDeltaX() { return lookDeltaX; }
    public double lookDeltaY() { return lookDeltaY; }
    public float processedMoveX() { return processedMoveX; }
    public float processedMoveY() { return processedMoveY; }
    public float processedLookX() { return processedLookX; }
    public float processedLookY() { return processedLookY; }
}
