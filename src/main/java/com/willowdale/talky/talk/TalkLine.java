package com.willowdale.talky.talk;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;

public class TalkLine {

    private final String text;
    private final long delayTicks;
    private final String sound;
    private final List<String> commands;

    public TalkLine(String text, long delayTicks, String sound) {
        this(text, delayTicks, sound, Collections.emptyList());
    }

    public TalkLine(String text, long delayTicks, String sound, List<String> commands) {
        this.text = text != null ? text : "";
        this.delayTicks = delayTicks;
        this.sound = sound;
        this.commands = commands != null ? commands : Collections.emptyList();
    }

    public String getText() {
        return text;
    }

    public long getDelayTicks() {
        return delayTicks;
    }

    public boolean isManual() {
        return delayTicks < 0;
    }

    public String getSound() {
        return sound;
    }

    public List<String> getCommands() {
        return commands;
    }

    public void playSound(Player player) {
        if (player == null || sound == null || sound.trim().isEmpty()) {
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
}
