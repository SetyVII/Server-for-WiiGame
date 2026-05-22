package server;

import io.javalin.Javalin;
import io.javalin.websocket.WsContext;
import model.ClientStore;
import model.ConnectionState;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

public class Main {
    private static final Gson gson = new Gson();

    // Message types sent from Unity that should be routed to a specific player
    private static final Set<String> UNITY_TO_PLAYER_TYPES = Set.of(
            "collision",
            "death",
            "pickup",
            "ui_update",
            "screen_effect",
            "puzzle_start",
            "custom"
    );

    public static void main(String[] args) {
        int port = 3002;

        System.out.println("Iniciando WiiGames Javalin Server...");

        Javalin app = Javalin.create(config -> {
            // Standard config - Jetty is configured automatically.
        });

        // 1. Serve index.html at root "/" and "/index.html"
        app.get("/", ctx -> serveIndexHtml(ctx));
        app.get("/index.html", ctx -> serveIndexHtml(ctx));

        // 2. Setup WebSocket endpoint on "/"
        app.ws("/", ws -> {
            ws.onConnect(ctx -> {
                System.out.println("[WebSocket] Nueva conexión desde: " + ctx.session.getRemoteAddress());
                ClientStore.sessionStates.put(ctx.sessionId(), new ConnectionState());
            });

            ws.onClose(ctx -> {
                ConnectionState state = ClientStore.sessionStates.remove(ctx.sessionId());

                if (ClientStore.unitySocket != null && ctx.sessionId().equals(ClientStore.unitySocket.sessionId())) {
                    ClientStore.unitySocket = null;
                    System.out.println("⚠️ Unity desconectado");
                    return;
                }

                if (state != null && state.playerId != null) {
                    ClientStore.players.remove(state.playerId);
                    System.out.println("Jugador " + state.playerId + " desconectado");
                } else {
                    // Backup check in case state was inconsistent
                    Integer foundPlayerId = null;
                    for (Map.Entry<Integer, WsContext> entry : ClientStore.players.entrySet()) {
                        if (entry.getValue().sessionId().equals(ctx.sessionId())) {
                            foundPlayerId = entry.getKey();
                            ClientStore.players.remove(foundPlayerId);
                            break;
                        }
                    }
                    if (foundPlayerId != null) {
                        System.out.println("Jugador " + foundPlayerId + " desconectado");
                    } else {
                        System.out.println("Conexión sin rol desconectada");
                    }
                }
            });

            ws.onMessage(ctx -> {
                String message = ctx.message();
                try {
                    JsonObject data = gson.fromJson(message, JsonObject.class);
                    if (data == null) return;

                    String type = data.has("type") ? data.get("type").getAsString() : "";
                    ConnectionState state = ClientStore.sessionStates.get(ctx.sessionId());
                    if (state == null) {
                        state = new ConnectionState();
                        ClientStore.sessionStates.put(ctx.sessionId(), state);
                    }

                    // Check if message is from Unity
                    boolean isUnity = state.isUnity;

                    // ── Messages from Unity ──────────────────────────────────────────
                    if (isUnity) {
                        if (UNITY_TO_PLAYER_TYPES.contains(type) && data.has("playerId")) {
                            int targetPlayerId = data.get("playerId").getAsInt();
                            sendToPlayer(targetPlayerId, message);
                            System.out.println("[Unity → Jugador " + targetPlayerId + "] type: " + type);
                        } else {
                            System.out.println("[Unity] Mensaje sin type válido o sin playerId: " + message);
                        }
                        return;
                    }

                    // ── Unity registering ─────────────────────────────────────────────
                    if ("join_unity".equals(type)) {
                        ClientStore.unitySocket = ctx;
                        state.isUnity = true;

                        JsonObject response = new JsonObject();
                        response.addProperty("type", "unity_ready");
                        ctx.send(gson.toJson(response));
                        System.out.println("✅ Unity conectado como receptor principal");
                        return;
                    }

                    // ── Mobile client requests to join ───────────────────────────────
                    if ("join".equals(type)) {
                        synchronized (ClientStore.players) {
                            if (ClientStore.players.get(1) == null) {
                                ClientStore.players.put(1, ctx);
                                state.playerId = 1;
                            } else if (ClientStore.players.get(2) == null) {
                                ClientStore.players.put(2, ctx);
                                state.playerId = 2;
                            } else {
                                JsonObject errorResponse = new JsonObject();
                                errorResponse.addProperty("type", "error");
                                errorResponse.addProperty("message", "Sala llena");
                                ctx.send(gson.toJson(errorResponse));
                                System.out.println("⚠️ Intento de conexión rechazado: Sala llena");
                                return;
                            }
                        }

                        JsonObject roleResponse = new JsonObject();
                        roleResponse.addProperty("type", "assignRole");
                        roleResponse.addProperty("playerId", state.playerId);
                        ctx.send(gson.toJson(roleResponse));
                        System.out.println("🎮 Mando asignado como Jugador " + state.playerId);
                        return;
                    }

                    // If the connection has no role assigned, ignore all other inputs
                    if (state.playerId == null) {
                        return;
                    }

                    // ── Movement/Sensor input from mobile ────────────────────────────
                    if ("input".equals(type)) {
                        data.addProperty("playerId", state.playerId);
                        sendToUnity(gson.toJson(data));
                        return;
                    }

                    // ── Puzzle action from mobile ─────────────────────────────────────
                    if ("puzzle_action".equals(type)) {
                        data.addProperty("playerId", state.playerId);
                        sendToUnity(gson.toJson(data));

                        String puzzleId = data.has("puzzleId") ? data.get("puzzleId").getAsString() : "unknown";
                        String status = data.has("status") ? data.get("status").getAsString() : "unknown";
                        System.out.println("[Jugador " + state.playerId + "] puzzle_action: " + puzzleId + " → " + status);
                        return;
                    }

                } catch (JsonSyntaxException e) {
                    System.err.println("Error parseando mensaje JSON: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Error al procesar mensaje: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            ws.onError(ctx -> {
                System.err.println("Error en la conexión WebSocket:");
                if (ctx.error() != null) {
                    ctx.error().printStackTrace();
                } else {
                    System.err.println("desconocido (error es null)");
                }
            });
        });

        app.start(port);
        System.out.println("==================================================================");
        System.out.println("Servidor Javalin único iniciado en el puerto " + port);
        System.out.println("- Mando Móvil: Abre http://<tu-ip-local>:" + port + " o vía ngrok");
        System.out.println("- Unity: Conectar a ws://<tu-ip-local>:" + port);
        System.out.println("==================================================================");
    }

    private static void serveIndexHtml(io.javalin.http.Context ctx) {
        try (InputStream is = Main.class.getResourceAsStream("/index.html")) {
            if (is == null) {
                ctx.status(404).result("index.html no encontrado en los recursos del servidor.");
                return;
            }
            byte[] htmlBytes = is.readAllBytes();
            ctx.contentType("text/html; charset=utf-8").result(new String(htmlBytes, StandardCharsets.UTF_8));
        } catch (Exception e) {
            ctx.status(500).result("Error interno del servidor: " + e.getMessage());
        }
    }

    private static void sendToUnity(String payload) {
        WsContext unity = ClientStore.unitySocket;
        if (unity != null && unity.session.isOpen()) {
            unity.send(payload);
        }
    }

    private static void sendToPlayer(int playerId, String payload) {
        WsContext player = ClientStore.players.get(playerId);
        if (player != null && player.session.isOpen()) {
            player.send(payload);
        } else {
            System.out.println("[Servidor] Jugador " + playerId + " no disponible para mensaje.");
        }
    }
}
