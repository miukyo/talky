package com.willowdale.talky.talk;

import com.willowdale.talky.TalkyPlugin;
import com.willowdale.talky.util.TextUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TalkManager {

    private final TalkyPlugin plugin;
    private final Map<String, TalkConfig> talksById = new HashMap<>();
    private final Map<String, TalkConfig> talksByNpcName = new HashMap<>();
    private final Map<UUID, TalkSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, Long> cooldowns = new ConcurrentHashMap<>();

    public TalkManager(TalkyPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadTalks() {
        // Cancel all existing sessions cleanly before reload
        cancelAllSessions("Plugin reloading", false);

        talksById.clear();
        talksByNpcName.clear();

        File talksFolder = new File(plugin.getDataFolder(), "talks");
        if (!talksFolder.exists()) {
            talksFolder.mkdirs();
            saveDefaultTalkFile("talks/guard.yml", new File(talksFolder, "guard.yml"));
            saveDefaultTalkFile("talks/welcome.yml", new File(talksFolder, "welcome.yml"));
        }

        File[] files = talksFolder.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (files == null || files.length == 0) {
            plugin.getLogger().warning("No dialogue files found in talks/ directory!");
            return;
        }

        long defaultDelay = plugin.getConfig().getLong("settings.default-line-delay", 45);
        int defaultFov = plugin.getConfig().getInt("settings.fov.amplifier", 2);
        boolean defaultBlindness = plugin.getConfig().getBoolean("settings.blindness.enabled", true);
        double defaultThreshold = plugin.getConfig().getDouble("settings.movement.move-threshold", 0.8);
        int defaultGraceTicks = plugin.getConfig().getInt("settings.movement.initiation-grace-ticks", 25);
        String defaultCancelMsg = plugin.getConfig().getString("settings.movement.cancel-message", "<red>Conversation cancelled.</red>");
        String defaultCancelSnd = plugin.getConfig().getString("settings.movement.cancel-sound", "block.fire.extinguish");

        String defaultModeStr = plugin.getConfig().getString("settings.advance.mode", "HYBRID").toUpperCase(Locale.ROOT);
        TalkConfig.AdvanceMode defaultMode;
        try {
            defaultMode = TalkConfig.AdvanceMode.valueOf(defaultModeStr);
        } catch (IllegalArgumentException e) {
            defaultMode = TalkConfig.AdvanceMode.HYBRID;
        }
        boolean defaultAdvSneak = plugin.getConfig().getBoolean("settings.advance.advance-on-sneak", true);
        boolean defaultAdvClick = plugin.getConfig().getBoolean("settings.advance.advance-on-click", true);

        boolean defaultLockCamera = plugin.getConfig().getBoolean("settings.interaction.lock-camera", true);
        int defaultSmoothTicks = plugin.getConfig().getInt("settings.interaction.camera-smooth-ticks", 12);

        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String id = yaml.getString("id", file.getName().replace(".yml", "").replace(".yaml", "")).toLowerCase(Locale.ROOT);
                TalkConfig config = TalkConfig.fromYaml(
                        id,
                        yaml,
                        defaultDelay,
                        defaultFov,
                        defaultBlindness,
                        defaultThreshold,
                        defaultGraceTicks,
                        defaultCancelMsg,
                        defaultCancelSnd,
                        defaultLockCamera,
                        defaultSmoothTicks,
                        defaultMode,
                        defaultAdvSneak,
                        defaultAdvClick
                );
                talksById.put(id, config);

                for (String npcName : config.getNpcNames()) {
                    talksByNpcName.put(npcName.toLowerCase(Locale.ROOT), config);
                }

                plugin.getLogger().info("Loaded talk dialogue: " + id + " (" + config.getLines().size() + " lines)");
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load dialogue file " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    private void saveDefaultTalkFile(String resourcePath, File destination) {
        if (destination.exists()) {
            return;
        }
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in != null) {
                Files.copy(in, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save default resource " + resourcePath + ": " + e.getMessage());
        }
    }

    public boolean startTalk(Player player, String talkId, String customNpcName) {
        return startTalk(player, talkId, customNpcName, null);
    }

    public boolean startTalk(Player player, String talkId, String customNpcName, org.bukkit.Location npcLocation) {
        if (player == null || !player.isOnline()) {
            return false;
        }

        TalkConfig config = talksById.get(talkId.toLowerCase(Locale.ROOT));
        if (config == null) {
            String notFound = plugin.getConfig().getString("messages.talk-not-found", "<red>Conversation '%talk%' not found.</red>");
            TextUtil.sendMessage(player, notFound.replace("%talk%", talkId));
            return false;
        }

        if (isTalking(player)) {
            TalkSession session = getActiveSession(player);
            if (session != null && session.isActive() && session.getConfig().isAdvanceOnClick()) {
                advanceTalk(player);
                return true;
            }
            String already = plugin.getConfig().getString("messages.already-talking", "<red>You are already talking to an NPC!</red>");
            TextUtil.sendMessage(player, already);
            return false;
        }

        // Permission check
        String permission = config.getPermission();
        if (permission != null && !permission.isEmpty() && !player.hasPermission(permission)) {
            String noPerm = plugin.getConfig().getString("messages.no-permission", "<red>No permission.</red>");
            TextUtil.sendMessage(player, noPerm);
            return false;
        }

        // Cooldown check
        if (config.getCooldownSeconds() > 0) {
            String cdKey = player.getUniqueId() + "_" + config.getId();
            Long expire = cooldowns.get(cdKey);
            long now = System.currentTimeMillis();
            if (expire != null && expire > now) {
                long remainingSec = (expire - now + 999) / 1000;
                String cdMsg = plugin.getConfig().getString("messages.cooldown", "<gray>Please wait %time%s.</gray>");
                TextUtil.sendMessage(player, cdMsg.replace("%time%", String.valueOf(remainingSec)));
                return false;
            }
        }

        TalkSession session = new TalkSession(plugin, player, config, customNpcName, npcLocation);
        activeSessions.put(player.getUniqueId(), session);
        session.start();
        return true;
    }

    public boolean startTalkForNpc(Player player, String npcName) {
        return startTalkForNpc(player, npcName, null);
    }

    public boolean startTalkForNpc(Player player, String npcName, org.bukkit.Location npcLocation) {
        if (npcName == null || npcName.trim().isEmpty()) {
            return false;
        }
        if (isTalking(player)) {
            TalkSession session = getActiveSession(player);
            if (session != null && session.isActive() && session.getConfig().isAdvanceOnClick()) {
                advanceTalk(player);
                return true;
            }
        }
        TalkConfig config = talksByNpcName.get(npcName.trim().toLowerCase(Locale.ROOT));
        if (config != null) {
            return startTalk(player, config.getId(), npcName, npcLocation);
        }
        return false;
    }

    public void onSessionEnded(TalkSession session, boolean completed) {
        activeSessions.remove(session.getPlayer().getUniqueId());
        if (completed && session.getConfig().getCooldownSeconds() > 0) {
            String cdKey = session.getPlayer().getUniqueId() + "_" + session.getConfig().getId();
            long expire = System.currentTimeMillis() + (session.getConfig().getCooldownSeconds() * 1000L);
            cooldowns.put(cdKey, expire);
        }
    }

    public boolean cancelTalk(Player player, String reason, boolean notifyPlayer) {
        if (player == null) {
            return false;
        }
        TalkSession session = activeSessions.get(player.getUniqueId());
        if (session != null) {
            session.cancel(reason, notifyPlayer);
            return true;
        }
        return false;
    }

    public boolean advanceTalk(Player player) {
        if (player == null) {
            return false;
        }
        TalkSession session = activeSessions.get(player.getUniqueId());
        if (session != null && session.isActive()) {
            session.advanceManual();
            return true;
        }
        return false;
    }

    public void cancelAllSessions(String reason, boolean notifyPlayer) {
        for (TalkSession session : activeSessions.values()) {
            session.cancel(reason, notifyPlayer);
        }
        activeSessions.clear();
    }

    public boolean isTalking(Player player) {
        if (player == null) {
            return false;
        }
        TalkSession session = activeSessions.get(player.getUniqueId());
        return session != null && session.isActive();
    }

    public TalkSession getActiveSession(Player player) {
        if (player == null) {
            return null;
        }
        return activeSessions.get(player.getUniqueId());
    }

    public TalkConfig getTalk(String talkId) {
        return talksById.get(talkId.toLowerCase(Locale.ROOT));
    }

    public Collection<TalkConfig> getAllTalks() {
        return Collections.unmodifiableCollection(talksById.values());
    }
}
