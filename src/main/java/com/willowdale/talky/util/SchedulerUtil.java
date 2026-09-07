package com.willowdale.talky.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SchedulerUtil {

    private static final boolean IS_FOLIA;
    private static Method ENTITY_GET_SCHEDULER;
    private static Method ENTITY_SCHEDULER_RUN;
    private static Method ENTITY_SCHEDULER_RUN_DELAYED;
    private static Method ENTITY_SCHEDULER_RUN_AT_FIXED_RATE;

    private static Method BUKKIT_GET_GLOBAL_REGION_SCHEDULER;
    private static Method GLOBAL_SCHEDULER_RUN;
    private static Method GLOBAL_SCHEDULER_RUN_DELAYED;
    private static Method GLOBAL_SCHEDULER_RUN_AT_FIXED_RATE;

    static {
        boolean folia = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;

            ENTITY_GET_SCHEDULER = Entity.class.getMethod("getScheduler");
            Class<?> entitySchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            ENTITY_SCHEDULER_RUN = entitySchedulerClass.getMethod("run", Plugin.class, Consumer.class, Runnable.class);
            ENTITY_SCHEDULER_RUN_DELAYED = entitySchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, Runnable.class, long.class);
            ENTITY_SCHEDULER_RUN_AT_FIXED_RATE = entitySchedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, Runnable.class, long.class, long.class);

            BUKKIT_GET_GLOBAL_REGION_SCHEDULER = Bukkit.class.getMethod("getGlobalRegionScheduler");
            Class<?> globalSchedulerClass = Class.forName("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler");
            GLOBAL_SCHEDULER_RUN = globalSchedulerClass.getMethod("run", Plugin.class, Consumer.class);
            GLOBAL_SCHEDULER_RUN_DELAYED = globalSchedulerClass.getMethod("runDelayed", Plugin.class, Consumer.class, long.class);
            GLOBAL_SCHEDULER_RUN_AT_FIXED_RATE = globalSchedulerClass.getMethod("runAtFixedRate", Plugin.class, Consumer.class, long.class, long.class);
        } catch (Throwable ignored) {
            folia = false;
        }
        IS_FOLIA = folia;
    }

    private SchedulerUtil() {
    }

    public static boolean isFolia() {
        return IS_FOLIA;
    }

    public interface TaskHandle {
        void cancel();
    }

    public static TaskHandle runDelayed(Plugin plugin, Entity entity, Runnable runnable, long delayTicks) {
        if (delayTicks <= 0) {
            return runNow(plugin, entity, runnable);
        }

        if (IS_FOLIA) {
            if (entity != null && ENTITY_GET_SCHEDULER != null && ENTITY_SCHEDULER_RUN_DELAYED != null) {
                try {
                    Object scheduler = ENTITY_GET_SCHEDULER.invoke(entity);
                    AtomicBoolean cancelled = new AtomicBoolean(false);
                    Object scheduledTask = ENTITY_SCHEDULER_RUN_DELAYED.invoke(scheduler, plugin, (Consumer<Object>) o -> {
                        if (!cancelled.get()) {
                            runnable.run();
                        }
                    }, null, delayTicks);

                    return () -> {
                        cancelled.set(true);
                        cancelFoliaTask(scheduledTask);
                    };
                } catch (Throwable ignored) {
                }
            } else if (BUKKIT_GET_GLOBAL_REGION_SCHEDULER != null && GLOBAL_SCHEDULER_RUN_DELAYED != null) {
                try {
                    Object globalScheduler = BUKKIT_GET_GLOBAL_REGION_SCHEDULER.invoke(null);
                    AtomicBoolean cancelled = new AtomicBoolean(false);
                    Object scheduledTask = GLOBAL_SCHEDULER_RUN_DELAYED.invoke(globalScheduler, plugin, (Consumer<Object>) o -> {
                        if (!cancelled.get()) {
                            runnable.run();
                        }
                    }, delayTicks);

                    return () -> {
                        cancelled.set(true);
                        cancelFoliaTask(scheduledTask);
                    };
                } catch (Throwable ignored) {
                }
            }
        }

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, runnable, delayTicks);
        return task::cancel;
    }

    public static TaskHandle runRepeating(Plugin plugin, Entity entity, Runnable runnable, long delayTicks, long periodTicks) {
        long effectiveDelay = Math.max(1, delayTicks);
        long effectivePeriod = Math.max(1, periodTicks);

        if (IS_FOLIA) {
            if (entity != null && ENTITY_GET_SCHEDULER != null && ENTITY_SCHEDULER_RUN_AT_FIXED_RATE != null) {
                try {
                    Object scheduler = ENTITY_GET_SCHEDULER.invoke(entity);
                    AtomicBoolean cancelled = new AtomicBoolean(false);
                    Object scheduledTask = ENTITY_SCHEDULER_RUN_AT_FIXED_RATE.invoke(scheduler, plugin, (Consumer<Object>) o -> {
                        if (!cancelled.get()) {
                            runnable.run();
                        }
                    }, null, effectiveDelay, effectivePeriod);

                    return () -> {
                        cancelled.set(true);
                        cancelFoliaTask(scheduledTask);
                    };
                } catch (Throwable ignored) {
                }
            } else if (BUKKIT_GET_GLOBAL_REGION_SCHEDULER != null && GLOBAL_SCHEDULER_RUN_AT_FIXED_RATE != null) {
                try {
                    Object globalScheduler = BUKKIT_GET_GLOBAL_REGION_SCHEDULER.invoke(null);
                    AtomicBoolean cancelled = new AtomicBoolean(false);
                    Object scheduledTask = GLOBAL_SCHEDULER_RUN_AT_FIXED_RATE.invoke(globalScheduler, plugin, (Consumer<Object>) o -> {
                        if (!cancelled.get()) {
                            runnable.run();
                        }
                    }, effectiveDelay, effectivePeriod);

                    return () -> {
                        cancelled.set(true);
                        cancelFoliaTask(scheduledTask);
                    };
                } catch (Throwable ignored) {
                }
            }
        }

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, runnable, effectiveDelay, effectivePeriod);
        return task::cancel;
    }

    public static TaskHandle runNow(Plugin plugin, Entity entity, Runnable runnable) {
        if (IS_FOLIA) {
            if (entity != null && ENTITY_GET_SCHEDULER != null && ENTITY_SCHEDULER_RUN != null) {
                try {
                    Object scheduler = ENTITY_GET_SCHEDULER.invoke(entity);
                    AtomicBoolean cancelled = new AtomicBoolean(false);
                    Object scheduledTask = ENTITY_SCHEDULER_RUN.invoke(scheduler, plugin, (Consumer<Object>) o -> {
                        if (!cancelled.get()) {
                            runnable.run();
                        }
                    }, null);

                    return () -> {
                        cancelled.set(true);
                        cancelFoliaTask(scheduledTask);
                    };
                } catch (Throwable ignored) {
                }
            } else if (BUKKIT_GET_GLOBAL_REGION_SCHEDULER != null && GLOBAL_SCHEDULER_RUN != null) {
                try {
                    Object globalScheduler = BUKKIT_GET_GLOBAL_REGION_SCHEDULER.invoke(null);
                    AtomicBoolean cancelled = new AtomicBoolean(false);
                    Object scheduledTask = GLOBAL_SCHEDULER_RUN.invoke(globalScheduler, plugin, (Consumer<Object>) o -> {
                        if (!cancelled.get()) {
                            runnable.run();
                        }
                    });

                    return () -> {
                        cancelled.set(true);
                        cancelFoliaTask(scheduledTask);
                    };
                } catch (Throwable ignored) {
                }
            }
        }

        BukkitTask task = Bukkit.getScheduler().runTask(plugin, runnable);
        return task::cancel;
    }

    public static TaskHandle runGlobal(Plugin plugin, Runnable runnable) {
        return runNow(plugin, null, runnable);
    }

    public static TaskHandle runGlobalDelayed(Plugin plugin, Runnable runnable, long delayTicks) {
        return runDelayed(plugin, null, runnable, delayTicks);
    }

    private static void cancelFoliaTask(Object scheduledTask) {
        if (scheduledTask != null) {
            try {
                Method cancelMethod = scheduledTask.getClass().getMethod("cancel");
                cancelMethod.invoke(scheduledTask);
            } catch (Throwable ignored) {
            }
        }
    }
}
