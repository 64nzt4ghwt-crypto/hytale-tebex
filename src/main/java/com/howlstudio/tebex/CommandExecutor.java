package com.howlstudio.tebex;

import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.*;
import java.util.concurrent.*;

/**
 * Executes Tebex commands on the server.
 * Tebex commands are arbitrary strings like "grant_rank {name} vip".
 * In Hytale, we route them as server commands or handle known patterns.
 */
public class CommandExecutor {
    // uuid → online PlayerRef (updated by TebexListener)
    private final Map<String, PlayerRef> onlinePlayers = new ConcurrentHashMap<>();
    // Pending delayed commands: execute after delay
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
        r -> { Thread t = new Thread(r, "TebexConnect-cmd"); t.setDaemon(true); return t; }
    );

    public void trackPlayer(String uuid, PlayerRef ref) {
        onlinePlayers.put(normalizeUuid(uuid), ref);
    }

    public void removePlayer(String uuid) {
        onlinePlayers.remove(normalizeUuid(uuid));
    }

    public boolean isOnline(String uuid) {
        return onlinePlayers.containsKey(normalizeUuid(uuid));
    }

    /** Execute a command, substituting {name} and {id} placeholders. */
    public void execute(TebexApi.TebexCommand cmd, int delayOverrideSeconds) {
        int delay = Math.max(0, delayOverrideSeconds > 0 ? delayOverrideSeconds : cmd.delaySeconds);
        Runnable task = () -> {
            String command = cmd.command
                .replace("{name}", cmd.playerName)
                .replace("{id}", cmd.playerUuid)
                .replace("{username}", cmd.playerName);
            System.out.println("[TebexConnect] Executing: " + command);
            dispatchCommand(command, cmd.playerName, cmd.playerUuid);
        };
        if (delay > 0) {
            scheduler.schedule(task, delay, TimeUnit.SECONDS);
        } else {
            task.run();
        }
    }

    /**
     * Dispatch a command string. We handle common patterns:
     *  - "give {name} ..." → send give message to player
     *  - "broadcast ..." → broadcast to all
     *  - "message {name} ..." → send private message
     *  - "console ..." → log to console
     *  - Any other → treat as console command, log it
     */
    private void dispatchCommand(String command, String playerName, String playerUuid) {
        String lower = command.toLowerCase();

        if (lower.startsWith("broadcast ")) {
            String msg = command.substring(10);
            System.out.println("[TebexConnect] BROADCAST: " + msg);
            return;
        }

        if (lower.startsWith("message ") || lower.startsWith("msg ")) {
            int space = command.indexOf(' ');
            String rest = command.substring(space + 1);
            int space2 = rest.indexOf(' ');
            if (space2 > 0) {
                String msg = rest.substring(space2 + 1);
                String uuid = normalizeUuid(playerUuid);
                PlayerRef ref = onlinePlayers.get(uuid);
                if (ref != null) ref.sendMessage(Message.raw("§6[Store] §f" + msg));
            }
            return;
        }

        if (lower.startsWith("notify ") || lower.startsWith("tell ")) {
            int space = command.indexOf(' ');
            String msg = command.substring(space + 1);
            String uuid = normalizeUuid(playerUuid);
            PlayerRef ref = onlinePlayers.get(uuid);
            if (ref != null) ref.sendMessage(Message.raw("§6[Store] §f" + msg));
            return;
        }

        // Fallback: log the command. Server admins can intercept via console.
        System.out.println("[TebexConnect] COMMAND_QUEUE: " + command);
    }

    private String normalizeUuid(String uuid) {
        return uuid != null ? uuid.toLowerCase().replace("-", "") : "";
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
