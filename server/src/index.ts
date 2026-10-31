import http from "http";
import { WebSocket, WebSocketServer } from "ws";
import { sanitizeRoomCode } from "./protocol";
import { ArenaRoom, ClientSession } from "./room";

const PORT = Number(process.env.PORT || 8080);
const TICK_HZ = 20;
const TICK_MS = 1000 / TICK_HZ;
const MAX_PAYLOAD_BYTES = 2048;
const MAX_MSGS_PER_SECOND = 48; // 25 Hz input + headroom; spammers above this are kicked

const publicRooms: Map<string, ArenaRoom> = new Map();
const privateRooms: Map<string, ArenaRoom> = new Map();

interface SocketState {
  room: ArenaRoom | null;
  session: ClientSession | null;
  msgCountWindow: number;
  windowStartMs: number;
}

function totalOnlinePlayers(): number {
  let sum = 0;
  for (const r of publicRooms.values()) sum += r.activePlayerCount;
  for (const r of privateRooms.values()) sum += r.activePlayerCount;
  return sum;
}

function generateFourLetterCode(): string {
  const chars = "ABCDEFGHJKLMNPQRSTUVWXYZ";
  for (let attempt = 0; attempt < 100; attempt++) {
    let code = "";
    for (let i = 0; i < 4; i++) {
      code += chars[Math.floor(Math.random() * chars.length)];
    }
    if (!privateRooms.has(code)) return code;
  }
  return "COIL";
}

function pickBestPublicRoom(): ArenaRoom {
  // Auto-join the public room with the most free slots (that still has room)
  let best: ArenaRoom | null = null;
  let maxFree = 0;
  for (const r of publicRooms.values()) {
    const free = r.freeSlots;
    if (free > maxFree) {
      maxFree = free;
      best = r;
    }
  }
  if (best) return best;

  const id = `PUB-${publicRooms.size + 1}-${Date.now().toString(36).slice(-4).toUpperCase()}`;
  const created = new ArenaRoom(id, false, true);
  publicRooms.set(id, created);
  return created;
}

function findRoomByToken(token: string): ArenaRoom | null {
  for (const r of publicRooms.values()) {
    if (r.hasSession(token)) return r;
  }
  for (const r of privateRooms.values()) {
    if (r.hasSession(token)) return r;
  }
  return null;
}

const server = http.createServer((req, res) => {
  res.setHeader("Access-Control-Allow-Origin", "*");
  res.setHeader("Access-Control-Allow-Methods", "GET, OPTIONS");
  res.setHeader("Access-Control-Allow-Headers", "Content-Type");

  if (req.method === "OPTIONS") {
    res.writeHead(204);
    res.end();
    return;
  }

  if (req.url?.startsWith("/health") || req.url === "/") {
    const payload = JSON.stringify({
      ok: true,
      status: "healthy",
      online: totalOnlinePlayers(),
      publicRooms: publicRooms.size,
      privateRooms: privateRooms.size,
      tickHz: TICK_HZ
    });
    res.writeHead(200, { "Content-Type": "application/json" });
    res.end(payload);
    return;
  }

  res.writeHead(404, { "Content-Type": "application/json" });
  res.end(JSON.stringify({ ok: false, error: "Not found" }));
});

const wss = new WebSocketServer({
  server,
  maxPayload: MAX_PAYLOAD_BYTES
});

wss.on("connection", (ws: WebSocket) => {
  const state: SocketState = {
    room: null,
    session: null,
    msgCountWindow: 0,
    windowStartMs: Date.now()
  };

  ws.on("message", (rawData, isBinary) => {
    try {
      if (isBinary) return;

      // Per-connection rate limit
      const now = Date.now();
      if (now - state.windowStartMs >= 1000) {
        state.windowStartMs = now;
        state.msgCountWindow = 0;
      }
      state.msgCountWindow++;
      if (state.msgCountWindow > MAX_MSGS_PER_SECOND) {
        ws.close(4008, "Rate limit exceeded");
        return;
      }

      const str = rawData.toString("utf8");
      if (str.length > MAX_PAYLOAD_BYTES) return;
      const msg = JSON.parse(str);
      if (!msg || typeof msg !== "object" || typeof msg.t !== "string") return;

      switch (msg.t) {
        case "i": {
          // Client input: { t: "i", a: angle, b: boost, q: seq }
          if (state.room && state.session) {
            state.room.handleInput(state.session.token, msg.a, msg.b, msg.q);
          }
          break;
        }

        case "p": {
          // Ping echo for RTT badge
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ t: "p", ts: typeof msg.ts === "number" ? msg.ts : now }));
          }
          break;
        }

        case "join": {
          // Join public room, create private room, join 4-letter private room, or reconnect via tok
          if (state.room && state.session) {
            state.room.handleSocketDisconnect(state.session.token, true);
            state.room = null;
            state.session = null;
          }

          // 1) Try sessionToken reconnect first if supplied
          if (typeof msg.tok === "string" && msg.tok.length >= 8) {
            const existingRoom = findRoomByToken(msg.tok);
            if (existingRoom) {
              const rejoined = existingRoom.addOrReconnectPlayer(ws, msg.nick, msg.skin, msg.tok);
              if (rejoined) {
                state.room = existingRoom;
                state.session = rejoined;
                return;
              }
            }
          }

          const mode = typeof msg.mode === "string" ? msg.mode : "public";
          let targetRoom: ArenaRoom | null = null;

          if (mode === "create_private") {
            const code = generateFourLetterCode();
            const botsOn = msg.bots !== false && msg.bots !== 0;
            targetRoom = new ArenaRoom(code, true, botsOn);
            privateRooms.set(code, targetRoom);
          } else if (mode === "join_private") {
            const code = sanitizeRoomCode(msg.code);
            if (!code || !privateRooms.has(code)) {
              ws.send(JSON.stringify({ t: "err", msg: "Room not found" }));
              return;
            }
            targetRoom = privateRooms.get(code)!;
            if (targetRoom.freeSlots <= 0) {
              ws.send(JSON.stringify({ t: "err", msg: "Room is full (max 10)" }));
              return;
            }
          } else {
            targetRoom = pickBestPublicRoom();
          }

          const joined = targetRoom.addOrReconnectPlayer(ws, msg.nick, msg.skin);
          if (!joined) {
            ws.send(JSON.stringify({ t: "err", msg: "Unable to join room" }));
            return;
          }
          state.room = targetRoom;
          state.session = joined;
          break;
        }

        case "respawn": {
          if (state.room && state.session) {
            state.room.respawnSessionPlayer(state.session.token, msg.skin);
          }
          break;
        }

        case "set_bots": {
          if (state.room && state.session) {
            state.room.handleSetBots(state.session.token, Boolean(msg.bots));
          }
          break;
        }

        case "leave": {
          if (state.room && state.session) {
            state.room.handleSocketDisconnect(state.session.token, true);
            state.room = null;
            state.session = null;
          }
          ws.close(1000, "Client left");
          break;
        }

        default:
          // Ignore unknown message types safely
          break;
      }
    } catch {
      // Ignore malformed JSON or oversized packets without crashing
    }
  });

  ws.on("close", () => {
    if (state.room && state.session) {
      // Keep snake alive for 10s grace period in case of transient disconnect
      state.room.handleSocketDisconnect(state.session.token, false);
      state.room = null;
      state.session = null;
    }
  });

  ws.on("error", () => {
    // Prevent unhandled socket error crashes
  });
});

// Authoritative 20 Hz simulation loop + empty room cleanup
let lastTickMs = Date.now();
setInterval(() => {
  const now = Date.now();
  const dt = Math.min(0.1, Math.max(0.01, (now - lastTickMs) / 1000));
  lastTickMs = now;

  for (const [id, room] of publicRooms.entries()) {
    try {
      if (room.isEmptyAndExpired(now) && publicRooms.size > 1) {
        publicRooms.delete(id);
      } else {
        room.stepTick(dt);
      }
    } catch (err) {
      console.error(`Error stepping public room ${id}:`, err);
    }
  }

  for (const [code, room] of privateRooms.entries()) {
    try {
      if (room.isEmptyAndExpired(now)) {
        privateRooms.delete(code);
      } else {
        room.stepTick(dt);
      }
    } catch (err) {
      console.error(`Error stepping private room ${code}:`, err);
    }
  }
}, TICK_MS);

// Ensure at least one warm public room on boot
pickBestPublicRoom();

process.on("uncaughtException", (err) => {
  console.error("Uncaught exception (kept server alive):", err);
});
process.on("unhandledRejection", (reason) => {
  console.error("Unhandled rejection (kept server alive):", reason);
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`NeonCoil Authoritative Server listening on http://0.0.0.0:${PORT} (20 TPS)`);
});
