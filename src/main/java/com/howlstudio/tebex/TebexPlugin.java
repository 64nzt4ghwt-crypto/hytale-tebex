package com.howlstudio.tebex;

import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

public final class TebexPlugin extends JavaPlugin {

    private TebexPoller poller;
    private CommandExecutor commandExecutor;

    public TebexPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        System.out.println("[TebexConnect] Loading...");

        TebexConfig config = new TebexConfig(getDataDirectory());
        commandExecutor = new CommandExecutor();

        if (!config.isConfigured()) {
            System.out.println("[TebexConnect] ⚠ No secret key configured.");
            System.out.println("[TebexConnect] Add your key to: " + getDataDirectory().resolve("tebex-config.txt"));
            System.out.println("[TebexConnect] Get it from: Tebex Dashboard → Game Server Settings");
            // Register command so admins can check status
            TebexApi dummyApi = new TebexApi("");
            poller = new TebexPoller(dummyApi, config, commandExecutor);
            CommandManager.get().register(new TebexCommand(config, dummyApi, poller));
            new TebexListener(commandExecutor).register();
            return;
        }

        TebexApi api = new TebexApi(config.getSecretKey());

        try {
            String storeName = api.getStoreName();
            System.out.println("[TebexConnect] Connected to store: " + storeName);
        } catch (Exception e) {
            System.err.println("[TebexConnect] ⚠ Could not connect to Tebex API: " + e.getMessage());
        }

        new TebexListener(commandExecutor).register();
        poller = new TebexPoller(api, config, commandExecutor);
        poller.start();

        CommandManager.get().register(new TebexCommand(config, api, poller));

        System.out.println("[TebexConnect] Ready. Use /tebex status to verify.");
    }
}
