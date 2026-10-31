# NeonCoil: Snake Arena

**NeonCoil: Snake Arena** is a bright, colorful cartoon snake arena game featuring:
- **100% Offline Infinite Mode** with smart AI bots, singleton session state, instant `Pause -> Home -> Continue` with `3 - 2 - 1` resume countdown, and automatic 3-second local snapshot saves.
- **Authoritative Online Multiplayer (`/server`)** running a 20-ticks/sec Node.js + TypeScript + `ws` server with spatial-grid viewport culling, food delta compression, public matchmaking (up to 20 real players + dynamic AI bot fill), private 4-letter Friend Rooms (up to 10 players with Host Bots On/Off toggle), 10-second disconnect grace reconnects, 60 FPS client prediction, and ~100ms remote snake interpolation.
- **Customizable Compact Non-Overlapping HUD & Controls** with live preview sliders (`Controls size` 50%–150%, `HUD size` 60%–140%) and full safe-area + `12dp` edge inset protection.

---

## 1. Run the Multiplayer Server Locally

```bash
cd server
npm install
npm run dev
```

- The server starts on `http://0.0.0.0:8080` by default (configurable via `PORT`).
- Verify health in your browser or terminal:
  ```bash
  curl http://127.0.0.1:8080/health
  # {"ok":true,"status":"healthy","online":0,"publicRooms":1,"privateRooms":0,"tickHz":20}
  ```

---

## 2. Test Multiplayer & Run the 20-Client Load Test

### Stress-test with 20 concurrent simulated players
With the server running in one terminal, open a second terminal:
```bash
cd server
npm run loadtest
```
`tools/loadtest.ts` connects **20 simultaneous WebSocket clients** sending steering & boost inputs at **25 Hz** and verifies smooth **20 Hz** snapshot delivery, RTT ping measurement, and zero server errors.

### Test with Android Emulator / Multiple Clients
- By default, the Android app connects to `ws://10.0.2.2:8080/ws` (`10.0.2.2` routes from the Android Emulator directly to `127.0.0.1` on your computer).
- Launch two emulator instances or connect a physical phone on the same Wi-Fi (updating `NEONCOIL_SERVER_URL` to `ws://<YOUR_LAN_IP>:8080/ws`):
  - Tap **PLAY ONLINE** on both devices to enter the same Public Arena, **or**
  - Tap **FRIENDS -> CREATE ROOM** on Device 1 to get a **4-letter room code** (e.g. `KPRM`), then tap **FRIENDS -> JOIN** with that code on Device 2!
- If the server is stopped or unreachable after 5 seconds, the game automatically offers **"Can't reach server. Play offline instead?"** so offline play always works seamlessly.

---

## 3. Deploy `/server` on Render (Free Tier)

1. Push this repository to GitHub.
2. In the [Render Dashboard](https://dashboard.render.com/), click **New + -> Web Service** and connect your repository.
3. Configure the service settings:
   - **Root Directory**: `server`
   - **Runtime**: `Node` (or `Docker` — a production `server/Dockerfile` is included)
   - **Build Command**: `npm install && npm run build`
   - **Start Command**: `npm start`
   - **Instance Type**: `Free`
   - **Health Check Path**: `/health`
4. **Environment Variables**:
   - Render automatically injects `PORT` (which `server/src/index.ts` reads via `process.env.PORT`). No extra secrets are required.
5. **Cold-Start Wake Time Note (Free Tier)**:
   - Render's Free Web Service spins down after 15 minutes of inactivity. The **first request after idle can take 30–60 seconds** while the container wakes up.
   - Tip: Open `https://<your-render-service>.onrender.com/health` once in a browser tab (or tap **RETRY** on the in-game popup) to wake a sleeping free-tier instance before starting a match.

---

## 4. Point the Client to Your Deployed `wss://` Server URL

Once Render gives you your HTTPS URL (for example `https://neoncoil-server.onrender.com`), use `wss://` (secure WebSocket) with `/ws`:

1. **Recommended (AI Studio Secrets Panel / `.env`)**:
   Set `NEONCOIL_SERVER_URL` in the **Secrets panel in AI Studio** (see `.env.example`):
   ```env
   NEONCOIL_SERVER_URL=wss://neoncoil-server.onrender.com/ws
   ```
2. **Or directly in code**:
   Update `DEFAULT_WS_URL` in `app/src/main/java/com/example/config/ServerConfig.kt`:
   ```kotlin
   private const val DEFAULT_WS_URL = "wss://neoncoil-server.onrender.com/ws"
   ```
   `ServerConfig.healthHttpUrl` automatically derives `https://neoncoil-server.onrender.com/health` from `wsUrl`.
