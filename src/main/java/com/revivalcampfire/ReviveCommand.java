package com.revivalcampfire;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

/**
 * /revivir demo                -> animacion de prueba (jugador ficticio)
 * /revivir testban <nombre>     -> banea un jugador ficticio para probar el menu
 * /revivir <jugador>            -> ritual en tu posicion
 * /revivir <jugador> <x y z>    -> ritual en unas coordenadas
 */
public class ReviveCommand {
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("revivir")
                .requires(s -> s.hasPermission(2) || s.getServer().isSingleplayer())
                .then(Commands.literal("demo")
                        .executes(c -> demo(c, c.getSource().getPosition().add(0, 0.5, 0))))
                .then(Commands.literal("testban")
                        .then(Commands.argument("nombre", StringArgumentType.word())
                                .executes(ReviveCommand::testBan)))
                .then(Commands.argument("jugador", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                RitualManager.banned(c.getSource().getServer()).stream().map(ReviveMenu.Entry::name), b))
                        .executes(c -> run(c, c.getSource().getPosition().add(0, 0.5, 0)))
                        .then(Commands.argument("pos", Vec3Argument.vec3())
                                .executes(c -> run(c, Vec3Argument.getVec3(c, "pos").add(0, 0.5, 0))))));
    }

    private static java.util.UUID reviverId(CommandSourceStack src) {
        return src.getEntity() instanceof net.minecraft.server.level.ServerPlayer sp ? sp.getUUID() : new java.util.UUID(0L, 0L);
    }

    private static int demo(CommandContext<CommandSourceStack> ctx, Vec3 pos) {
        CommandSourceStack src = ctx.getSource();
        Component err = RitualManager.startDemo(src.getLevel(), pos, "Jugador_Demo", src.getTextName(), reviverId(src));
        if (err != null) {
            src.sendFailure(err);
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("msg.revivalcampfire.started", "Jugador_Demo"), false);
        return 1;
    }

    private static int testBan(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "nombre");
        RitualManager.addTestBan(ctx.getSource().getServer(), name);
        ctx.getSource().sendSuccess(() -> Component.literal("Baneo de prueba añadido: " + name), false);
        return 1;
    }

    private static int run(CommandContext<CommandSourceStack> ctx, Vec3 pos) {
        String name = StringArgumentType.getString(ctx, "jugador");
        CommandSourceStack src = ctx.getSource();
        Optional<ReviveMenu.Entry> entry = RitualManager.banned(src.getServer()).stream()
                .filter(e -> e.name().equalsIgnoreCase(name)).findFirst();
        if (entry.isEmpty()) {
            src.sendFailure(Component.translatable("msg.revivalcampfire.not_banned", name));
            return 0;
        }
        Component err = RitualManager.start(src.getLevel(), pos, entry.get().id(), src.getTextName(), reviverId(src));
        if (err != null) {
            src.sendFailure(err);
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("msg.revivalcampfire.started", name), true);
        return 1;
    }
}
