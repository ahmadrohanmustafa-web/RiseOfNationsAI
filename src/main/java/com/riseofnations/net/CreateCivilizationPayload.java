package com.riseofnations.net;

import com.riseofnations.RiseOfNationsMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: "create my civilization" with what the player typed and picked. Nothing else. */
public record CreateCivilizationPayload(String data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CreateCivilizationPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(RiseOfNationsMod.MOD_ID, "create_civilization"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CreateCivilizationPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            CreateCivilizationPayload::data,
            CreateCivilizationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
