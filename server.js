import http from "http";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
import { WebSocketServer, WebSocket } from "ws";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Servidor HTTP — sirve el mando web al móvil
const server = http.createServer((req, res) => {
  if (req.url === "/" || req.url === "/index.html") {
    fs.readFile(path.join(__dirname, "mobile-web", "index.html"), (err, data) => {
      if (err) {
        res.writeHead(500);
        return res.end("Error cargando mobile-web/index.html");
      }
      res.writeHead(200, { "Content-Type": "text/html" });
      res.end(data);
    });
  } else {
    res.writeHead(404);
    res.end();
  }
});

const wss = new WebSocketServer({ server });

let players = { 1: null, 2: null };
let unitySocket = null;

// Tipos que Unity envía hacia los móviles (siempre incluyen playerId)
const UNITY_TO_PLAYER_TYPES = new Set([
  "collision",
  "death",
  "pickup",
  "ui_update",
  "screen_effect",
  "puzzle_start",
  "custom",
]);

function sendToUnity(payload) {
  if (unitySocket && unitySocket.readyState === WebSocket.OPEN) {
    unitySocket.send(JSON.stringify(payload));
  } else {
    console.warn(
      "[Servidor] Unity no está conectado, mensaje descartado:",
      payload.type,
    );
  }
}

function sendToPlayer(playerId, payload) {
  const player = players[playerId];
  if (player && player.readyState === WebSocket.OPEN) {
    player.send(JSON.stringify(payload));
  } else {
    console.warn(`[Servidor] Jugador ${playerId} no disponible`);
  }
}

wss.on("connection", (ws) => {
  console.log("Nueva conexión entrante");

  ws.on("message", (messageAsString) => {
    try {
      const data = JSON.parse(messageAsString.toString());

      // ── Mensajes desde Unity ──────────────────────────────────────────
      if (ws === unitySocket) {
        if (UNITY_TO_PLAYER_TYPES.has(data.type) && data.playerId) {
          sendToPlayer(data.playerId, data);
          console.log(`[Unity → Jugador ${data.playerId}] type: ${data.type}`);
        } else {
          console.warn("[Unity] Mensaje sin type válido o sin playerId:", data);
        }
        return;
      }

      // ── Unity se registra ─────────────────────────────────────────────
      if (data.type === "join_unity") {
        unitySocket = ws;
        ws.isUnity = true;
        ws.send(JSON.stringify({ type: "unity_ready" }));
        console.log("✅ Unity conectado como receptor principal");
        return;
      }

      // ── Móvil pide unirse ─────────────────────────────────────────────
      if (data.type === "join") {
        if (!players[1]) {
          players[1] = ws;
          ws.playerId = 1;
        } else if (!players[2]) {
          players[2] = ws;
          ws.playerId = 2;
        } else {
          ws.send(JSON.stringify({ type: "error", message: "Sala llena" }));
          return;
        }
        ws.send(JSON.stringify({ type: "assignRole", playerId: ws.playerId }));
        console.log(`🎮 Mando asignado como Jugador ${ws.playerId}`);
        return;
      }

      // Sin rol asignado → ignorar el resto
      if (!ws.playerId) return;

      // ── Input de movimiento/sensores ──────────────────────────────────
      if (data.type === "input") {
        sendToUnity({ ...data, playerId: ws.playerId });
        return;
      }

      // ── Acción de puzzle ──────────────────────────────────────────────
      if (data.type === "puzzle_action") {
        sendToUnity({ ...data, playerId: ws.playerId });
        console.log(
          `[Jugador ${ws.playerId}] puzzle_action: ${data.puzzleId} → ${data.status}`,
        );
        return;
      }
    } catch (e) {
      console.error("Error parseando mensaje:", e.message);
    }
  });

  ws.on("close", () => {
    if (ws === unitySocket) {
      unitySocket = null;
      console.log("⚠️  Unity desconectado");
      return;
    }
    if (ws.playerId === 1) players[1] = null;
    if (ws.playerId === 2) players[2] = null;
    console.log(`Jugador ${ws.playerId || "sin rol"} desconectado`);
  });
});

server.listen(3000, "0.0.0.0", () => {
  console.log("Servidor HTTP y WebSocket escuchando en el puerto 3000");
});
