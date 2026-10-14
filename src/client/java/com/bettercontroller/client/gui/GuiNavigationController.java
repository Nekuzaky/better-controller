package com.bettercontroller.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class GuiNavigationController {
    private static final float SCROLL_STICK_DEADZONE = 0.20F;
    private static final long SCROLL_BASE_INTERVAL_MS = 110L;

    private final ControllerInventorySelectionState inventorySelectionState;
    private final List<GuiFocusElement> focusElementsBuffer = new ArrayList<>();
    private final List<Slot> handledSlotsBuffer = new ArrayList<>();
    private final CursorCaptureManager cursorCapture = new CursorCaptureManager();
    private Screen lastScreen;
    private int selectedIndex = -1;
    private int selectedHandledSlotId = -1;
    private boolean preferListFocus;
    private long lastAnalogScrollMs;
    private NavigationMode navigationMode = NavigationMode.WIDGETS;

    public GuiNavigationController(ControllerInventorySelectionState inventorySelectionState) {
        this.inventorySelectionState = inventorySelectionState == null
            ? new ControllerInventorySelectionState()
            : inventorySelectionState;
    }

    public void onScreenTick(
        Minecraft client,
        GuiInputFrame inputFrame,
        Consumer<Boolean> virtualKeyboardRequest,
        boolean debugLogging
    ) {
        if (client == null || client.gui.screen() == null || inputFrame == null) {
            return;
        }
        Screen screen = client.gui.screen();
        boolean screenChanged = screen != lastScreen;
        lastScreen = screen;

        if (inputFrame.anyNavigation()) {
            cursorCapture.noteControllerInput();
        }

        ObjectSelectionList<?> listWidget = findListWidget(screen);
        collectFocusElements(screen, focusElementsBuffer);
        if (screenChanged) {
            handleScreenChange(screen);
        }
        selectedIndex = WidgetNavigator.ensureValidSelection(screen, focusElementsBuffer, selectedIndex);

        AbstractContainerScreen<?> handledScreen = screen instanceof AbstractContainerScreen<?> h ? h : null;
        collectHandledSlots(handledScreen, handledSlotsBuffer);
        if (!handledSlotsBuffer.isEmpty()) {
            initializeHandledSlotSelection(client, handledSlotsBuffer, screenChanged);
        }

        updateListFocusPreference(listWidget, inputFrame, screen, screenChanged);
        navigationMode = resolveNavigationMode(
            screen, listWidget, focusElementsBuffer, handledScreen, handledSlotsBuffer, inputFrame, screenChanged
        );

        dispatchNavigation(client, screen, listWidget, focusElementsBuffer, handledScreen, handledSlotsBuffer, inputFrame, screenChanged, virtualKeyboardRequest);

        cursorCapture.sync(client, navigationMode != NavigationMode.TEXT_INPUT);
    }

    public void reset() {
        // Released before forgetScreen(), which would otherwise clear the flag release() checks.
        cursorCapture.release(Minecraft.getInstance());
        forgetScreen();
        lastAnalogScrollMs = 0L;
    }

    /**
     * Lets go of the screen that just closed - the screen itself, its widgets and its slots - so
     * none of it stays reachable while the player is in the world. The cursor is only forgotten,
     * not released: Minecraft has already locked it for gameplay, and showing it would break that.
     */
    public void forgetScreen() {
        lastScreen = null;
        focusElementsBuffer.clear();
        handledSlotsBuffer.clear();
        selectedIndex = -1;
        selectedHandledSlotId = -1;
        preferListFocus = false;
        navigationMode = NavigationMode.WIDGETS;
        cursorCapture.forget();
        inventorySelectionState.clear();
    }

    /**
     * Scrolls the current screen using a normalized analog axis value (typically right stick Y).
     * Stick pushed up produces a negative value -> scrolls content up. The call rate is throttled
     * proportionally to the stick magnitude so light deflection scrolls slowly, full deflection fast.
     */
    public void handleAnalogScroll(Minecraft client, float stickY) {
        if (client == null || client.gui.screen() == null) {
            lastAnalogScrollMs = 0L;
            return;
        }
        float magnitude = Math.abs(stickY);
        if (magnitude < SCROLL_STICK_DEADZONE) {
            lastAnalogScrollMs = 0L;
            return;
        }
        long now = System.currentTimeMillis();
        long interval = (long) (SCROLL_BASE_INTERVAL_MS / Math.max(0.25F, magnitude));
        if (now - lastAnalogScrollMs < interval) {
            return;
        }
        lastAnalogScrollMs = now;

        Screen screen = client.gui.screen();
        double mouseX = screen.width / 2.0D;
        double mouseY = screen.height / 2.0D;
        double verticalAmount = stickY < 0.0F ? 1.0D : -1.0D;
        screen.mouseScrolled(mouseX, mouseY, 0.0D, verticalAmount);
        cursorCapture.noteControllerInput();
    }

    private void handleScreenChange(Screen screen) {
        selectedIndex = -1;
        selectedHandledSlotId = -1;
        inventorySelectionState.onScreenChanged(screen);
        if (focusElementsBuffer.isEmpty()) {
            return;
        }
        selectedIndex = WidgetNavigator.findInitialIndex(screen, focusElementsBuffer);
        if (screen instanceof BetterControllerSettingsScreen) {
            selectedIndex = WidgetNavigator.preferNonSliderIndex(focusElementsBuffer, selectedIndex);
        }
    }

    private void updateListFocusPreference(
        ObjectSelectionList<?> listWidget,
        GuiInputFrame inputFrame,
        Screen screen,
        boolean screenChanged
    ) {
        if (screenChanged) {
            preferListFocus = listWidget != null;
        } else if (listWidget == null) {
            preferListFocus = false;
        } else if (inputFrame.left() || inputFrame.right() || inputFrame.tabNext() || inputFrame.tabPrev()) {
            preferListFocus = false;
        } else if (inputFrame.up() || inputFrame.down() || inputFrame.pageNext() || inputFrame.pagePrev()) {
            preferListFocus = true;
        } else if (inputFrame.confirm() && screen.getFocused() == listWidget) {
            preferListFocus = true;
        }
    }

    private NavigationMode resolveNavigationMode(
        Screen screen,
        ObjectSelectionList<?> listWidget,
        List<GuiFocusElement> focusElements,
        AbstractContainerScreen<?> handledScreen,
        List<Slot> handledSlots,
        GuiInputFrame inputFrame,
        boolean screenChanged
    ) {
        boolean hasHandledSlots = handledScreen != null && !handledSlots.isEmpty();
        boolean hasFocusableWidgets = !focusElements.isEmpty();
        NavigationDirection direction = resolveDirectionalInput(inputFrame);

        if (screenChanged) {
            navigationMode = resolveInitialMode(screen, listWidget, hasFocusableWidgets, hasHandledSlots);
        }
        if (handleTextInputMode(screen, hasHandledSlots, direction)) {
            return NavigationMode.TEXT_INPUT;
        }
        navigationMode = applyFocusOverrides(screen, listWidget, hasFocusableWidgets);
        return finalizeMode(screen, listWidget, focusElements, handledSlots, inputFrame, direction, hasFocusableWidgets, hasHandledSlots);
    }

    private boolean handleTextInputMode(Screen screen, boolean hasHandledSlots, NavigationDirection direction) {
        boolean focusedTextInput = screen.getFocused() instanceof EditBox;
        if (focusedTextInput && direction != null) {
            screen.setFocused(null);
            if (navigationMode == NavigationMode.TEXT_INPUT) {
                navigationMode = hasHandledSlots ? NavigationMode.INVENTORY : NavigationMode.WIDGETS;
            }
            return false;
        }
        return focusedTextInput;
    }

    private NavigationMode applyFocusOverrides(Screen screen, ObjectSelectionList<?> listWidget, boolean hasFocusableWidgets) {
        if (screen.getFocused() == listWidget && listWidget != null) {
            return NavigationMode.LIST;
        }
        if (screen.getFocused() instanceof AbstractWidget && hasFocusableWidgets) {
            return NavigationMode.WIDGETS;
        }
        return navigationMode;
    }

    private NavigationMode finalizeMode(
        Screen screen,
        ObjectSelectionList<?> listWidget,
        List<GuiFocusElement> focusElements,
        List<Slot> handledSlots,
        GuiInputFrame inputFrame,
        NavigationDirection direction,
        boolean hasFocusableWidgets,
        boolean hasHandledSlots
    ) {
        if (navigationMode == NavigationMode.INVENTORY) {
            if (!hasHandledSlots) {
                return resolveFallbackMode(screen, listWidget, focusElements, false);
            }
            if ((inputFrame.tabNext() || inputFrame.tabPrev()) && (hasFocusableWidgets || listWidget != null)) {
                return resolveFallbackMode(screen, listWidget, focusElements, true);
            }
            if (direction != null
                && !canMoveInventorySelection(handledSlots, direction)
                && (hasFocusableWidgets || listWidget != null)) {
                return resolveFallbackMode(screen, listWidget, focusElements, true);
            }
            return NavigationMode.INVENTORY;
        }
        if (navigationMode == NavigationMode.LIST && listWidget != null) {
            if (!preferListFocus && direction != null && direction.horizontal()) {
                if (hasHandledSlots) return NavigationMode.INVENTORY;
                return hasFocusableWidgets ? NavigationMode.WIDGETS : NavigationMode.LIST;
            }
            return NavigationMode.LIST;
        }
        if (hasFocusableWidgets) {
            if (hasHandledSlots
                && navigationMode == NavigationMode.WIDGETS
                && direction != null
                && !canMoveWidgetSelection(screen, focusElements, direction)) {
                return NavigationMode.INVENTORY;
            }
            return NavigationMode.WIDGETS;
        }
        if (listWidget != null && preferListFocus) {
            return NavigationMode.LIST;
        }
        return hasHandledSlots ? NavigationMode.INVENTORY : NavigationMode.WIDGETS;
    }

    private static NavigationMode resolveInitialMode(
        Screen screen,
        ObjectSelectionList<?> listWidget,
        boolean hasFocusableWidgets,
        boolean hasHandledSlots
    ) {
        if (screen != null && screen.getFocused() instanceof EditBox) {
            return NavigationMode.TEXT_INPUT;
        }
        if (screen != null && listWidget != null && screen.getFocused() == listWidget) {
            return NavigationMode.LIST;
        }
        if (hasHandledSlots) return NavigationMode.INVENTORY;
        if (hasFocusableWidgets) return NavigationMode.WIDGETS;
        if (listWidget != null) return NavigationMode.LIST;
        return NavigationMode.WIDGETS;
    }

    private NavigationMode resolveFallbackMode(
        Screen screen,
        ObjectSelectionList<?> listWidget,
        List<GuiFocusElement> focusElements,
        boolean hasHandledSlots
    ) {
        if (screen != null && screen.getFocused() instanceof EditBox) {
            return NavigationMode.TEXT_INPUT;
        }
        if (listWidget != null && (preferListFocus || screen.getFocused() == listWidget)) {
            return NavigationMode.LIST;
        }
        if (focusElements != null && !focusElements.isEmpty()) {
            return NavigationMode.WIDGETS;
        }
        return hasHandledSlots ? NavigationMode.INVENTORY : NavigationMode.WIDGETS;
    }

    private boolean canMoveInventorySelection(List<Slot> slots, NavigationDirection direction) {
        Slot current = SlotNavigator.findSlotById(slots, selectedHandledSlotId);
        if (current == null) {
            return true;
        }
        int nextSlotId = SlotNavigator.findDirectionalSlot(slots, current.index, direction.x(), direction.y());
        return nextSlotId != current.index;
    }

    private boolean canMoveWidgetSelection(Screen screen, List<GuiFocusElement> focusElements, NavigationDirection direction) {
        if (selectedIndex < 0 || selectedIndex >= focusElements.size()) {
            return false;
        }
        if (screen instanceof BetterControllerSettingsScreen) {
            if (direction.vertical()) {
                return focusElements.size() > 1;
            }
            return focusElements.get(selectedIndex).widget() instanceof AbstractSliderButton;
        }
        AbstractWidget selectedWidget = focusElements.get(selectedIndex).widget();
        if (selectedWidget instanceof AbstractSliderButton && direction.horizontal()) {
            return true;
        }
        int nextIndex = WidgetNavigator.findDirectionalIndex(focusElements, selectedIndex, direction.x(), direction.y());
        return nextIndex != selectedIndex;
    }

    private void dispatchNavigation(
        Minecraft client,
        Screen screen,
        ObjectSelectionList<?> listWidget,
        List<GuiFocusElement> focusElements,
        AbstractContainerScreen<?> handledScreen,
        List<Slot> handledSlots,
        GuiInputFrame inputFrame,
        boolean screenChanged,
        Consumer<Boolean> virtualKeyboardRequest
    ) {
        switch (navigationMode) {
            case INVENTORY -> {
                if (handledScreen == null || handledSlots.isEmpty()) {
                    navigationMode = resolveFallbackMode(screen, listWidget, focusElements, false);
                    handleWidgetOrListNavigation(screen, listWidget, focusElements, inputFrame, virtualKeyboardRequest);
                } else {
                    handleHandledScreenNavigation(client, handledScreen, handledSlots, inputFrame, screenChanged);
                }
            }
            case LIST -> handleListNavigation(screen, listWidget, inputFrame);
            case TEXT_INPUT -> handleTextInputNavigation(screen, inputFrame, virtualKeyboardRequest);
            default -> handleWidgetOrListNavigation(screen, listWidget, focusElements, inputFrame, virtualKeyboardRequest);
        }
    }

    private void initializeHandledSlotSelection(Minecraft client, List<Slot> slots, boolean screenChanged) {
        if (slots.isEmpty()) return;
        if (screenChanged || SlotNavigator.findSlotById(slots, selectedHandledSlotId) == null) {
            Slot initial = SlotNavigator.resolveInitialSlot(client, slots);
            if (initial != null) {
                selectedHandledSlotId = initial.index;
            }
        }
        if (SlotNavigator.findSlotById(slots, selectedHandledSlotId) == null) {
            selectedHandledSlotId = slots.get(0).index;
        }
    }

    private void handleTextInputNavigation(Screen screen, GuiInputFrame inputFrame, Consumer<Boolean> virtualKeyboardRequest) {
        if (inputFrame.confirm() && screen.getFocused() instanceof EditBox textFieldWidget) {
            triggerConfirm(GuiFocusElement.fromWidget(textFieldWidget), virtualKeyboardRequest);
        }
        if (inputFrame.back()) triggerBack(screen);
        if (inputFrame.tabNext()) pressKey(screen, GLFW.GLFW_KEY_TAB);
        if (inputFrame.tabPrev()) pressKey(screen, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT);
        if (inputFrame.pageNext()) pressKey(screen, GLFW.GLFW_KEY_PAGE_DOWN);
        if (inputFrame.pagePrev()) pressKey(screen, GLFW.GLFW_KEY_PAGE_UP);
    }

    private void handleWidgetOrListNavigation(
        Screen screen,
        ObjectSelectionList<?> listWidget,
        List<GuiFocusElement> focusElements,
        GuiInputFrame inputFrame,
        Consumer<Boolean> virtualKeyboardRequest
    ) {
        if (navigationMode == NavigationMode.LIST && listWidget != null) {
            handleListNavigation(screen, listWidget, inputFrame);
            return;
        }
        if (focusElements.isEmpty()) {
            handleEmptyWidgetNavigation(screen, inputFrame);
            return;
        }

        selectedIndex = WidgetNavigator.ensureValidSelection(screen, focusElements, selectedIndex);
        WidgetNavigator.applySelection(screen, focusElements, selectedIndex);

        if (screen instanceof BetterControllerSettingsScreen settingsScreen) {
            handleSettingsNavigation(settingsScreen, focusElements, inputFrame, virtualKeyboardRequest);
            return;
        }

        handleStandardWidgetNavigation(screen, focusElements, inputFrame, virtualKeyboardRequest);
    }

    private void handleEmptyWidgetNavigation(Screen screen, GuiInputFrame inputFrame) {
        if (inputFrame.back()) triggerBack(screen);
        if (inputFrame.tabNext()) pressKey(screen, GLFW.GLFW_KEY_TAB);
        if (inputFrame.tabPrev()) pressKey(screen, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT);
        if (inputFrame.pageNext()) pressKey(screen, GLFW.GLFW_KEY_PAGE_DOWN);
        if (inputFrame.pagePrev()) pressKey(screen, GLFW.GLFW_KEY_PAGE_UP);
    }

    private void handleStandardWidgetNavigation(
        Screen screen,
        List<GuiFocusElement> focusElements,
        GuiInputFrame inputFrame,
        Consumer<Boolean> virtualKeyboardRequest
    ) {
        GuiFocusElement selected = focusElements.get(selectedIndex);
        NavigationDirection direction = resolveDirectionalInput(inputFrame);
        if (direction != null) {
            if (selected.widget() instanceof AbstractSliderButton sliderWidget && direction.horizontal()) {
                pressKey(sliderWidget, direction.x() < 0 ? GLFW.GLFW_KEY_LEFT : GLFW.GLFW_KEY_RIGHT);
            } else {
                selectedIndex = WidgetNavigator.findDirectionalIndex(focusElements, selectedIndex, direction.x(), direction.y());
                WidgetNavigator.applySelection(screen, focusElements, selectedIndex);
                selected = focusElements.get(selectedIndex);
            }
        }
        if (inputFrame.confirm()) triggerConfirm(selected, virtualKeyboardRequest);
        if (inputFrame.back()) triggerBack(screen);
        if (inputFrame.pageNext()) pressKey(screen, GLFW.GLFW_KEY_PAGE_DOWN);
        if (inputFrame.pagePrev()) pressKey(screen, GLFW.GLFW_KEY_PAGE_UP);
        if (inputFrame.tabNext()) pressKey(screen, GLFW.GLFW_KEY_TAB);
        if (inputFrame.tabPrev()) pressKey(screen, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT);
    }

    private void handleSettingsNavigation(
        BetterControllerSettingsScreen settingsScreen,
        List<GuiFocusElement> focusElements,
        GuiInputFrame inputFrame,
        Consumer<Boolean> virtualKeyboardRequest
    ) {
        if (inputFrame.down()) {
            selectedIndex = Math.floorMod(selectedIndex + 1, focusElements.size());
        } else if (inputFrame.up()) {
            selectedIndex = Math.floorMod(selectedIndex - 1, focusElements.size());
        }
        WidgetNavigator.applySelection(settingsScreen, focusElements, selectedIndex);

        GuiFocusElement selected = focusElements.get(selectedIndex);
        AbstractWidget widget = selected.widget();
        if (widget instanceof AbstractSliderButton sliderWidget) {
            if (inputFrame.left()) pressKey(sliderWidget, GLFW.GLFW_KEY_LEFT);
            if (inputFrame.right()) pressKey(sliderWidget, GLFW.GLFW_KEY_RIGHT);
        } else if (inputFrame.confirm()) {
            triggerConfirm(selected, virtualKeyboardRequest);
        }
        if (inputFrame.back()) triggerBack(settingsScreen);
    }

    private void handleHandledScreenNavigation(
        Minecraft client,
        AbstractContainerScreen<?> handledScreen,
        List<Slot> slots,
        GuiInputFrame inputFrame,
        boolean screenChanged
    ) {
        if (slots.isEmpty()) {
            return;
        }
        initializeHandledSlotSelection(client, slots, screenChanged);

        Slot selectedSlot = SlotNavigator.findSlotById(slots, selectedHandledSlotId);
        if (selectedSlot == null) {
            selectedSlot = slots.get(0);
            selectedHandledSlotId = selectedSlot.index;
        }
        inventorySelectionState.setSelectedSlot(handledScreen, selectedSlot);

        boolean tabConsumed = handleCreativeTabInputs(handledScreen, inputFrame);
        if (!tabConsumed) {
            handleSlotDirection(slots, inputFrame, handledScreen);
        }

        selectedSlot = SlotNavigator.findSlotById(slots, selectedHandledSlotId);
        if (selectedSlot == null) {
            selectedSlot = slots.get(0);
            selectedHandledSlotId = selectedSlot.index;
        }
        inventorySelectionState.setSelectedSlot(handledScreen, selectedSlot);

        if (inputFrame.confirm()) {
            clickHandledSlot(client, handledScreen, selectedSlot);
        }
        if (inputFrame.back()) triggerBack(handledScreen);
    }

    private boolean handleCreativeTabInputs(AbstractContainerScreen<?> handledScreen, GuiInputFrame inputFrame) {
        if (!(handledScreen instanceof CreativeModeInventoryScreen)) {
            return false;
        }
        if (inputFrame.tabNext() || inputFrame.pageNext()) {
            if (CreativeTabNavigator.cycle(handledScreen, 1)) {
                selectedHandledSlotId = -1;
                return true;
            }
        }
        if (inputFrame.tabPrev() || inputFrame.pagePrev()) {
            if (CreativeTabNavigator.cycle(handledScreen, -1)) {
                selectedHandledSlotId = -1;
                return true;
            }
        }
        return false;
    }

    private void handleSlotDirection(List<Slot> slots, GuiInputFrame inputFrame, AbstractContainerScreen<?> handledScreen) {
        NavigationDirection direction = resolveDirectionalInput(inputFrame);
        if (direction == null) {
            return;
        }
        int nextSlotId = SlotNavigator.findDirectionalSlot(slots, selectedHandledSlotId, direction.x(), direction.y());
        if (nextSlotId != selectedHandledSlotId) {
            selectedHandledSlotId = nextSlotId;
            return;
        }
        if (direction.vertical() && tryScrollHandledScreen(handledScreen, direction == NavigationDirection.UP ? 1.0D : -1.0D)) {
            return;
        }
        if (direction.horizontal() && handledScreen instanceof CreativeModeInventoryScreen) {
            if (CreativeTabNavigator.cycle(handledScreen, direction == NavigationDirection.RIGHT ? 1 : -1)) {
                selectedHandledSlotId = -1;
            }
        }
    }

    private static boolean tryScrollHandledScreen(AbstractContainerScreen<?> screen, double verticalAmount) {
        if (screen == null) {
            return false;
        }
        double mouseX = screen.width / 2.0D;
        double mouseY = screen.height / 2.0D;
        return screen.mouseScrolled(mouseX, mouseY, 0.0D, verticalAmount);
    }

    private static void handleListNavigation(Screen screen, ObjectSelectionList<?> listWidget, GuiInputFrame inputFrame) {
        if (listWidget == null) {
            return;
        }
        if (inputFrame.up()) {
            screen.setFocused(listWidget);
            if (!pressKey(listWidget, GLFW.GLFW_KEY_UP)) pressKey(screen, GLFW.GLFW_KEY_UP);
        }
        if (inputFrame.down()) {
            screen.setFocused(listWidget);
            if (!pressKey(listWidget, GLFW.GLFW_KEY_DOWN)) pressKey(screen, GLFW.GLFW_KEY_DOWN);
        }
        if (inputFrame.confirm()) {
            screen.setFocused(listWidget);
            if (!pressKey(listWidget, GLFW.GLFW_KEY_ENTER)) pressKey(listWidget, GLFW.GLFW_KEY_KP_ENTER);
        }
        if (inputFrame.pageNext()) pressKey(listWidget, GLFW.GLFW_KEY_PAGE_DOWN);
        if (inputFrame.pagePrev()) pressKey(listWidget, GLFW.GLFW_KEY_PAGE_UP);
        if (inputFrame.tabNext()) pressKey(screen, GLFW.GLFW_KEY_TAB);
        if (inputFrame.tabPrev()) pressKey(screen, GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT);
        if (inputFrame.back()) triggerBack(screen);
    }

    private static void collectFocusElements(Screen screen, List<GuiFocusElement> buffer) {
        buffer.clear();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbstractWidget w && w.active && w.visible) {
                buffer.add(GuiFocusElement.fromWidget(w));
            }
        }
    }

    private void collectHandledSlots(AbstractContainerScreen<?> screen, List<Slot> buffer) {
        buffer.clear();
        if (screen == null || screen.getMenu() == null) {
            return;
        }
        for (Slot slot : screen.getMenu().slots) {
            if (slot != null && slot.isActive() && slot.isHighlightable()) {
                buffer.add(slot);
            }
        }
        if (screen instanceof CreativeModeInventoryScreen) {
            filterVisibleSlots(buffer);
        }
        if (!buffer.isEmpty()) {
            return;
        }
        for (Slot slot : screen.getMenu().slots) {
            if (slot != null && slot.isActive()) {
                buffer.add(slot);
            }
        }
        if (screen instanceof CreativeModeInventoryScreen) {
            filterVisibleSlots(buffer);
        }
    }

    private static void filterVisibleSlots(List<Slot> slots) {
        if (slots.isEmpty()) {
            return;
        }
        int visible = 0;
        for (int i = 0; i < slots.size(); i++) {
            Slot s = slots.get(i);
            if (s != null && s.x >= 0 && s.y >= 0 && s.x <= 400 && s.y <= 400) {
                visible++;
            }
        }
        if (visible == 0) {
            return;
        }
        slots.removeIf(slot -> slot == null || slot.x < 0 || slot.y < 0 || slot.x > 400 || slot.y > 400);
    }

    private static ObjectSelectionList<?> findListWidget(Screen screen) {
        if (screen == null) {
            return null;
        }
        for (GuiEventListener child : screen.children()) {
            if (child instanceof ObjectSelectionList<?> listWidget) {
                return listWidget;
            }
        }
        return null;
    }

    private static NavigationDirection resolveDirectionalInput(GuiInputFrame inputFrame) {
        if (inputFrame.up()) return NavigationDirection.UP;
        if (inputFrame.down()) return NavigationDirection.DOWN;
        if (inputFrame.left()) return NavigationDirection.LEFT;
        if (inputFrame.right()) return NavigationDirection.RIGHT;
        return null;
    }

    private static void triggerConfirm(GuiFocusElement selected, Consumer<Boolean> virtualKeyboardRequest) {
        AbstractWidget widget = selected.widget();
        // Sliders are driven with left/right. Confirming one would synthesise a click at its centre,
        // and AbstractSliderButton.onClick sets the value from the click's X - so A would snap it to 50%.
        if (widget instanceof AbstractSliderButton) {
            return;
        }
        if (widget instanceof AbstractButton pressableWidget) {
            pressableWidget.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
            return;
        }
        MouseButtonEvent click = new MouseButtonEvent(selected.centerX(), selected.centerY(), new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0));
        widget.mouseClicked(click, false);
        widget.mouseReleased(click);
        if (widget instanceof EditBox && virtualKeyboardRequest != null) {
            virtualKeyboardRequest.accept(true);
        }
    }

    private static void triggerBack(Screen screen) {
        if (screen.shouldCloseOnEsc()) {
            screen.onClose();
        } else {
            pressKey(screen, GLFW.GLFW_KEY_ESCAPE);
        }
    }

    private static void clickHandledSlot(Minecraft client, AbstractContainerScreen<?> screen, Slot slot) {
        if (client == null || client.player == null || client.gameMode == null || screen == null || slot == null) {
            return;
        }
        client.gameMode.handleContainerInput(
            screen.getMenu().containerId,
            slot.index,
            0,
            ContainerInput.PICKUP,
            client.player
        );
    }

    private static boolean pressKey(GuiEventListener element, int keyCode) {
        return pressKey(element, keyCode, 0);
    }

    private static boolean pressKey(GuiEventListener element, int keyCode, int modifiers) {
        if (element == null) {
            return false;
        }
        return element.keyPressed(new KeyEvent(keyCode, 0, modifiers));
    }

    private enum NavigationMode {
        WIDGETS, LIST, INVENTORY, TEXT_INPUT
    }

    private enum NavigationDirection {
        UP(0, -1), DOWN(0, 1), LEFT(-1, 0), RIGHT(1, 0);

        private final int x;
        private final int y;

        NavigationDirection(int x, int y) {
            this.x = x;
            this.y = y;
        }

        int x() { return x; }
        int y() { return y; }
        boolean horizontal() { return x != 0; }
        boolean vertical() { return y != 0; }
    }
}
