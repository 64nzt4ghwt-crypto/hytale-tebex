package com.howlstudio.tebex;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Background thread that polls the Tebex API on the recommended interval
 * and dispatches commands via CommandExecutor.
 */
public class TebexPoller {
    private final TebexApi api;
    private final TebexConfig config;
    private final CommandExecutor executor;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ScheduledFuture<?> task;

    public TebexPoller(TebexApi api, TebexConfig config, CommandExecutor executor) {
        this.api = api;
        this.config = config;
        this.executor = executor;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "TebexConnect-poller"); t.setDaemon(true); return t; }
        );
    }

    public void start() {
        if (running.getAndSet(true)) return;
        scheduleNext(config.getPollInterval());
        System.out.println("[TebexConnect] Polling Tebex every " + config.getPollInterval() + "s");
    }

    public void stop() {
        running.set(false);
        if (task != null) task.cancel(false);
        scheduler.shutdownNow();
    }

    private void scheduleNext(int delaySecs) {
        task = scheduler.schedule(this::poll, delaySecs, TimeUnit.SECONDS);
    }

    private void poll() {
        if (!running.get()) return;
        int nextCheck = config.getPollInterval();
        try {
            // 1. Process offline commands
            List<TebexApi.TebexCommand> offlineCmds = api.getOfflineCommands();
            List<Integer> toDelete = new ArrayList<>();
            for (TebexApi.TebexCommand cmd : offlineCmds) {
                executor.execute(cmd, 0);
                toDelete.add(cmd.id);
            }
            if (!toDelete.isEmpty()) {
                api.deleteCommands(toDelete);
                System.out.println("[TebexConnect] Executed " + toDelete.size() + " offline command(s).");
            }

            // 2. Get queue + online commands
            TebexApi.QueueResult queue = api.getQueue();
            nextCheck = queue.nextCheck;
            config.setPollInterval(nextCheck);

            List<Integer> onlineToDelete = new ArrayList<>();
            for (TebexApi.QueuePlayer player : queue.players) {
                if (!executor.isOnline(player.uuid)) continue;
                List<TebexApi.TebexCommand> cmds = api.getOnlineCommands(player.id);
                for (TebexApi.TebexCommand cmd : cmds) {
                    executor.execute(cmd, cmd.delaySeconds);
                    onlineToDelete.add(cmd.id);
                }
            }
            if (!onlineToDelete.isEmpty()) {
                api.deleteCommands(onlineToDelete);
                System.out.println("[TebexConnect] Executed " + onlineToDelete.size() + " online command(s).");
            }

        } catch (Exception e) {
            System.err.println("[TebexConnect] Poll error: " + e.getMessage());
            nextCheck = Math.max(30, nextCheck);
        } finally {
            if (running.get()) scheduleNext(nextCheck);
        }
    }
}
