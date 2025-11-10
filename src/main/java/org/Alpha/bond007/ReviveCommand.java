package org.Alpha.bond007;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.UUID;

public class ReviveCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, Bond007 mod) {
        dispatcher.register(CommandManager.literal("revive")
            .requires(source -> source.hasPermissionLevel(2))
            .then(CommandManager.argument("player", EntityArgumentType.player())
                .executes(context -> executeRevive(context, mod, false))
                .then(CommandManager.argument("death_counter", IntegerArgumentType.integer(0, 8))
                    .executes(context -> executeRevive(context, mod, true))
                )
            )
        );
    }

    private static int executeRevive(CommandContext<ServerCommandSource> context, Bond007 mod, boolean withCounter) throws CommandSyntaxException {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity target = EntityArgumentType.getPlayer(context, "player");
        UUID uuid = target.getUuid();

        if (!mod.isDead(uuid)) {
            source.sendError(Text.literal("§cDieser Spieler ist nicht tot!"));
            return 0;
        }

        if (withCounter) {
            int deathCounter = IntegerArgumentType.getInteger(context, "death_counter");

            // Set the death counter for the player
            mod.setDeathCounter(uuid, deathCounter);
            source.sendFeedback(() -> Text.literal("§aDeath Counter von " + target.getName().getString() + " auf " + deathCounter + " gesetzt!"), true);
            target.sendMessage(Text.literal("§eDein Death Counter wurde auf " + deathCounter + " gesetzt!"));

            // Revive the player immediately
            mod.revivePlayer(target);
        } else {
            // Set death time to 20 seconds (original behavior)
            mod.setDeathTime(target, 20 * 1000);
            source.sendFeedback(() -> Text.literal("§aTod-Zeit von " + target.getName().getString() + " auf 20 Sekunden gesetzt!"), true);
            target.sendMessage(Text.literal("§eDeine Tod-Zeit wurde auf 20 Sekunden reduziert!"));
        }

        return 1;
    }
}
