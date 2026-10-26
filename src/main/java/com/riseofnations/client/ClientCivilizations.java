package com.riseofnations.client;

import com.riseofnations.RiseOfNationsMod;
import com.riseofnations.core.civ.CivWire;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Decides what to do with a message from the server: update the screen that is already open, or open
 * the overview if the server asked for it. Messages that cannot be decoded are ignored.
 */
final class ClientCivilizations {

    private ClientCivilizations() { }

    static void onState(Minecraft client, String data) {
        CivWire.StateMessage message = CivWire.decodeState(data);
        if (message == null) {
            RiseOfNationsMod.LOGGER.warn("Ignored an unreadable civilization message from the server.");
            return;
        }
        Screen current = client.gui.screen();
        if (current instanceof CreateCivilizationScreen create) {
            create.applyState(message);
        } else if (current instanceof CivilizationScreen overview) {
            overview.applyState(message);
        } else if (message.open()) {
            client.gui.setScreen(new CivilizationScreen(message));
        }
    }
}
