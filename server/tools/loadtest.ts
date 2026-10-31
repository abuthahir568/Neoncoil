import { WebSocket } from "ws";
import { SKIN_LIST } from "../src/protocol";

const URL = process.env.SERVER_URL || "ws://127.0.0.1:8080/ws";
const CLIENT_COUNT = Number(process.env.CLIENTS || 20);
const DURATION_MS = Number(process.env.DURATION_MS || 6000);

interface BotStats {
  connected: boolean;
  snapshots: number;
  lastPingMs: number;
  errors: number;
}

async function runLoadTest(): Promise<void> {
  console.log(`Starting NeonCoil loadtest: ${CLIENT_COUNT} fake clients -> ${URL} for ${DURATION_MS}ms...`);
  const stats: BotStats[] = [];
  const sockets: WebSocket[] = [];
  const timers: NodeJS.Timeout[] = [];

  for (let i = 0; i < CLIENT_COUNT; i++) {
    const st: BotStats = { connected: false, snapshots: 0, lastPingMs: 0, errors: 0 };
    stats.push(st);

    const ws = new WebSocket(URL);
    sockets.push(ws);

    let seq = 0;
    let angle = (i / CLIENT_COUNT) * Math.PI * 2 - Math.PI;

    ws.on("open", () => {
      st.connected = true;
      ws.send(
        JSON.stringify({
          t: "join",
          mode: "public",
          nick: `LoadBot${i + 1}`,
          skin: SKIN_LIST[i % SKIN_LIST.length]
        })
      );

      // Send steering + boost inputs at 25 Hz (40ms)
      const inputTimer = setInterval(() => {
        if (ws.readyState !== WebSocket.OPEN) return;
        angle += (Math.random() - 0.5) * 0.35;
        if (angle > Math.PI) angle -= Math.PI * 2;
        if (angle < -Math.PI) angle += Math.PI * 2;
        seq++;
        ws.send(
          JSON.stringify({
            t: "i",
            a: Number(angle.toFixed(3)),
            b: seq % 15 < 4 ? 1 : 0,
            q: seq
          })
        );
      }, 40);
      timers.push(inputTimer);

      const pingTimer = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ t: "p", ts: Date.now() }));
        }
      }, 1000);
      timers.push(pingTimer);
    });

    ws.on("message", (raw) => {
      try {
        const msg = JSON.parse(raw.toString());
        if (msg.t === "s") {
          st.snapshots++;
        } else if (msg.t === "p" && typeof msg.ts === "number") {
          st.lastPingMs = Date.now() - msg.ts;
        } else if (msg.t === "dead") {
          ws.send(JSON.stringify({ t: "respawn" }));
        }
      } catch {
        st.errors++;
      }
    });

    ws.on("error", () => {
      st.errors++;
    });
  }

  await new Promise((resolve) => setTimeout(resolve, DURATION_MS));

  for (const t of timers) clearInterval(t);
  for (const ws of sockets) {
    try {
      if (ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ t: "leave" }));
      }
      ws.close();
    } catch {
      // ignore
    }
  }

  const connectedCount = stats.filter((s) => s.connected).length;
  const totalSnaps = stats.reduce((acc, s) => acc + s.snapshots, 0);
  const avgPing =
    stats.reduce((acc, s) => acc + s.lastPingMs, 0) / Math.max(1, connectedCount);
  const totalErrors = stats.reduce((acc, s) => acc + s.errors, 0);

  console.log(
    `Loadtest complete: ${connectedCount}/${CLIENT_COUNT} connected | total snapshots=${totalSnaps} (~${(
      totalSnaps /
      Math.max(1, connectedCount) /
      (DURATION_MS / 1000)
    ).toFixed(1)} Hz/client) | avg RTT=${avgPing.toFixed(1)}ms | errors=${totalErrors}`
  );

  if (connectedCount < CLIENT_COUNT || totalSnaps === 0 || totalErrors > 0) {
    process.exitCode = 1;
  }
}

runLoadTest().catch((err) => {
  console.error("Loadtest failed:", err);
  process.exit(1);
});
