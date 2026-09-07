package com.willowdale.talky;

import com.willowdale.talky.command.TalkyCommand;
import com.willowdale.talky.listener.NpcInteractListener;
import com.willowdale.talky.listener.PlayerMoveListener;
import com.willowdale.talky.talk.TalkManager;
import com.willowdale.talky.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TalkyPlugin extends JavaPlugin {

    private static TalkyPlugin instance;
    private TalkManager talkManager;
    private NpcInteractListener npcListener;

    public static TalkyPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;

        // Save default configuration
        saveDefaultConfig();

        // Check for PlaceholderAPI
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            TextUtil.setPapiHooked(true);
            getLogger().info("PlaceholderAPI detected and hooked.");
        }

        // Initialize talk manager and load dialogues
        this.talkManager = new TalkManager(this);
        this.talkManager.loadTalks();

        // Register movement & cancellation listeners
        Bukkit.getPluginManager().registerEvents(new PlayerMoveListener(this), this);

        // Register NPC interaction listener and hooks (FancyNpcs, Citizens, Vanilla)
        this.npcListener = new NpcInteractListener(this);
        this.npcListener.registerHooks();

        // Register commands
        TalkyCommand commandExecutor = new TalkyCommand(this);

        PluginCommand talkyCmd = getCommand("talky");
        if (talkyCmd != null) {
            talkyCmd.setExecutor(commandExecutor);
            talkyCmd.setTabCompleter(commandExecutor);
        }

        PluginCommand talkCmd = getCommand("talk");
        if (talkCmd != null) {
            talkCmd.setExecutor(commandExecutor);
            talkCmd.setTabCompleter(commandExecutor);
        }

        getLogger().info("Talky v" + getDescription().getVersion() + " successfully enabled!");
    }

    @Override
    public void onDisable() {
        if (talkManager != null) {
            // Cancel all active conversations and remove FOV effects
            talkManager.cancelAllSessions("Server shutting down or reloading", false);
        }
        getLogger().info("Talky disabled.");
    }

    public TalkManager getTalkManager() {
        return talkManager;
    }
}
