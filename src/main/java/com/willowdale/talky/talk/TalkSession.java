package com.willowdale.talky.talk;

import com.willowdale.talky.TalkyPlugin;
import com.willowdale.talky.util.SchedulerUtil;
import com.willowdale.talky.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.List;

public class TalkSession {

    private final TalkyPlugin plugin;
    private final Player player;
    private final TalkConfig config;
    private final String npcName;
    private final Location startLocation;
    private final Location npcLocation;
    private Location anchoredLocation;

    private int currentLineIndex = 0;
    private SchedulerUtil.TaskHandle currentTask;
    private boolean active = false;
    private long lastAdvanceTime = 0;

    private boolean gracePeriod = true;
    private boolean pendingGroundAnchor = false;
    private SchedulerUtil.TaskHandle graceTask;

    private boolean smoothRotating = false;
    private SchedulerUtil.TaskHandle cameraTask;
    private float currentCameraYaw;
    private float currentCameraPitch;
    private volatile long lastInternalTeleportTime = 0;

    public TalkSession(TalkyPlugin plugin, Player player, TalkConfig config, String npcName, Location npcLocation) {
        this.plugin = plugin;
        this.player = player;
        this.config = config;
        this.npcName = npcName != null ? npcName : config.getTitle();
        this.startLocation = player.getLocation().clone();
        this.anchoredLocation = player.getLocation().clone();
        this.npcLocation = npcLocation != null ? npcLocation.clone() : null;
    }

    public void start() {
        if (active || !player.isOnline()) {
            return;
        }
        active = true;
        lastAdvanceTime = System.currentTimeMillis();

        // Cancel sprint immediately so FOV doesn't jump when slowness is applied
        try {
            player.setSprinting(false);
        } catch (Throwable ignored) {
        }

        // Apply Blindness effect if enabled (cinematic focus on NPC)
        if (config.isBlindnessEnabled()) {
            applyBlindnessEffect();
        }

        // Apply single consistent FOV zoom slowness (prevents double FOV jumps)
        if (config.isFovEnabled()) {
            applyFovEffect();
        }

        // Smooth step-by-step camera rotation towards NPC eye level
        if (npcLocation != null && config.isLockCamera()) {
            startSmoothCameraLock(config.getCameraSmoothTicks());
        }

        // Movement grace period (allows running towards NPC / holding W without instant cancel)
        startGracePeriod(config.getInitiationGraceTicks());

        // Send optional header
        String header = plugin.getConfig().getString("chat.header", "");
        if (!header.isEmpty()) {
            TextUtil.sendMessage(player, header);
        }

        deliverLine();
    }

    private void applyBlindnessEffect() {
        try {
            PotionEffect effect = new PotionEffect(
                    PotionEffectType.BLINDNESS,
                    72000,
                    0,
                    false, // ambient
                    false, // particles
                    false  // icon
            );
            player.addPotionEffect(effect);
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to apply blindness effect: " + t.getMessage());
        }
    }

    private void applyFovEffect() {
        try {
            PotionEffect effect = new PotionEffect(
                    PotionEffectType.SLOWNESS,
                    72000,
                    config.getFovAmplifier(),
                    false, // ambient
                    false, // particles
                    false  // icon
            );
            player.addPotionEffect(effect);
        } catch (Throwable t) {
            plugin.getLogger().warning("Failed to apply FOV slowness: " + t.getMessage());
        }
    }

    private void startSmoothCameraLock(int totalSteps) {
        if (npcLocation == null || !player.isOnline()) {
            return;
        }

        Location eye = player.getEyeLocation();
        double dx = npcLocation.getX() - eye.getX();
        double dy = npcLocation.getY() - eye.getY();
        double dz = npcLocation.getZ() - eye.getZ();
        double distXZ = Math.hypot(dx, dz);
        if (distXZ < 0.001) {
            return;
        }

        float initialYaw = player.getLocation().getYaw();
        float initialPitch = player.getLocation().getPitch();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));

        if (totalSteps <= 1) {
            currentCameraYaw = targetYaw;
            currentCameraPitch = targetPitch;
            teleportRotation(targetYaw, targetPitch);
            return;
        }

        smoothRotating = true;
        currentCameraYaw = initialYaw;
        currentCameraPitch = initialPitch;

        int[] stepCounter = new int[]{0};

        cameraTask = SchedulerUtil.runRepeating(plugin, player, () -> {
            if (!active || !player.isOnline()) {
                if (cameraTask != null) {
                    cameraTask.cancel();
                    cameraTask = null;
                }
                smoothRotating = false;
                return;
            }

            stepCounter[0]++;
            int step = stepCounter[0];
            float progress = Math.min(1.0f, (float) step / (float) totalSteps);
            // Smoothstep curve (ease in, ease out)
            float t = progress * progress * (3.0f - 2.0f * progress);

            Location currentEye = player.getEyeLocation();
            double curDx = npcLocation.getX() - currentEye.getX();
            double curDy = npcLocation.getY() - currentEye.getY();
            double curDz = npcLocation.getZ() - currentEye.getZ();
            double curDistXZ = Math.hypot(curDx, curDz);

            if (curDistXZ >= 0.001) {
                float curTargetYaw = (float) Math.toDegrees(Math.atan2(-curDx, curDz));
                float curTargetPitch = (float) -Math.toDegrees(Math.atan2(curDy, curDistXZ));

                float deltaYaw = ((curTargetYaw - initialYaw + 180f) % 360f + 360f) % 360f - 180f;
                float deltaPitch = curTargetPitch - initialPitch;

                currentCameraYaw = initialYaw + deltaYaw * t;
                currentCameraPitch = initialPitch + deltaPitch * t;

                teleportRotation(currentCameraYaw, currentCameraPitch);
            }

            if (step >= totalSteps) {
                smoothRotating = false;
                if (cameraTask != null) {
                    cameraTask.cancel();
                    cameraTask = null;
                }
            }
        }, 1, 1);
    }

    private void teleportRotation(float yaw, float pitch) {
        if (!player.isOnline()) {
            return;
        }
        lastInternalTeleportTime = System.currentTimeMillis();
        try {
            player.setRotation(yaw, pitch);
        } catch (Throwable ignored) {
        }

        // If player is airborne (jumping / falling), DO NOT teleport XYZ coordinates.
        // Teleporting airborne players cancels gravity, resets downward velocity,
        // and causes anti-cheat to flag for flying / hovering.
        // PlayerMoveEvent handles rotation while falling with full gravity physics.
        if (!player.isOnGround()) {
            return;
        }

        Location loc = player.getLocation().clone();
        loc.setYaw(yaw);
        loc.setPitch(pitch);
        try {
            player.teleportAsync(loc);
        } catch (Throwable t) {
            player.teleport(loc);
        }
    }

    private void startGracePeriod(int graceTicks) {
        gracePeriod = true;
        pendingGroundAnchor = false;
        anchoredLocation = player.getLocation().clone();
        if (graceTicks <= 0) {
            gracePeriod = false;
            return;
        }
        graceTask = SchedulerUtil.runDelayed(plugin, player, () -> {
            if (active && player.isOnline()) {
                if (!player.isOnGround()) {
                    // Wait until player actually touches the ground before ending grace period
                    pendingGroundAnchor = true;
                } else {
                    gracePeriod = false;
                    anchoredLocation = player.getLocation().clone();
                }
            }
        }, graceTicks);
    }

    public void onGroundLanded(Location location) {
        if (pendingGroundAnchor && active && location != null) {
            pendingGroundAnchor = false;
            gracePeriod = false;
            anchoredLocation = location.clone();
        }
    }

    private void removeEffects() {
        try {
            if (player.isOnline()) {
                player.removePotionEffect(PotionEffectType.SLOWNESS);
                player.removePotionEffect(PotionEffectType.BLINDNESS);
            }
        } catch (Throwable ignored) {
        }
    }

    private void deliverLine() {
        if (!active || !player.isOnline()) {
            return;
        }

        List<TalkLine> lines = config.getLines();
        if (lines.isEmpty() || currentLineIndex >= lines.size()) {
            finish();
            return;
        }

        TalkLine line = lines.get(currentLineIndex);
        String formatted = line.getText()
                .replace("%npc%", npcName)
                .replace("%player%", player.getName());

        TextUtil.sendMessage(player, formatted);
        line.playSound(player);

        for (String cmd : line.getCommands()) {
            executeCommand(cmd);
        }

        currentLineIndex++;

        // Auto schedule only if not strictly manual
        boolean lineIsManual = line.isManual() || config.getAdvanceMode() == TalkConfig.AdvanceMode.MANUAL;
        if (!lineIsManual) {
            long delay = Math.max(1, line.getDelayTicks());
            if (currentLineIndex < lines.size()) {
                currentTask = SchedulerUtil.runDelayed(plugin, player, this::deliverLine, delay);
            } else {
                currentTask = SchedulerUtil.runDelayed(plugin, player, this::finish, delay);
            }
        }
    }

    public void advanceManual() {
        if (!active || !player.isOnline()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastAdvanceTime < 250) {
            return; // debounce fast clicks
        }
        lastAdvanceTime = now;

        cancelTask();

        if (currentLineIndex < config.getLines().size()) {
            deliverLine();
        } else {
            finish();
        }
    }

    public void finish() {
        if (!active) {
            return;
        }
        active = false;
        smoothRotating = false;
        gracePeriod = false;
        pendingGroundAnchor = false;

        cancelAllTasks();
        removeEffects();

        // Optional footer
        String footer = plugin.getConfig().getString("chat.footer", "");
        if (!footer.isEmpty()) {
            TextUtil.sendMessage(player, footer);
        }

        // Completion message
        String compMsg = config.getCompleteMessage();
        if (compMsg != null && !compMsg.isEmpty()) {
            TextUtil.sendMessage(player, compMsg.replace("%npc%", npcName));
        }

        // Completion sound
        String compSound = config.getCompleteSound();
        if (compSound != null && !compSound.isEmpty()) {
            playSound(compSound);
        }

        // Completion commands
        for (String cmd : config.getCompleteCommands()) {
            executeCommand(cmd);
        }

        plugin.getTalkManager().onSessionEnded(this, true);
    }

    public void cancel(String reason, boolean notifyPlayer) {
        if (!active) {
            return;
        }
        active = false;
        smoothRotating = false;
        gracePeriod = false;
        pendingGroundAnchor = false;

        cancelAllTasks();
        removeEffects();

        if (notifyPlayer && player.isOnline()) {
            if (reason != null && !reason.isEmpty()) {
                TextUtil.sendMessage(player, reason.replace("%npc%", npcName));
            }
            String cancelSound = config.getCancelSound();
            if (cancelSound != null && !cancelSound.isEmpty()) {
                playSound(cancelSound);
            }
        }

        plugin.getTalkManager().onSessionEnded(this, false);
    }

    private void cancelAllTasks() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
        if (cameraTask != null) {
            cameraTask.cancel();
            cameraTask = null;
        }
        if (graceTask != null) {
            graceTask.cancel();
            graceTask = null;
        }
    }

    private void cancelTask() {
        if (currentTask != null) {
            currentTask.cancel();
            currentTask = null;
        }
    }

    private void playSound(String sound) {
        if (sound == null || sound.trim().isEmpty() || !player.isOnline()) {
            return;
        }
        try {
            String soundKey = sound.trim().toLowerCase(java.util.Locale.ROOT).replace("_", ".");
            if (!soundKey.contains(":")) {
                soundKey = "minecraft:" + soundKey;
            }
            net.kyori.adventure.key.Key key = net.kyori.adventure.key.Key.key(soundKey);
            player.playSound(net.kyori.adventure.sound.Sound.sound(key, net.kyori.adventure.sound.Sound.Source.VOICE, 1.0f, 1.0f));
        } catch (Throwable ignored) {
        }
    }

    private void executeCommand(String rawCommand) {
        if (rawCommand == null || rawCommand.trim().isEmpty() || !player.isOnline()) {
            return;
        }
        String cmd = rawCommand.replace("%player%", player.getName()).replace("%npc%", npcName);

        if (cmd.startsWith("[player] ")) {
            String playerCmd = cmd.substring(9).trim();
            SchedulerUtil.runNow(plugin, player, () -> player.performCommand(playerCmd));
        } else {
            String consoleCmd = cmd.startsWith("[console] ") ? cmd.substring(10).trim() : cmd.trim();
            SchedulerUtil.runGlobal(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), consoleCmd));
        }
    }

    public Player getPlayer() {
        return player;
    }

    public TalkConfig getConfig() {
        return config;
    }

    public Location getStartLocation() {
        return startLocation;
    }

    public Location getAnchoredLocation() {
        return anchoredLocation != null ? anchoredLocation : startLocation;
    }

    public Location getNpcLocation() {
        return npcLocation;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isGracePeriod() {
        return gracePeriod;
    }

    public boolean isSmoothRotating() {
        return smoothRotating;
    }

    public float getCurrentCameraYaw() {
        return currentCameraYaw;
    }

    public float getCurrentCameraPitch() {
        return currentCameraPitch;
    }

    public boolean isInternalTeleport() {
        return (System.currentTimeMillis() - lastInternalTeleportTime) < 350;
    }
}
