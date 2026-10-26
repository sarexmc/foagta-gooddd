package com.revivalcampfire;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/** Jugador "fantasma" que solo existe en el cliente: se dibuja con el modelo y la skin reales, sin soporte de armadura. */
public class AvatarPlayer extends RemotePlayer {
    public ResourceLocation skin;

    public AvatarPlayer(ClientLevel level, GameProfile profile) {
        super(level, profile);
    }

    @Override
    public ResourceLocation getSkinTextureLocation() {
        return skin != null ? skin : DefaultPlayerSkin.getDefaultSkin(getUUID());
    }

    @Override
    public boolean isSpectator() {
        return false;
    }
}
