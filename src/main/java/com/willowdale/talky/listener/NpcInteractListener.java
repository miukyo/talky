package com.willowdale.talky.listener;

import com.willowdale.talky.TalkyPlugin;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.EventExecutor;

import java.lang.reflect.Method;

public class NpcInteractListener implements Listener {

    private final TalkyPlugin plugin;

    public NpcInteractListener(TalkyPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerHooks() {
        // Register this listener for Bukkit events
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Register FancyNpcs hook if available
        hookFancyNpcs();

        // Register Citizens hook if available
        hookCitizens();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        // Only trigger on main hand interaction to avoid double triggering
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        if (plugin.getTalkManager().isTalking(player)) {
            var session = plugin.getTalkManager().getActiveSession(player);
            if (session != null && session.isActive() && session.getConfig().isAdvanceOnClick()) {
                event.setCancelled(true);
                plugin.getTalkManager().advanceTalk(player);
                return;
            }
        }

        Entity entity = event.getRightClicked();

        String entityName = null;
        if (entity.customName() != null) {
            entityName = PlainTextComponentSerializer.plainText().serialize(entity.customName()).trim();
        } else if (entity.getCustomName() != null) {
            entityName = entity.getCustomName().trim();
        }

        if (entityName != null && !entityName.isEmpty()) {
            Location npcLoc = (entity instanceof org.bukkit.entity.LivingEntity living)
                    ? living.getEyeLocation()
                    : entity.getLocation().clone().add(0, 1.62, 0);
            if (plugin.getTalkManager().startTalkForNpc(player, entityName, npcLoc)) {
                event.setCancelled(true);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void hookFancyNpcs() {
        if (!Bukkit.getPluginManager().isPluginEnabled("FancyNpcs")) {
            return;
        }

        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName("de.oliver.fancynpcs.api.events.NpcInteractEvent");

            EventExecutor executor = (listener, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                try {
                    Method getPlayerMethod = event.getClass().getMethod("getPlayer");
                    Player player = (Player) getPlayerMethod.invoke(event);

                    Method getNpcMethod = event.getClass().getMethod("getNpc");
                    Object npc = getNpcMethod.invoke(event);
                    if (npc == null || player == null) {
                        return;
                    }

                    if (plugin.getTalkManager().isTalking(player)) {
                        var session = plugin.getTalkManager().getActiveSession(player);
                        if (session != null && session.isActive() && session.getConfig().isAdvanceOnClick()) {
                            try {
                                Method setCancelled = event.getClass().getMethod("setCancelled", boolean.class);
                                setCancelled.invoke(event, true);
                            } catch (Throwable ignored) {
                            }
                            plugin.getTalkManager().advanceTalk(player);
                            return;
                        }
                    }

                    String npcName = null;
                    Location npcLocation = null;

                    try {
                        Method getDataMethod = npc.getClass().getMethod("getData");
                        Object data = getDataMethod.invoke(npc);
                        if (data != null) {
                            Method getNameMethod = data.getClass().getMethod("getName");
                            npcName = (String) getNameMethod.invoke(data);

                            try {
                                Method getLocationMethod = data.getClass().getMethod("getLocation");
                                npcLocation = (Location) getLocationMethod.invoke(data);
                            } catch (Throwable ignored) {
                            }
                        }
                    } catch (NoSuchMethodException e) {
                        Method getNameMethod = npc.getClass().getMethod("getName");
                        npcName = (String) getNameMethod.invoke(npc);
                    }

                    if (npcLocation == null) {
                        try {
                            Method getLocationMethod = npc.getClass().getMethod("getLocation");
                            npcLocation = (Location) getLocationMethod.invoke(npc);
                        } catch (Throwable ignored) {
                        }
                    }

                    if (npcLocation != null) {
                        npcLocation = npcLocation.clone().add(0, 1.62, 0);
                    }

                    if (npcName != null && !npcName.isEmpty()) {
                        if (plugin.getTalkManager().startTalkForNpc(player, npcName, npcLocation)) {
                            try {
                                Method setCancelled = event.getClass().getMethod("setCancelled", boolean.class);
                                setCancelled.invoke(event, true);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Error handling FancyNpcs interact event: " + t.getMessage());
                }
            };

            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    this,
                    EventPriority.NORMAL,
                    executor,
                    plugin
            );
            plugin.getLogger().info("Successfully hooked into FancyNpcs interaction events!");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("FancyNpcs event class not found, skipping hook.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not hook FancyNpcs: " + t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void hookCitizens() {
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            return;
        }

        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName("net.citizensnpcs.api.event.NPCRightClickEvent");

            EventExecutor executor = (listener, event) -> {
                if (!eventClass.isInstance(event)) {
                    return;
                }
                try {
                    Method getClickerMethod = event.getClass().getMethod("getClicker");
                    Player player = (Player) getClickerMethod.invoke(event);

                    Method getNpcMethod = event.getClass().getMethod("getNPC");
                    Object npc = getNpcMethod.invoke(event);
                    if (npc == null || player == null) {
                        return;
                    }

                    if (plugin.getTalkManager().isTalking(player)) {
                        var session = plugin.getTalkManager().getActiveSession(player);
                        if (session != null && session.isActive() && session.getConfig().isAdvanceOnClick()) {
                            try {
                                Method setCancelled = event.getClass().getMethod("setCancelled", boolean.class);
                                setCancelled.invoke(event, true);
                            } catch (Throwable ignored) {
                            }
                            plugin.getTalkManager().advanceTalk(player);
                            return;
                        }
                    }

                    Method getNameMethod = npc.getClass().getMethod("getName");
                    String npcName = (String) getNameMethod.invoke(npc);

                    Location npcLocation = null;
                    try {
                        Method getStoredLocationMethod = npc.getClass().getMethod("getStoredLocation");
                        npcLocation = (Location) getStoredLocationMethod.invoke(npc);
                    } catch (Throwable ignored) {
                        try {
                            Method getEntityMethod = npc.getClass().getMethod("getEntity");
                            Entity npcEntity = (Entity) getEntityMethod.invoke(npc);
                            if (npcEntity != null) {
                                npcLocation = npcEntity.getLocation();
                            }
                        } catch (Throwable ignored2) {
                        }
                    }

                    if (npcLocation != null) {
                        npcLocation = npcLocation.clone().add(0, 1.62, 0);
                    }

                    if (npcName != null && !npcName.isEmpty()) {
                        if (plugin.getTalkManager().startTalkForNpc(player, npcName, npcLocation)) {
                            try {
                                Method setCancelled = event.getClass().getMethod("setCancelled", boolean.class);
                                setCancelled.invoke(event, true);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Error handling Citizens interact event: " + t.getMessage());
                }
            };

            Bukkit.getPluginManager().registerEvent(
                    eventClass,
                    this,
                    EventPriority.NORMAL,
                    executor,
                    plugin
            );
            plugin.getLogger().info("Successfully hooked into Citizens interaction events!");
        } catch (ClassNotFoundException ignored) {
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not hook Citizens: " + t.getMessage());
        }
    }
}
