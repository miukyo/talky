package com.willowdale.talky.listener;

import com.willowdale.talky.TalkyPlugin;
import com.willowdale.talky.talk.TalkSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

public class PlayerMoveListener implements Listener {

    private final TalkyPlugin plugin;

    public PlayerMoveListener(TalkyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        TalkSession session = plugin.getTalkManager().getActiveSession(player);
        if (session == null || !session.isActive()) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        // World change triggers immediate cancellation
        if (from.getWorld() != to.getWorld()) {
            session.cancel(session.getConfig().getCancelMessage(), true);
            return;
        }

        // Camera lock: force camera to look at NPC eye level, allow body to move
        if (session.getNpcLocation() != null && session.getConfig().isLockCamera()) {
            if (session.isSmoothRotating()) {
                // Keep player camera locked along the smooth interpolation path
                event.setTo(new Location(to.getWorld(), to.getX(), to.getY(), to.getZ(),
                        session.getCurrentCameraYaw(), session.getCurrentCameraPitch()));
            } else {
                Location npcEye = session.getNpcLocation();
                double playerEyeY = to.getY() + player.getEyeHeight();
                double dx = npcEye.getX() - to.getX();
                double dy = npcEye.getY() - playerEyeY;
                double dz = npcEye.getZ() - to.getZ();
                double distXZ = Math.hypot(dx, dz);

                if (distXZ >= 0.001) {
                    float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                    float targetPitch = (float) -Math.toDegrees(Math.atan2(dy, distXZ));

                    // If player attempts to move camera, lock yaw and pitch back to NPC
                    if (Math.abs(to.getYaw() - targetYaw) > 0.1f || Math.abs(to.getPitch() - targetPitch) > 0.1f) {
                        event.setTo(new Location(to.getWorld(), to.getX(), to.getY(), to.getZ(), targetYaw, targetPitch));
                    }
                }
            }
        }

        // Update ground landing anchor if player was airborne during initiation
        if (player.isOnGround()) {
            session.onGroundLanded(to);
        }

        // Positional movement cancellation
        if (session.getConfig().isCancelOnMove()) {
            if (session.isGracePeriod()) {
                // Grace period on initiation: player was running/holding W when talk started
                // Allow deceleration and falling to ground; only cancel if moving excessively far (> 6 blocks horizontal)
                if (session.getNpcLocation() != null) {
                    double dx = to.getX() - session.getNpcLocation().getX();
                    double dz = to.getZ() - session.getNpcLocation().getZ();
                    if (dx * dx + dz * dz > 36.0) {
                        session.cancel(session.getConfig().getCancelMessage(), true);
                    }
                } else {
                    double dx = to.getX() - session.getStartLocation().getX();
                    double dz = to.getZ() - session.getStartLocation().getZ();
                    if (dx * dx + dz * dz > 16.0) {
                        session.cancel(session.getConfig().getCancelMessage(), true);
                    }
                }
            } else {
                // Grace period has elapsed: check distance from anchored position
                Location anchor = session.getAnchoredLocation();
                if (anchor != null) {
                    // Only check horizontal (X, Z) displacement for walking away!
                    // Gravity (falling / landing on ground) is vertical and must not cancel dialogue
                    double totalDx = to.getX() - anchor.getX();
                    double totalDz = to.getZ() - anchor.getZ();
                    double horizontalDistSq = totalDx * totalDx + totalDz * totalDz;

                    if (horizontalDistSq > session.getConfig().getMoveThresholdSquared()) {
                        session.cancel(session.getConfig().getCancelMessage(), true);
                        return;
                    }

                    // Cancel only if player drops a large vertical distance (e.g. cliff / hole > 3.5 blocks)
                    double totalDy = to.getY() - anchor.getY();
                    if (Math.abs(totalDy) > 3.5) {
                        session.cancel(session.getConfig().getCancelMessage(), true);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        TalkSession session = plugin.getTalkManager().getActiveSession(player);
        if (session != null && session.isActive() && session.getConfig().isAdvanceOnSneak()) {
            plugin.getTalkManager().advanceTalk(player);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        TalkSession session = plugin.getTalkManager().getActiveSession(player);
        if (session != null && session.isActive() && session.getConfig().isAdvanceOnClick()) {
            Action action = event.getAction();
            if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK ||
                action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
                event.setCancelled(true);
                plugin.getTalkManager().advanceTalk(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        TalkSession session = plugin.getTalkManager().getActiveSession(player);
        if (session != null && session.isActive()) {
            if (session.isInternalTeleport()) {
                return; // Do not cancel on talky's internal camera rotations
            }
            session.cancel(session.getConfig().getCancelMessage(), true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!plugin.getConfig().getBoolean("settings.cancel-on-damage", true)) {
            return;
        }
        if (event.getEntity() instanceof Player player) {
            TalkSession session = plugin.getTalkManager().getActiveSession(player);
            if (session != null && session.isActive()) {
                session.cancel("<red><i>Conversation interrupted by taking damage!</i></red>", true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        plugin.getTalkManager().cancelTalk(event.getPlayer(), null, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getTalkManager().cancelTalk(event.getPlayer(), null, false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        plugin.getTalkManager().cancelTalk(event.getPlayer(), null, false);
    }
}
