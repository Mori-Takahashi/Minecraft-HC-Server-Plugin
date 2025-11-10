package org.Alpha.bond007;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

public class DeathInfoCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, Bond007 mod) {
        dispatcher.register(CommandManager.literal("deathinfo")
            .requires(source -> source.hasPermissionLevel(0))
            .executes(context -> showStats(context, mod, false))
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(context -> showStats(context, mod, true))
            )
        );
    }

    private static int showStats(CommandContext<ServerCommandSource> context, Bond007 mod, boolean hasTargetArgument) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity target;

        if (hasTargetArgument) {
            target = EntityArgumentType.getPlayer(context, "player");
        } else if (source.getEntity() instanceof ServerPlayerEntity player) {
            target = player;
        } else {
            source.sendError(Text.literal("§cNur Spieler können diesen Command ohne Spieler-Argument verwenden."));
            return 0;
        }

        UUID uuid = target.getUuid();
        int deathCount = mod.getDeathCount(uuid);
        long nextDurationSeconds = millisToSeconds(mod.getNextDeathDuration(uuid));
        long remainingSeconds = millisToSeconds(mod.getRemainingDeathTime(uuid));

        source.sendFeedback(() -> Text.literal("§6Death Stats für " + target.getName().getString() + ":"), false);
        source.sendFeedback(() -> Text.literal("§eTode insgesamt: §f" + deathCount), false);
        source.sendFeedback(() -> Text.literal("§eSperrzeit beim nächsten Tod: §f" + mod.formatTime(nextDurationSeconds)), false);

        if (remainingSeconds > 0) {
            source.sendFeedback(() -> Text.literal("§cAktuell gesperrt: noch " + mod.formatTime(remainingSeconds)), false);
        } else {
            source.sendFeedback(() -> Text.literal("§aAktuell: Spieler ist lebendig."), false);
        }

        return 1;
    }

    private static long millisToSeconds(long millis) {
        if (millis <= 0) {
            return 0;
        }
        return (millis + 999) / 1000;
    }
}
