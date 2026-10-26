package com.riseofnations.server;

import com.mojang.serialization.Codec;
import com.riseofnations.RiseOfNationsMod;
import com.riseofnations.core.persist.CoreState;
import com.riseofnations.core.persist.StateCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The mod's persistent world data, stored by vanilla Minecraft in the world folder
 * (data/riseofnations/state.dat). The content is the versioned, tested text format from
 * {@link StateCodec}, so loading and saving logic is the same code the unit tests run.
 *
 * If the stored data comes from a NEWER mod version, it is kept untouched and every change is
 * refused ({@link #refused()}), so a downgrade can never destroy a world's civilization data.
 */
public final class RonSavedData extends SavedData {

    private static final Codec<RonSavedData> CODEC = Codec.STRING.xmap(RonSavedData::new, RonSavedData::toSaveText);

    public static final SavedDataType<RonSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(RiseOfNationsMod.MOD_ID, "state"),
            RonSavedData::new,
            CODEC,
            null);

    private final CoreState state;
    private final boolean refused;
    private final String refusedRawText;

    /** A new world: nothing saved yet. */
    public RonSavedData() {
        this.state = new CoreState();
        this.refused = false;
        this.refusedRawText = "";
    }

    /** Loads saved text. Never throws: damaged parts are repaired and logged. */
    public RonSavedData(String raw) {
        StateCodec.Decoded decoded = StateCodec.decode(raw);
        for (String warning : decoded.warnings()) {
            RiseOfNationsMod.LOGGER.warn("Rise of Nations save data: {}", warning);
        }
        this.refused = decoded.refused();
        this.state = decoded.state();
        this.refusedRawText = decoded.refused() ? raw : "";
        if (decoded.refused()) {
            RiseOfNationsMod.LOGGER.error("Rise of Nations save data was written by a newer version. It will be left untouched and civilization features are disabled.");
        }
    }

    public CoreState state() {
        return state;
    }

    public boolean refused() {
        return refused;
    }

    /** Call after every change so Minecraft writes the data at the next save. */
    public void markChanged() {
        setDirty();
    }

    private String toSaveText() {
        return refused ? refusedRawText : StateCodec.encode(state);
    }

    /** The saved data of this world, or null if the overworld is not available yet. */
    public static RonSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) {
            RiseOfNationsMod.LOGGER.error("Rise of Nations: overworld not available, cannot access saved data.");
            return null;
        }
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }
}
