package com.riseofnations.client;

import com.riseofnations.RiseOfNationsMod;
import com.riseofnations.core.civ.CivColor;
import com.riseofnations.core.civ.CivError;
import com.riseofnations.core.civ.CivNames;
import com.riseofnations.core.civ.CivWire;
import com.riseofnations.core.civ.Government;
import com.riseofnations.net.CreateCivilizationPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Form for founding a civilization. The same name rules the server uses are checked here only to give
 * instant feedback; the server checks everything again and has the final say.
 */
final class CreateCivilizationScreen extends Screen {

    private EditBox nameBox;
    private EditBox leaderBox;
    private Button confirm;

    private String nameDraft = "";
    private String leaderDraft = "";
    private int colorIndex = 0;
    private int governmentIndex = 0;
    private String localError = "";
    private String serverError = "";
    private boolean pending = false;

    CreateCivilizationScreen() {
        super(Component.literal("Found a Civilization"));
    }

    /** Called when the server answers the create request. */
    void applyState(CivWire.StateMessage state) {
        pending = false;
        if (state.error() == CivError.NONE && state.view() != null) {
            this.minecraft.gui.setScreen(new CivilizationScreen(state));
            return;
        }
        serverError = state.error() == CivError.NONE ? CivError.INVALID_REQUEST.message() : state.error().message();
        refreshState();
    }

    private int top() {
        return Math.max(6, this.height / 2 - 96);
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int panelWidth = Math.min(this.width - 20, 220);
        int left = cx - panelWidth / 2;
        int y = top() + 14;

        nameBox = new EditBox(this.font, left, y + 11, panelWidth, 20, Component.literal("Civilization name"));
        nameBox.setMaxLength(64);
        nameBox.setHint(Component.literal("e.g. New Rome"));
        nameBox.setValue(nameDraft);
        this.addRenderableWidget(nameBox);
        y += 35;

        leaderBox = new EditBox(this.font, left, y + 11, panelWidth, 20, Component.literal("Leader name"));
        leaderBox.setMaxLength(64);
        leaderBox.setHint(Component.literal("e.g. Aldric Stone"));
        leaderBox.setValue(leaderDraft);
        this.addRenderableWidget(leaderBox);
        y += 35;

        Button colorButton = Button.builder(colorLabel(), button -> {
            colorIndex = (colorIndex + 1) % CivColor.values().length;
            button.setMessage(colorLabel());
        }).bounds(left, y, panelWidth, 20).build();
        colorButton.setTooltip(Tooltip.create(Component.literal("Click to cycle through the colors.")));
        this.addRenderableWidget(colorButton);
        y += 24;

        Button governmentButton = Button.builder(governmentLabel(), button -> {
            governmentIndex = (governmentIndex + 1) % Government.values().length;
            button.setMessage(governmentLabel());
        }).bounds(left, y, panelWidth, 20).build();
        governmentButton.setTooltip(Tooltip.create(Component.literal("Click to cycle. For now this is recorded but has no gameplay effect.")));
        this.addRenderableWidget(governmentButton);

        int buttonsY = top() + 188;
        confirm = Button.builder(Component.literal("Found Civilization"), button -> submit())
                .bounds(left, buttonsY, panelWidth / 2 - 2, 20).build();
        this.addRenderableWidget(confirm);
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> this.onClose())
                .bounds(cx + 2, buttonsY, panelWidth / 2 - 2, 20).build());

        nameBox.setResponder(text -> {
            nameDraft = text;
            serverError = "";
            refreshState();
        });
        leaderBox.setResponder(text -> {
            leaderDraft = text;
            serverError = "";
            refreshState();
        });
        this.setInitialFocus(nameBox);
        refreshState();
    }

    private Component colorLabel() {
        return Component.literal("Color: " + CivColor.values()[colorIndex].displayName());
    }

    private Component governmentLabel() {
        return Component.literal("Government: " + Government.values()[governmentIndex].displayName());
    }

    /** Re-checks the typed values and enables the confirm button only when everything is valid. */
    private void refreshState() {
        localError = "";
        boolean ok = true;
        if (nameDraft.isEmpty() || leaderDraft.isEmpty()) {
            ok = false;
        }
        if (!nameDraft.isEmpty()) {
            CivNames.NameCheck check = CivNames.check(nameDraft);
            if (!check.ok()) {
                localError = CivError.forCivName(check.problem()).message();
                ok = false;
            }
        }
        if (localError.isEmpty() && !leaderDraft.isEmpty()) {
            CivNames.NameCheck check = CivNames.check(leaderDraft);
            if (!check.ok()) {
                localError = CivError.forLeaderName(check.problem()).message();
                ok = false;
            }
        }
        if (confirm != null) {
            confirm.active = ok && !pending;
        }
    }

    private void submit() {
        if (pending) return;
        pending = true;
        serverError = "";
        refreshState();
        try {
            ClientPlayNetworking.send(new CreateCivilizationPayload(CivWire.encodeCreate(
                    nameDraft, leaderDraft,
                    CivColor.values()[colorIndex].id(), Government.values()[governmentIndex].id())));
        } catch (RuntimeException e) {
            RiseOfNationsMod.LOGGER.warn("Could not send the create request: {}", e.getMessage());
            pending = false;
            serverError = "Could not reach the server. Is the mod installed on it?";
            refreshState();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int cx = this.width / 2;
        int panelWidth = Math.min(this.width - 20, 220);
        int left = cx - panelWidth / 2;
        int y = top();

        ScreenText.centered(graphics, this.font, "Found a Civilization", cx, y, ScreenText.WHITE);
        ScreenText.left(graphics, this.font, "Civilization name", left, y + 14, ScreenText.GRAY);
        ScreenText.left(graphics, this.font, "Leader name", left, y + 49, ScreenText.GRAY);

        Government government = Government.values()[governmentIndex];
        ScreenText.left(graphics, this.font, government.description(), left, y + 128, ScreenText.GRAY);

        CivNames.NameCheck name = CivNames.check(nameDraft);
        if (name.ok()) {
            int color = 0xFF000000 | CivColor.values()[colorIndex].rgb();
            ScreenText.centered(graphics, this.font, name.clean(), cx, y + 143, color);
        }

        String shown = !serverError.isEmpty() ? serverError : localError;
        if (pending) {
            ScreenText.centered(graphics, this.font, "Waiting for the server...", cx, y + 164, ScreenText.GRAY);
        } else if (!shown.isEmpty()) {
            ScreenText.centered(graphics, this.font, shown, cx, y + 164, ScreenText.RED);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(null);
    }
}
