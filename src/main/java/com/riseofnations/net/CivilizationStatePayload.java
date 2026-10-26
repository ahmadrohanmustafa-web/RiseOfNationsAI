package com.riseofnations.net;

import com.riseofnations.RiseOfNationsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: the player's own civilization (or an error). The text is built by CivWire. */
public record CivilizationStatePayload(String data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CivilizationStatePayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(RiseOfNationsMod.MOD_ID, "civilization_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CivilizationStatePayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            CivilizationStatePayload::data,
            CivilizationStatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
