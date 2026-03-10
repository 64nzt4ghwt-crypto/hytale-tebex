package com.howlstudio.tebex;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.basecommands.AbstractPlayerCommand;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Arrays;

public class TebexCommand extends AbstractPlayerCommand {
    private final TebexConfig config;
    private final TebexApi api;
    private final TebexPoller poller;

    public TebexCommand(TebexConfig config, TebexApi api, TebexPoller poller) {
        super("tebex", "TebexConnect admin. /tebex <status|reload|forcecheck>");
        this.config = config;
        this.api = api;
        this.poller = poller;
    }

    @Override
    protected void execute(CommandContext ctx, Store<EntityStore> store, Ref<EntityStore> ref,
                           PlayerRef playerRef, World world) {
        String input = ctx.getInputString().trim();
        String[] parts = input.split("\\s+");
        String[] args = parts.length > 1 ? Arrays.copyOfRange(parts, 1, parts.length) : new String[0];

        String sub = args.length > 0 ? args[0].toLowerCase() : "status";

        switch (sub) {
            case "status" -> {
                if (!config.isConfigured()) {
                    playerRef.sendMessage(Message.raw("§c[TebexConnect] Not configured. Add secret key to tebex-config.txt"));
                    return;
                }
                playerRef.sendMessage(Message.raw("§6[TebexConnect] §aConnected to Tebex"));
                playerRef.sendMessage(Message.raw("§7Poll interval: §f" + config.getPollInterval() + "s"));
                try {
                    String storeName = api.getStoreName();
                    playerRef.sendMessage(Message.raw("§7Store: §f" + storeName));
                } catch (Exception e) {
                    playerRef.sendMessage(Message.raw("§c[TebexConnect] Error fetching store info: " + e.getMessage()));
                }
            }
            case "reload" -> {
                playerRef.sendMessage(Message.raw("§6[TebexConnect] §fReloading config..."));
                // Restart poller
                poller.stop();
                poller.start();
                playerRef.sendMessage(Message.raw("§6[TebexConnect] §aDone."));
            }
            case "forcecheck" -> {
                playerRef.sendMessage(Message.raw("§6[TebexConnect] §fForce checking queue..."));
                // Restart poller with immediate poll
                poller.stop();
                poller.start();
                playerRef.sendMessage(Message.raw("§6[TebexConnect] §7Check scheduled."));
            }
            default -> {
                playerRef.sendMessage(Message.raw("§6[TebexConnect] §eCommands:"));
                playerRef.sendMessage(Message.raw("§f/tebex status §7— Connection status + store name"));
                playerRef.sendMessage(Message.raw("§f/tebex reload §7— Reload config"));
                playerRef.sendMessage(Message.raw("§f/tebex forcecheck §7— Force queue check now"));
            }
        }
    }
}
