package com.revivalcampfire;

import net.minecraftforge.common.ForgeConfigSpec;

public class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue BAN_ON_DEATH;
    public static final ForgeConfigSpec.ConfigValue<String> BAN_REASON;
    public static final ForgeConfigSpec.IntValue REQUIRED_PERMISSION;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        BAN_ON_DEATH = b.comment("Banear automaticamente a los jugadores al morir (desactivado si ya tienes otro sistema de baneo)")
                .define("banOnDeath", false);
        BAN_REASON = b.comment("Mensaje/razon del baneo por muerte")
                .define("banReason", "Has muerto. Espera a que alguien te reviva en la fogata.");
        REQUIRED_PERMISSION = b.comment("Nivel de permiso para usar la fogata (0 = todos, 2 = operadores)")
                .defineInRange("requiredPermissionLevel", 0, 0, 4);
        SPEC = b.build();
    }
}
