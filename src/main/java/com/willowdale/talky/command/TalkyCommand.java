package com.willowdale.talky.command;

import com.willowdale.talky.TalkyPlugin;
import com.willowdale.talky.talk.TalkConfig;
import com.willowdale.talky.util.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class TalkyCommand implements CommandExecutor, TabCompleter {

    private final TalkyPlugin plugin;

    public TalkyCommand(TalkyPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Direct /talk <talkId> alias
        if (label.equalsIgnoreCase("talk")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command is for players only.");
                return true;
            }
            if (args.length < 1) {
                TextUtil.sendMessage(player, "<gold>Usage:</gold> <yellow>/talk <dialogue_id></yellow>");
                return true;
            }
            String talkId = args[0];
            plugin.getTalkManager().startTalk(player, talkId, null);
            return true;
        }

        // Subcommands for /talky
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "next" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("This command is for players only.");
                    return true;
                }
                plugin.getTalkManager().advanceTalk(player);
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("talky.admin")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                plugin.reloadConfig();
                plugin.getTalkManager().loadTalks();
                String reloaded = plugin.getConfig().getString("messages.reloaded", "<green>Talky reloaded!</green>");
                sender.sendMessage(TextUtil.parse(sender instanceof Player p ? p : null, reloaded));
            }
            case "list" -> {
                if (!sender.hasPermission("talky.admin")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                sender.sendMessage(TextUtil.parse(null, "<gold><bold>=== Loaded Talks ===</bold></gold>"));
                for (TalkConfig talk : plugin.getTalkManager().getAllTalks()) {
                    String npcs = String.join(", ", talk.getNpcNames());
                    sender.sendMessage(TextUtil.parse(null, "<yellow>• <gold>" + talk.getId() + "</gold> (<white>" + talk.getTitle() + "</white>) - NPCs: [" + npcs + "] Lines: " + talk.getLines().size()));
                }
            }
            case "start" -> {
                if (!sender.hasPermission("talky.admin")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("Usage: /talky start <talk_id> [player]");
                    return true;
                }
                String talkId = args[1];
                Player target = null;
                if (args.length >= 3) {
                    target = Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        sender.sendMessage("Player not found: " + args[2]);
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    sender.sendMessage("Console must specify a player: /talky start <talk_id> <player>");
                    return true;
                }

                boolean started = plugin.getTalkManager().startTalk(target, talkId, null);
                if (started && target != sender) {
                    sender.sendMessage("Started talk '" + talkId + "' for " + target.getName() + ".");
                }
            }
            case "stop" -> {
                if (!sender.hasPermission("talky.admin")) {
                    sender.sendMessage("No permission.");
                    return true;
                }
                Player target = null;
                if (args.length >= 2) {
                    target = Bukkit.getPlayer(args[1]);
                    if (target == null) {
                        sender.sendMessage("Player not found: " + args[1]);
                        return true;
                    }
                } else if (sender instanceof Player p) {
                    target = p;
                } else {
                    sender.sendMessage("Console must specify a player: /talky stop <player>");
                    return true;
                }

                boolean stopped = plugin.getTalkManager().cancelTalk(target, "<yellow>Conversation stopped by an administrator.</yellow>", true);
                if (stopped) {
                    sender.sendMessage("Stopped active conversation for " + target.getName() + ".");
                } else {
                    sender.sendMessage(target.getName() + " is not currently in a conversation.");
                }
            }
            default -> sendHelp(sender);
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(TextUtil.parse(null, "<gold><bold>Talky Commands:</bold></gold>"));
        sender.sendMessage(TextUtil.parse(null, "<yellow>/talky reload</yellow> <gray>- Reload configuration and dialogues</gray>"));
        sender.sendMessage(TextUtil.parse(null, "<yellow>/talky list</yellow> <gray>- List loaded dialogues</gray>"));
        sender.sendMessage(TextUtil.parse(null, "<yellow>/talky start <talk_id> [player]</yellow> <gray>- Start dialogue for player</gray>"));
        sender.sendMessage(TextUtil.parse(null, "<yellow>/talky stop [player]</yellow> <gray>- Stop dialogue for player</gray>"));
        sender.sendMessage(TextUtil.parse(null, "<yellow>/talk <talk_id></yellow> <gray>- Start dialogue</gray>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (alias.equalsIgnoreCase("talk")) {
            if (args.length == 1) {
                return filterPrefix(args[0], plugin.getTalkManager().getAllTalks().stream().map(TalkConfig::getId).toList());
            }
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterPrefix(args[0], Arrays.asList("reload", "list", "start", "stop"));
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return filterPrefix(args[1], plugin.getTalkManager().getAllTalks().stream().map(TalkConfig::getId).toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("stop")) {
            return filterPrefix(args[1], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("start")) {
            return filterPrefix(args[2], Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
        }

        return Collections.emptyList();
    }

    private List<String> filterPrefix(String prefix, Iterable<String> items) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String item : items) {
            if (item.toLowerCase(Locale.ROOT).startsWith(lower)) {
                matches.add(item);
            }
        }
        return matches;
    }
}
