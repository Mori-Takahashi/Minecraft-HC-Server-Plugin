package org.Alpha.bond007;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Bond007 implements DedicatedServerModInitializer {
    public static final String MOD_ID = "bond007";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path DATA_FILE = Path.of("config", "deathcounter_data.json");

    private final Map<UUID, Long> deathTimestamps = new HashMap<>();
    private final Map<UUID, Integer> deathCounts = new HashMap<>();
    private final Map<UUID, DeathLocation> deathLocations = new HashMap<>();
    private final Map<UUID, SpawnData> playerSpawnData = new HashMap<>();

    // Progressive death durations in milliseconds
    private static final long[] DEATH_DURATIONS = {
            10 * 1000,              // 1st death: 10 seconds
            20 * 1000,              // 2nd death: 20 seconds
            30 * 1000,              // 3rd death: 30 seconds
            30 * 60 * 1000,         // 4th death: 30 minutes
            60 * 60 * 1000,         // 5th death: 1 hour
            2 * 60 * 60 * 1000,     // 6th death: 2 hours
            3 * 60 * 60 * 1000,     // 7th death: 3 hours
            24 * 60 * 60 * 1000     // 8th+ death: 24 hours
    };

    private static Bond007 INSTANCE;
    private MinecraftServer server;

    @Override
    public void onInitializeServer() {
        INSTANCE = this;
        LOGGER.info("DeathCounter Fabric Mod wird geladen...");

        // Load data
        loadDeathData();

        // Register events
        ServerLivingEntityEvents.ALLOW_DEATH.register(this::onPlayerDeath);
        ServerPlayerEvents.AFTER_RESPAWN.register(this::onPlayerRespawn);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onPlayerJoin(handler.player));
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStarted);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);

        // Register command
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            ReviveCommand.register(dispatcher, this);
            DeathInfoCommand.register(dispatcher, this);
        });

        LOGGER.info("DeathCounter Fabric Mod aktiviert!");
    }

    private void onServerStarted(MinecraftServer server) {
        this.server = server;
    }

    private void onServerStopping(MinecraftServer server) {
        saveDeathData();
        LOGGER.info("DeathCounter Fabric Mod deaktiviert!");
    }

    private boolean onPlayerDeath(net.minecraft.entity.LivingEntity entity, DamageSource damageSource, float damageAmount) {
        if (entity instanceof ServerPlayerEntity player) {
            UUID uuid = player.getUuid();

            // Save player's spawn data before death (Yarn 1.21.8 API)
            ServerPlayerEntity.Respawn respawn = player.getRespawn();
            BlockPos spawnPos = respawn != null ? respawn.pos() : null;
            RegistryKey<World> spawnDimension = respawn != null ? respawn.dimension() : null;
            boolean spawnForced = respawn != null && respawn.forced();

            playerSpawnData.put(uuid, new SpawnData(spawnDimension, spawnPos, spawnForced));

            // Save death location
            Vec3d deathPos = player.getPos();
            String dimensionKey = player.getWorld().getRegistryKey().getValue().toString();
            deathLocations.put(uuid, new DeathLocation(deathPos.x, deathPos.y, deathPos.z, dimensionKey));

            // Increment death count
            int currentDeathCount = deathCounts.getOrDefault(uuid, 0) + 1;
            deathCounts.put(uuid, currentDeathCount);

            // Get death duration based on death count
            long deathDuration = getDeathDuration(currentDeathCount);
            long reviveTime = System.currentTimeMillis() + deathDuration;

            deathTimestamps.put(uuid, reviveTime);
            saveDeathData();

            player.sendMessage(Text.literal("\u00A7c\u00A7lDU BIST GESTORBEN!"));
            long deathSeconds = deathDuration / 1000;
            player.sendMessage(Text.literal("\u00A7eTod Nr. " + currentDeathCount + " - Du bist für " + formatTime(deathSeconds) + " im Spectator-Modus."));

            broadcastStatus(Text.literal(
                "§c" + player.getName().getString() + " ist gestorben (Tod Nr. " + currentDeathCount + ", Sperrzeit: " + formatTime(deathSeconds) + ")."
            ));

            return true;
        }
        return true;
    }

    private void onPlayerRespawn(ServerPlayerEntity oldPlayer, ServerPlayerEntity newPlayer, boolean alive) {
        if (!alive) {
            UUID uuid = newPlayer.getUuid();
            if (deathTimestamps.containsKey(uuid)) {
                newPlayer.changeGameMode(GameMode.SPECTATOR);
                teleportToDeathLocation(newPlayer, deathLocations.get(uuid));

                long reviveTime = deathTimestamps.get(uuid);
                long remainingSeconds = Math.max(0, (reviveTime - System.currentTimeMillis()) / 1000);
                int currentDeathCount = deathCounts.getOrDefault(uuid, 1);

                newPlayer.sendMessage(Text.literal("\u00A7c\u00A7lDU BIST TOT!"));
                newPlayer.sendMessage(Text.literal("\u00A7eTod Nr. " + currentDeathCount + " - Verbleibende Zeit: " + formatTime(remainingSeconds)));
            }
        }
    }

    private void onPlayerJoin(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        if (deathTimestamps.containsKey(uuid)) {
            long reviveTime = deathTimestamps.get(uuid);
            long currentTime = System.currentTimeMillis();

            if (currentTime < reviveTime) {
                // Player is still dead
                player.changeGameMode(GameMode.SPECTATOR);
                teleportToDeathLocation(player, deathLocations.get(uuid));
                long remainingSeconds = (reviveTime - currentTime) / 1000;
                player.sendMessage(Text.literal("\u00A7c\u00A7lDU BIST TOT!"));
                player.sendMessage(Text.literal("\u00A7eVerbleibende Zeit: " + formatTime(remainingSeconds)));
            } else {
                // Death time expired, revive player
                revivePlayer(player);
            }
        }
    }

    private void onServerTick(MinecraftServer server) {
        long currentTime = System.currentTimeMillis();

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            UUID uuid = player.getUuid();

            if (deathTimestamps.containsKey(uuid)) {
                long reviveTime = deathTimestamps.get(uuid);

                if (currentTime >= reviveTime) {
                    // Time expired, revive player
                    revivePlayer(player);
                } else {
                    // Show countdown in action bar
                    long remainingSeconds = (reviveTime - currentTime) / 1000;
                    player.sendMessage(Text.literal("§c☠ Tod: " + formatTime(remainingSeconds) + " §c☠"), true);

                    // Ensure player stays in spectator mode
                    if (player.interactionManager.getGameMode() != GameMode.SPECTATOR) {
                        player.changeGameMode(GameMode.SPECTATOR);
                    }
                }
            }
        }

        // Save data every minute
        if (currentTime % 60000 < 50) {
            saveDeathData();
        }
    }

    private long getDeathDuration(int deathCount) {
        int index = deathCount - 1;
        if (index >= DEATH_DURATIONS.length) {
            return DEATH_DURATIONS[DEATH_DURATIONS.length - 1];
        }
        return DEATH_DURATIONS[index];
    }

    public void revivePlayer(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();

        if (deathTimestamps.containsKey(uuid)) {
            deathTimestamps.remove(uuid);
            saveDeathData();

            // Teleport to player's saved spawn point (bed/respawn anchor) or fallback to world spawn
            SpawnData spawnData = playerSpawnData.get(uuid);
            teleportToSpawn(player, spawnData);

            // Clean up death location and spawn data
            deathLocations.remove(uuid);
            playerSpawnData.remove(uuid);

            player.setYaw(0.0f);
            player.setPitch(0.0f);
            player.changeGameMode(GameMode.SURVIVAL);
            player.sendMessage(Text.literal("§a§l✓ DU WURDEST WIEDERBELEBT!"));
            player.sendMessage(Text.literal("§eDu kannst nun wieder normal spielen."));
            broadcastStatus(Text.literal("§a" + player.getName().getString() + " wurde wiederbelebt und darf wieder spielen."));
        }
    }

    private void teleportToSpawn(ServerPlayerEntity player, SpawnData spawnData) {
        if (spawnData != null && spawnData.dimension() != null && spawnData.pos() != null) {
            ServerWorld world = server.getWorld(spawnData.dimension());
            if (world != null) {
                teleportPlayer(player, world, spawnData.pos());
                return;
            }
        }

        teleportToWorldSpawn(player);
    }

    private void teleportToWorldSpawn(ServerPlayerEntity player) {
        ServerWorld world = server.getOverworld();
        teleportPlayer(player, world, world.getSpawnPos());
    }

    private void teleportPlayer(ServerPlayerEntity player, ServerWorld world, BlockPos pos) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        player.teleport(world, x, y, z, EnumSet.noneOf(PositionFlag.class), player.getYaw(), player.getPitch(), false);
        player.setYaw(0.0f);
        player.setPitch(0.0f);
    }

    private void teleportToDeathLocation(ServerPlayerEntity player, DeathLocation deathLoc) {
        if (deathLoc == null || server == null) {
            return;
        }

        ServerWorld targetWorld = null;
        if (deathLoc.dimension != null) {
            Identifier id = Identifier.tryParse(deathLoc.dimension);
            if (id != null) {
                RegistryKey<World> dimensionKey = RegistryKey.of(RegistryKeys.WORLD, id);
                targetWorld = server.getWorld(dimensionKey);
            } else {
                LOGGER.warn("Ungültige Dimension '{}' für gespeicherte DeathLocation von {}", deathLoc.dimension, player.getGameProfile().getName());
            }
        }

        if (targetWorld == null) {
            targetWorld = player.getWorld();
        }

        player.teleport(
            targetWorld,
            deathLoc.x,
            deathLoc.y,
            deathLoc.z,
            EnumSet.noneOf(PositionFlag.class),
            player.getYaw(),
            player.getPitch(),
            false
        );
    }

    private void broadcastStatus(Text message) {
        if (server != null && server.getPlayerManager() != null) {
            server.getPlayerManager().broadcast(message, false);
        }
    }

    public void setDeathTime(ServerPlayerEntity player, long milliseconds) {
        long reviveTime = System.currentTimeMillis() + milliseconds;
        deathTimestamps.put(player.getUuid(), reviveTime);
        player.changeGameMode(GameMode.SPECTATOR);
        saveDeathData();
    }

    public boolean isDead(UUID uuid) {
        return deathTimestamps.containsKey(uuid);
    }

    public void setDeathCounter(UUID uuid, int count) {
        deathCounts.put(uuid, count);
        saveDeathData();
    }

    public int getDeathCount(UUID uuid) {
        return deathCounts.getOrDefault(uuid, 0);
    }

    public long getNextDeathDuration(UUID uuid) {
        int nextDeathCount = getDeathCount(uuid) + 1;
        return getDeathDuration(nextDeathCount);
    }

    public long getRemainingDeathTime(UUID uuid) {
        Long reviveTime = deathTimestamps.get(uuid);
        if (reviveTime == null) {
            return 0L;
        }
        long remaining = reviveTime - System.currentTimeMillis();
        return Math.max(0L, remaining);
    }

    public String formatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }

    private void loadDeathData() {
        try {
            if (Files.exists(DATA_FILE)) {
                try (Reader reader = Files.newBufferedReader(DATA_FILE)) {
                    Type type = new TypeToken<DeathData>(){}.getType();
                    DeathData data = GSON.fromJson(reader, type);

                    if (data != null) {
                        long currentTime = System.currentTimeMillis();

                        // Load death timestamps (only if not expired)
                        if (data.deathTimestamps != null) {
                            data.deathTimestamps.forEach((uuidStr, timestamp) -> {
                                if (currentTime < timestamp) {
                                    deathTimestamps.put(UUID.fromString(uuidStr), timestamp);
                                }
                            });
                        }

                        // Load death counts
                        if (data.deathCounts != null) {
                            data.deathCounts.forEach((uuidStr, count) ->
                                deathCounts.put(UUID.fromString(uuidStr), count)
                            );
                        }

                        // Load death locations
                        if (data.deathLocations != null) {
                            data.deathLocations.forEach((uuidStr, location) ->
                                deathLocations.put(UUID.fromString(uuidStr), location)
                            );
                        }

                        // Load spawn data for dead players
                        playerSpawnData.clear();
                        if (data.spawnData != null) {
                            data.spawnData.forEach((uuidStr, stored) -> {
                                RegistryKey<World> dimension = null;
                                if (stored.dimension != null) {
                                    Identifier id = Identifier.tryParse(stored.dimension);
                                    if (id != null) {
                                        dimension = RegistryKey.of(RegistryKeys.WORLD, id);
                                    } else {
                                        LOGGER.warn("Ungültige Dimension für SpawnData von {}: {}", uuidStr, stored.dimension);
                                    }
                                }

                                BlockPos spawnPos = null;
                                if (stored.x != null && stored.y != null && stored.z != null) {
                                    spawnPos = new BlockPos(stored.x, stored.y, stored.z);
                                }

                                playerSpawnData.put(
                                    UUID.fromString(uuidStr),
                                    new SpawnData(dimension, spawnPos, stored.forced)
                                );
                            });
                        }

                        LOGGER.info("Geladen: {} tote Spieler", deathTimestamps.size());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Fehler beim Laden der Death-Daten", e);
        }
    }

    private void saveDeathData() {
        try {
            Files.createDirectories(DATA_FILE.getParent());

            DeathData data = new DeathData();
            data.deathTimestamps = new HashMap<>();
            data.deathCounts = new HashMap<>();
            data.deathLocations = new HashMap<>();
            data.spawnData = new HashMap<>();

            deathTimestamps.forEach((uuid, timestamp) ->
                data.deathTimestamps.put(uuid.toString(), timestamp)
            );

            deathCounts.forEach((uuid, count) ->
                data.deathCounts.put(uuid.toString(), count)
            );

            deathLocations.forEach((uuid, location) ->
                data.deathLocations.put(uuid.toString(), location)
            );

            playerSpawnData.forEach((uuid, spawnData) -> {
                StoredSpawnData stored = new StoredSpawnData();
                if (spawnData.dimension() != null) {
                    stored.dimension = spawnData.dimension().getValue().toString();
                }
                if (spawnData.pos() != null) {
                    stored.x = spawnData.pos().getX();
                    stored.y = spawnData.pos().getY();
                    stored.z = spawnData.pos().getZ();
                }
                stored.forced = spawnData.forced();
                data.spawnData.put(uuid.toString(), stored);
            });

            try (Writer writer = Files.newBufferedWriter(DATA_FILE)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Fehler beim Speichern der Death-Daten", e);
        }
    }

    public static Bond007 getInstance() {
        return INSTANCE;
    }

    public MinecraftServer getServer() {
        return server;
    }

    private record SpawnData(RegistryKey<World> dimension, BlockPos pos, boolean forced) {}

    private static class DeathData {
        Map<String, Long> deathTimestamps;
        Map<String, Integer> deathCounts;
        Map<String, DeathLocation> deathLocations;
        Map<String, StoredSpawnData> spawnData;
    }

    private static class DeathLocation {
        double x, y, z;
        String dimension;

        DeathLocation(double x, double y, double z, String dimension) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.dimension = dimension;
        }
    }

    private static class StoredSpawnData {
        String dimension;
        Integer x;
        Integer y;
        Integer z;
        boolean forced;
    }
}
