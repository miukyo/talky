package com.willowdale.talky.talk;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TalkConfig {

    public enum AdvanceMode {
        AUTO,
        MANUAL,
        HYBRID
    }

    private final String id;
    private final String title;
    private final List<String> npcNames;
    private final int cooldownSeconds;
    private final String permission;

    private final boolean fovEnabled;
    private final int fovAmplifier;
    private final boolean blindnessEnabled;

    private final boolean cancelOnMove;
    private final double moveThresholdSquared;
    private final int initiationGraceTicks;
    private final String cancelMessage;
    private final String cancelSound;

    private final boolean lockCamera;
    private final int cameraSmoothTicks;

    private final AdvanceMode advanceMode;
    private final boolean advanceOnSneak;
    private final boolean advanceOnClick;

    private final List<TalkLine> lines;

    private final String completeSound;
    private final String completeMessage;
    private final List<String> completeCommands;

    public TalkConfig(String id,
                      String title,
                      List<String> npcNames,
                      int cooldownSeconds,
                      String permission,
                      boolean fovEnabled,
                      int fovAmplifier,
                      boolean blindnessEnabled,
                      boolean cancelOnMove,
                      double moveThresholdSquared,
                      int initiationGraceTicks,
                      String cancelMessage,
                      String cancelSound,
                      boolean lockCamera,
                      int cameraSmoothTicks,
                      AdvanceMode advanceMode,
                      boolean advanceOnSneak,
                      boolean advanceOnClick,
                      List<TalkLine> lines,
                      String completeSound,
                      String completeMessage,
                      List<String> completeCommands) {
        this.id = id;
        this.title = title;
        this.npcNames = npcNames != null ? npcNames : Collections.emptyList();
        this.cooldownSeconds = cooldownSeconds;
        this.permission = permission != null ? permission : "";
        this.fovEnabled = fovEnabled;
        this.fovAmplifier = fovAmplifier;
        this.blindnessEnabled = blindnessEnabled;
        this.cancelOnMove = cancelOnMove;
        this.moveThresholdSquared = moveThresholdSquared;
        this.initiationGraceTicks = initiationGraceTicks;
        this.cancelMessage = cancelMessage;
        this.cancelSound = cancelSound;
        this.lockCamera = lockCamera;
        this.cameraSmoothTicks = cameraSmoothTicks;
        this.advanceMode = advanceMode != null ? advanceMode : AdvanceMode.HYBRID;
        this.advanceOnSneak = advanceOnSneak;
        this.advanceOnClick = advanceOnClick;
        this.lines = lines != null ? lines : Collections.emptyList();
        this.completeSound = completeSound;
        this.completeMessage = completeMessage;
        this.completeCommands = completeCommands != null ? completeCommands : Collections.emptyList();
    }

    public static TalkConfig fromYaml(String id,
                                     FileConfiguration config,
                                     long defaultDelay,
                                     int defaultFovAmp,
                                     boolean defaultBlindness,
                                     double defaultThreshold,
                                     int defaultGraceTicks,
                                     String defaultCancelMsg,
                                     String defaultCancelSound,
                                     boolean defaultLockCamera,
                                     int defaultSmoothTicks,
                                     AdvanceMode defaultMode,
                                     boolean defaultAdvSneak,
                                     boolean defaultAdvClick) {
        String title = config.getString("title", id);
        List<String> npcNames = config.getStringList("npc-names");
        int cooldown = config.getInt("cooldown", 0);
        String perm = config.getString("permission", "");

        boolean fovEnabled = config.getBoolean("fov.enabled", true);
        int fovAmp = config.getInt("fov.amplifier", defaultFovAmp);
        boolean blindnessEnabled = config.getBoolean("blindness.enabled", defaultBlindness);

        boolean cancelOnMove = config.getBoolean("movement.cancel-on-move", true);
        double threshold = config.getDouble("movement.move-threshold", defaultThreshold);
        double thresholdSquared = threshold * threshold;
        int graceTicks = config.getInt("movement.initiation-grace-ticks", defaultGraceTicks);
        String cancelMsg = config.getString("movement.cancel-message", defaultCancelMsg);
        String cancelSnd = config.getString("movement.cancel-sound", defaultCancelSound);

        boolean lockCamera = config.getBoolean("interaction.lock-camera", defaultLockCamera);
        int smoothTicks = config.getInt("interaction.camera-smooth-ticks", defaultSmoothTicks);

        String modeStr = config.getString("advance.mode", defaultMode.name()).toUpperCase(Locale.ROOT);
        AdvanceMode mode;
        try {
            mode = AdvanceMode.valueOf(modeStr);
        } catch (IllegalArgumentException e) {
            mode = defaultMode;
        }

        boolean advSneak = config.getBoolean("advance.advance-on-sneak", defaultAdvSneak);
        boolean advClick = config.getBoolean("advance.advance-on-click", defaultAdvClick);

        List<TalkLine> talkLines = new ArrayList<>();
        List<?> rawLines = config.getList("lines");
        if (rawLines != null) {
            for (Object obj : rawLines) {
                if (obj instanceof String text) {
                    talkLines.add(new TalkLine(text, defaultDelay, null));
                } else if (obj instanceof List<?> list) {
                    talkLines.add(new TalkLine(parseTextList(list), defaultDelay, null));
                } else if (obj instanceof Map<?, ?> map) {
                    String text = parseTextObject(map.get("text"));
                    long delay = defaultDelay;
                    if (map.containsKey("delay")) {
                        try {
                            delay = Long.parseLong(String.valueOf(map.get("delay")));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    if (Boolean.TRUE.equals(map.get("manual"))) {
                        delay = -1;
                    }
                    String sound = map.containsKey("sound") ? String.valueOf(map.get("sound")) : null;
                    List<String> commands = parseCommands(map.get("commands"), map.get("command"));
                    talkLines.add(new TalkLine(text, delay, sound, commands));
                } else if (obj instanceof ConfigurationSection sec) {
                    String text = parseTextObject(sec.get("text"));
                    long delay = sec.getBoolean("manual", false) ? -1 : sec.getLong("delay", defaultDelay);
                    String sound = sec.getString("sound", null);
                    List<String> commands = parseCommands(sec.get("commands"), sec.get("command"));
                    talkLines.add(new TalkLine(text, delay, sound, commands));
                }
            }
        }

        String compSound = config.getString("on-complete.sound", "");
        String compMsg = config.getString("on-complete.message", "");
        List<String> compCmds = config.getStringList("on-complete.commands");

        return new TalkConfig(
                id,
                title,
                npcNames,
                cooldown,
                perm,
                fovEnabled,
                fovAmp,
                blindnessEnabled,
                cancelOnMove,
                thresholdSquared,
                graceTicks,
                cancelMsg,
                cancelSnd,
                lockCamera,
                smoothTicks,
                mode,
                advSneak,
                advClick,
                talkLines,
                compSound,
                compMsg,
                compCmds
        );
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getNpcNames() {
        return npcNames;
    }

    public int getCooldownSeconds() {
        return cooldownSeconds;
    }

    public String getPermission() {
        return permission;
    }

    public boolean isFovEnabled() {
        return fovEnabled;
    }

    public int getFovAmplifier() {
        return fovAmplifier;
    }

    public boolean isBlindnessEnabled() {
        return blindnessEnabled;
    }

    public boolean isCancelOnMove() {
        return cancelOnMove;
    }

    public double getMoveThresholdSquared() {
        return moveThresholdSquared;
    }

    public int getInitiationGraceTicks() {
        return initiationGraceTicks;
    }

    public String getCancelMessage() {
        return cancelMessage;
    }

    public String getCancelSound() {
        return cancelSound;
    }

    public boolean isLockCamera() {
        return lockCamera;
    }

    public int getCameraSmoothTicks() {
        return cameraSmoothTicks;
    }

    public AdvanceMode getAdvanceMode() {
        return advanceMode;
    }

    public boolean isAdvanceOnSneak() {
        return advanceOnSneak;
    }

    public boolean isAdvanceOnClick() {
        return advanceOnClick;
    }

    public List<TalkLine> getLines() {
        return lines;
    }

    public String getCompleteSound() {
        return completeSound;
    }

    public String getCompleteMessage() {
        return completeMessage;
    }

    public List<String> getCompleteCommands() {
        return completeCommands;
    }

    private static String parseTextObject(Object obj) {
        if (obj == null) {
            return "";
        }
        if (obj instanceof List<?> list) {
            return parseTextList(list);
        }
        return String.valueOf(obj);
    }

    private static String parseTextList(List<?> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append("\n");
            }
            Object item = list.get(i);
            sb.append(item != null ? String.valueOf(item) : "");
        }
        return sb.toString();
    }

    private static List<String> parseCommands(Object commandsObj, Object commandObj) {
        List<String> list = parseStringList(commandsObj);
        if (!list.isEmpty()) {
            return list;
        }
        return parseStringList(commandObj);
    }

    private static List<String> parseStringList(Object obj) {
        if (obj == null) {
            return Collections.emptyList();
        }
        if (obj instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
        if (obj instanceof String str && !str.trim().isEmpty()) {
            return Collections.singletonList(str);
        }
        return Collections.emptyList();
    }
}
