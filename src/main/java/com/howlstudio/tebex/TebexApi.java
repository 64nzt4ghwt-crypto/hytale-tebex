package com.howlstudio.tebex;

import com.google.gson.*;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public class TebexApi {
    private static final String BASE = "https://plugin.tebex.io";
    private final HttpClient http;
    private final String secret;
    private final Gson gson = new Gson();

    public TebexApi(String secret) {
        this.secret = secret;
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    private HttpRequest.Builder req(String path) {
        return HttpRequest.newBuilder()
            .uri(URI.create(BASE + path))
            .header("X-Tebex-Secret", secret)
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(15));
    }

    private String get(String path) throws Exception {
        var resp = http.send(req(path).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) throw new RuntimeException("Tebex API " + resp.statusCode() + " on " + path);
        return resp.body();
    }

    private void delete(String path, String body) throws Exception {
        var resp = http.send(req(path)
            .method("DELETE", HttpRequest.BodyPublishers.ofString(body))
            .build(), HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200 && resp.statusCode() != 204) {
            System.err.println("[TebexConnect] DELETE " + path + " returned " + resp.statusCode());
        }
    }

    /** Returns {nextCheck, players: [{id, name, uuid}]} */
    public QueueResult getQueue() throws Exception {
        JsonObject obj = gson.fromJson(get("/queue"), JsonObject.class);
        QueueResult result = new QueueResult();
        result.nextCheck = obj.getAsJsonObject("meta").get("next_check").getAsInt();
        result.executeOffline = obj.getAsJsonObject("meta").get("execute_offline").getAsBoolean();
        result.more = obj.getAsJsonObject("meta").get("more").getAsBoolean();
        JsonArray players = obj.getAsJsonArray("players");
        for (JsonElement el : players) {
            JsonObject p = el.getAsJsonObject();
            result.players.add(new QueuePlayer(
                p.get("id").getAsInt(),
                p.get("name").getAsString(),
                p.get("uuid").getAsString()
            ));
        }
        return result;
    }

    /** Get offline commands to execute immediately */
    public List<TebexCommand> getOfflineCommands() throws Exception {
        JsonObject obj = gson.fromJson(get("/queue/offline-commands"), JsonObject.class);
        List<TebexCommand> cmds = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray("commands")) {
            cmds.add(TebexCommand.from(el.getAsJsonObject()));
        }
        return cmds;
    }

    /** Get online commands for a specific player (by plugin id) */
    public List<TebexCommand> getOnlineCommands(int playerId) throws Exception {
        JsonObject obj = gson.fromJson(get("/queue/online-commands/" + playerId), JsonObject.class);
        List<TebexCommand> cmds = new ArrayList<>();
        for (JsonElement el : obj.getAsJsonArray("commands")) {
            cmds.add(TebexCommand.from(el.getAsJsonObject()));
        }
        return cmds;
    }

    /** Acknowledge executed commands so they're removed from the queue */
    public void deleteCommands(List<Integer> ids) throws Exception {
        if (ids.isEmpty()) return;
        JsonObject body = new JsonObject();
        JsonArray arr = new JsonArray();
        for (int id : ids) arr.add(id);
        body.add("ids", arr);
        delete("/queue", gson.toJson(body));
    }

    /** Get store information (used for connection test) */
    public String getStoreName() throws Exception {
        JsonObject obj = gson.fromJson(get("/information"), JsonObject.class);
        return obj.getAsJsonObject("account").get("name").getAsString();
    }

    public static class QueueResult {
        public int nextCheck = 90;
        public boolean executeOffline = false;
        public boolean more = false;
        public List<QueuePlayer> players = new ArrayList<>();
    }

    public static class QueuePlayer {
        public final int id;
        public final String name;
        public final String uuid;
        public QueuePlayer(int id, String name, String uuid) {
            this.id = id; this.name = name; this.uuid = uuid;
        }
    }

    public static class TebexCommand {
        public final int id;
        public final String command;
        public final int delaySeconds;
        public final String playerName;
        public final String playerUuid;

        public TebexCommand(int id, String command, int delaySeconds, String playerName, String playerUuid) {
            this.id = id; this.command = command; this.delaySeconds = delaySeconds;
            this.playerName = playerName; this.playerUuid = playerUuid;
        }

        public static TebexCommand from(JsonObject obj) {
            int delay = 0;
            if (obj.has("conditions") && obj.getAsJsonObject("conditions").has("delay")) {
                delay = obj.getAsJsonObject("conditions").get("delay").getAsInt();
            }
            JsonObject player = obj.getAsJsonObject("player");
            return new TebexCommand(
                obj.get("id").getAsInt(),
                obj.get("command").getAsString(),
                delay,
                player.get("name").getAsString(),
                player.get("uuid").getAsString()
            );
        }
    }
}
