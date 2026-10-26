package com.riseofnations.net;

import com.riseofnations.RiseOfNationsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: "rename my civilization" to the typed name. */
public record RenameCivilizationPayload(String data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RenameCivilizationPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(RiseOfNationsMod.MOD_ID, "rename_civilization"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenameCivilizationPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            RenameCivilizationPayload::data,
            RenameCivilizationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
