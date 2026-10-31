import crypto from "crypto";
import { WebSocket } from "ws";
import {
  FoodKindWire,
  SKIN_LIST,
  WireFoodTuple,
  WireLeaderboardItem,
  WireSnake,
  sanitizeNickname,
  sanitizeSkinId
} from "./protocol";
import { SpatialGrid } from "./spatialGrid";

const ARENA_RADIUS = 2800;
const SEGMENT_SPACING = 11;
const MIN_BOOST_LENGTH = 13;
const TARGET_FOOD_COUNT = 340;
const PUBLIC_MIN_SNAKES = 12;
const PUBLIC_MAX_PLAYERS = 20;
const PRIVATE_MAX_PLAYERS = 10;
const VIEWPORT_HALF_W = 1050;
const VIEWPORT_HALF_H = 1050;
const DISCONNECT_GRACE_MS = 10_000;

const BOT_NAMES = [
  "Sunny", "Bubbles", "Ziggy", "Mochi", "Noodle",
  "Sprinkle", "Pebble", "Mango", "Waffles", "Pickles",
  "Twisty", "Biscuit", "Skittles", "Jellybean", "Zippy",
  "Coco", "Taffy", "Poppy", "Clover", "Pippin", "Doodle", "Starry"
];

const FOOD_STATS: Record<number, { growth: number; score: number; radius: number }> = {
  [FoodKindWire.BOOST_PELLET]: { growth: 0.32, score: 3, radius: 6.5 },
  [FoodKindWire.GLOW_ORB]: { growth: 0.55, score: 8, radius: 9.0 },
  [FoodKindWire.APPLE]: { growth: 1.30, score: 22, radius: 14.5 },
  [FoodKindWire.CUPCAKE]: { growth: 1.90, score: 34, radius: 16.0 },
  [FoodKindWire.CROISSANT]: { growth: 2.35, score: 45, radius: 17.0 },
  [FoodKindWire.DEATH_DROP]: { growth: 1.45, score: 25, radius: 13.0 },
  [FoodKindWire.COIN]: { growth: 0.75, score: 15, radius: 13.5 }
};

interface Segment {
  x: number;
  y: number;
}

interface SegRef {
  snake: ServerSnake;
  segIndex: number;
  x: number;
  y: number;
  radius: number;
}

interface ServerFood {
  id: number;
  x: number;
  y: number;
  kind: number;
  colorIdx: number;
  radius: number;
}

export interface ClientSession {
  token: string;
  ws: WebSocket | null;
  snakeId: number;
  baseNickname: string;
  skinId: string;
  lastSeq: number;
  disconnectedAtMs: number | null;
  knownFoodIds: Set<number>;
  coinsEarnedPending: number;
  isHost: boolean;
}

interface ServerSnake {
  id: number;
  name: string;
  skinId: string;
  isBot: boolean;
  sessionToken: string | null;
  segments: Segment[];
  angle: number;
  targetAngle: number;
  desiredLength: number;
  score: number;
  kills: number;
  wantsBoost: boolean;
  activelyBoosting: boolean;
  boostEnergy: number;
  alive: boolean;
  invulnSec: number;
  pelletTimer: number;
  aiTimer: number;
  aiWander: number;
  personality: number; // 0 = aggressive, 1 = cautious, 2 = food hunter
}

export class ArenaRoom {
  public readonly roomId: string;
  public readonly isPrivate: boolean;
  public botsEnabled: boolean;
  public lastActivityMs: number = Date.now();

  private nextEntityId = 1;
  private nextFoodId = 1;
  private snakes: Map<number, ServerSnake> = new Map();
  private foods: Map<number, ServerFood> = new Map();
  private sessions: Map<string, ClientSession> = new Map();

  private readonly foodGrid = new SpatialGrid<ServerFood>(200);
  private readonly segGrid = new SpatialGrid<SegRef>(150);

  constructor(roomId: string, isPrivate: boolean, botsEnabled: boolean = true) {
    this.roomId = roomId;
    this.isPrivate = isPrivate;
    this.botsEnabled = botsEnabled;

    for (let i = 0; i < TARGET_FOOD_COUNT; i++) {
      this.spawnRandomFood();
    }
    this.rebalanceBots();
  }

  public get maxPlayers(): number {
    return this.isPrivate ? PRIVATE_MAX_PLAYERS : PUBLIC_MAX_PLAYERS;
  }

  public get activePlayerCount(): number {
    let count = 0;
    const now = Date.now();
    for (const s of this.sessions.values()) {
      if (s.ws !== null || (s.disconnectedAtMs !== null && now - s.disconnectedAtMs < DISCONNECT_GRACE_MS)) {
        count++;
      }
    }
    return count;
  }

  public get connectedSocketCount(): number {
    let count = 0;
    for (const s of this.sessions.values()) {
      if (s.ws !== null && s.ws.readyState === WebSocket.OPEN) count++;
    }
    return count;
  }

  public get freeSlots(): number {
    return Math.max(0, this.maxPlayers - this.activePlayerCount);
  }

  public isEmptyAndExpired(nowMs: number): boolean {
    return this.activePlayerCount === 0 && nowMs - this.lastActivityMs > 12_000;
  }

  public hasSession(token: string): boolean {
    return this.sessions.has(token);
  }

  public addOrReconnectPlayer(
    ws: WebSocket,
    rawNick: unknown,
    rawSkin: unknown,
    existingToken?: string
  ): ClientSession | null {
    this.lastActivityMs = Date.now();

    if (existingToken && this.sessions.has(existingToken)) {
      const session = this.sessions.get(existingToken)!;
      if (session.ws && session.ws !== ws) {
        try {
          session.ws.close(4001, "Replaced by reconnect");
        } catch {
          // ignore
        }
      }
      session.ws = ws;
      session.disconnectedAtMs = null;
      session.knownFoodIds.clear();

      let snake = this.snakes.get(session.snakeId);
      if (!snake || !snake.alive) {
        snake = this.spawnSnake(
          this.makeUniqueName(session.baseNickname, session.snakeId),
          session.skinId,
          false,
          session.token
        );
        session.snakeId = snake.id;
      }
      this.sendJson(ws, {
        t: "welcome",
        tok: session.token,
        id: snake.id,
        rm: this.roomId,
        prv: this.isPrivate ? 1 : 0,
        hst: session.isHost ? 1 : 0,
        bots: this.botsEnabled ? 1 : 0,
        ar: ARENA_RADIUS,
        nm: snake.name
      });
      return session;
    }

    if (this.freeSlots <= 0) {
      return null;
    }

    // Remove a bot first if needed so bots never crowd out real players
    this.removeOneBotIfNeededForJoin();

    const token = crypto.randomBytes(12).toString("hex");
    const cleanBase = sanitizeNickname(rawNick);
    const skinId = sanitizeSkinId(rawSkin);
    const isFirstHost = this.isPrivate && this.sessions.size === 0;

    const snake = this.spawnSnake(this.makeUniqueName(cleanBase, -1), skinId, false, token);
    const session: ClientSession = {
      token,
      ws,
      snakeId: snake.id,
      baseNickname: cleanBase,
      skinId,
      lastSeq: 0,
      disconnectedAtMs: null,
      knownFoodIds: new Set(),
      coinsEarnedPending: 0,
      isHost: isFirstHost
    };
    this.sessions.set(token, session);
    this.rebalanceBots();

    this.sendJson(ws, {
      t: "welcome",
      tok: session.token,
      id: snake.id,
      rm: this.roomId,
      prv: this.isPrivate ? 1 : 0,
      hst: session.isHost ? 1 : 0,
      bots: this.botsEnabled ? 1 : 0,
      ar: ARENA_RADIUS,
      nm: snake.name
    });
    return session;
  }

  public respawnSessionPlayer(token: string, rawSkin?: unknown): void {
    const session = this.sessions.get(token);
    if (!session || !session.ws) return;
    this.lastActivityMs = Date.now();
    if (rawSkin !== undefined) {
      session.skinId = sanitizeSkinId(rawSkin);
    }
    const existing = this.snakes.get(session.snakeId);
    if (existing && existing.alive) return;

    const snake = this.spawnSnake(
      this.makeUniqueName(session.baseNickname, -1),
      session.skinId,
      false,
      session.token
    );
    session.snakeId = snake.id;
    session.knownFoodIds.clear();
    this.sendJson(session.ws, {
      t: "welcome",
      tok: session.token,
      id: snake.id,
      rm: this.roomId,
      prv: this.isPrivate ? 1 : 0,
      hst: session.isHost ? 1 : 0,
      bots: this.botsEnabled ? 1 : 0,
      ar: ARENA_RADIUS,
      nm: snake.name
    });
  }

  public handleInput(token: string, angle: unknown, boost: unknown, seq: unknown): void {
    const session = this.sessions.get(token);
    if (!session) return;
    if (typeof seq === "number" && Number.isFinite(seq)) {
      session.lastSeq = Math.max(0, Math.floor(seq));
    }
    const snake = this.snakes.get(session.snakeId);
    if (!snake || !snake.alive) return;

    if (typeof angle === "number" && Number.isFinite(angle)) {
      // Normalize angle into [-PI, PI]
      let a = angle % (Math.PI * 2);
      if (a > Math.PI) a -= Math.PI * 2;
      if (a < -Math.PI) a += Math.PI * 2;
      snake.targetAngle = a;
    }
    snake.wantsBoost = boost === 1 || boost === true;
  }

  public handleSetBots(token: string, enabled: boolean): void {
    const session = this.sessions.get(token);
    if (!session || !this.isPrivate || !session.isHost) return;
    this.botsEnabled = enabled;
    if (!enabled) {
      for (const [id, s] of this.snakes.entries()) {
        if (s.isBot) this.snakes.delete(id);
      }
    } else {
      this.rebalanceBots();
    }
    this.broadcastRoomMeta();
  }

  public handleSocketDisconnect(token: string, immediateLeave: boolean): void {
    const session = this.sessions.get(token);
    if (!session) return;
    session.ws = null;
    if (immediateLeave) {
      const snake = this.snakes.get(session.snakeId);
      if (snake && snake.alive) {
        this.killSnake(snake, null);
      }
      const wasHost = session.isHost;
      this.sessions.delete(token);
      if (wasHost) this.assignNewHostIfNeeded();
      this.rebalanceBots();
    } else {
      session.disconnectedAtMs = Date.now();
    }
  }

  public stepTick(dt: number): void {
    const now = Date.now();

    // 1. Clean up expired disconnected sessions (>10s grace period)
    for (const [token, session] of this.sessions.entries()) {
      if (session.ws === null && session.disconnectedAtMs !== null) {
        if (now - session.disconnectedAtMs >= DISCONNECT_GRACE_MS) {
          const snake = this.snakes.get(session.snakeId);
          if (snake && snake.alive) {
            this.killSnake(snake, null);
          }
          const wasHost = session.isHost;
          this.sessions.delete(token);
          if (wasHost) this.assignNewHostIfNeeded();
        }
      }
    }

    if (this.activePlayerCount > 0) {
      this.lastActivityMs = now;
    }

    // 2. Rebuild spatial grids
    this.foodGrid.clear();
    for (const f of this.foods.values()) {
      this.foodGrid.insert(f.x, f.y, f);
    }

    this.segGrid.clear();
    for (const s of this.snakes.values()) {
      if (!s.alive) continue;
      const rad = this.bodyRadius(s);
      for (let i = 0; i < s.segments.length; i += 2) {
        const pt = s.segments[i];
        this.segGrid.insert(pt.x, pt.y, {
          snake: s,
          segIndex: i,
          x: pt.x,
          y: pt.y,
          radius: rad
        });
      }
    }

    // 3. Server-side AI bot steering
    for (const s of this.snakes.values()) {
      if (s.isBot && s.alive) {
        this.updateBotAi(s, dt);
      }
    }

    // 4. Authoritative movement, turn clamping & boost drain
    for (const s of this.snakes.values()) {
      if (!s.alive) continue;
      if (s.invulnSec > 0) s.invulnSec = Math.max(0, s.invulnSec - dt);

      const minEnergy = s.activelyBoosting ? 0.02 : 0.12;
      const canBoost =
        s.wantsBoost && s.boostEnergy > minEnergy && s.desiredLength > MIN_BOOST_LENGTH;
      s.activelyBoosting = canBoost;

      if (canBoost) {
        s.boostEnergy = Math.max(0, s.boostEnergy - dt * 0.28);
        s.desiredLength = Math.max(MIN_BOOST_LENGTH, s.desiredLength - dt * 1.45);
        s.pelletTimer += dt;
        if (s.pelletTimer >= 0.18) {
          s.pelletTimer = 0;
          const tail = s.segments[s.segments.length - 1];
          if (tail) {
            this.spawnFoodAt(
              tail.x + (Math.random() * 10 - 5),
              tail.y + (Math.random() * 10 - 5),
              FoodKindWire.BOOST_PELLET,
              s.id % 6
            );
          }
        }
      } else {
        s.boostEnergy = Math.min(1.0, s.boostEnergy + dt * 0.11);
      }

      const baseSpeed = 225;
      const speed = canBoost ? baseSpeed * 1.66 : baseSpeed;
      const rad = this.bodyRadius(s);
      const turnRate = 5.3 - Math.min(1.3, rad * 0.028);
      const diff = this.shortestAngleDiff(s.angle, s.targetAngle);
      const maxTurn = turnRate * dt;
      s.angle += Math.max(-maxTurn, Math.min(maxTurn, diff));

      const hx = s.segments[0].x + Math.cos(s.angle) * speed * dt;
      const hy = s.segments[0].y + Math.sin(s.angle) * speed * dt;
      s.segments[0].x = hx;
      s.segments[0].y = hy;

      for (let i = 1; i < s.segments.length; i++) {
        const prev = s.segments[i - 1];
        const curr = s.segments[i];
        const dx = prev.x - curr.x;
        const dy = prev.y - curr.y;
        const dist = Math.hypot(dx, dy);
        if (dist > SEGMENT_SPACING && dist > 0.001) {
          const pull = dist - SEGMENT_SPACING;
          curr.x += (dx / dist) * pull;
          curr.y += (dy / dist) * pull;
        }
      }

      const targetCount = Math.max(10, Math.min(240, Math.floor(s.desiredLength)));
      while (s.segments.length < targetCount) {
        const tail = s.segments[s.segments.length - 1];
        s.segments.push({ x: tail.x, y: tail.y });
      }
      while (s.segments.length > targetCount && s.segments.length > 10) {
        s.segments.pop();
      }
    }

    // 5. Eat food
    for (const s of this.snakes.values()) {
      if (!s.alive) continue;
      const hx = s.segments[0].x;
      const hy = s.segments[0].y;
      const eatR = this.bodyRadius(s) + 24;
      const eaten: ServerFood[] = [];
      this.foodGrid.queryRadius(hx, hy, eatR, (f) => {
        if (this.foods.has(f.id)) {
          const d = Math.hypot(hx - f.x, hy - f.y);
          if (d < eatR) eaten.push(f);
        }
      });
      for (const f of eaten) {
        if (!this.foods.has(f.id)) continue;
        this.foods.delete(f.id);
        const st = FOOD_STATS[f.kind] || FOOD_STATS[FoodKindWire.GLOW_ORB];
        s.desiredLength += st.growth;
        s.score += st.score;
        s.boostEnergy = Math.min(1.0, s.boostEnergy + 0.035);
        if (f.kind === FoodKindWire.COIN && s.sessionToken) {
          const sess = this.sessions.get(s.sessionToken);
          if (sess) sess.coinsEarnedPending += 1;
        }
      }
    }

    // 6. Authoritative collisions (border, head-to-head, head-to-body)
    this.checkCollisions();

    // 7. Replenish food & bots
    const missingFood = TARGET_FOOD_COUNT - this.foods.size;
    if (missingFood > 0) {
      const toAdd = Math.min(6, missingFood);
      for (let i = 0; i < toAdd; i++) this.spawnRandomFood();
    }
    this.rebalanceBots();

    // Remove dead snakes from map
    for (const [id, s] of this.snakes.entries()) {
      if (!s.alive) this.snakes.delete(id);
    }

    // 8. Send 20 Hz interest-culled, quantized snapshots to connected clients
    this.broadcastSnapshots();
  }

  private checkCollisions(): void {
    const aliveList = Array.from(this.snakes.values()).filter((s) => s.alive);
    for (const s of aliveList) {
      if (!s.alive) continue;
      const hx = s.segments[0].x;
      const hy = s.segments[0].y;
      const sRad = this.bodyRadius(s);

      // Border collision
      if (Math.hypot(hx, hy) + sRad * 0.76 >= ARENA_RADIUS) {
        this.killSnake(s, null);
        continue;
      }

      if (s.invulnSec > 0) continue;

      let killed = false;
      this.segGrid.queryRadius(hx, hy, sRad + 40, (ref) => {
        if (killed) return;
        const other = ref.snake;
        if (other.id === s.id || !other.alive || other.invulnSec > 0) return;
        const oRad = this.bodyRadius(other);

        if (ref.segIndex === 0) {
          const hd = Math.hypot(hx - other.segments[0].x, hy - other.segments[0].y);
          if (hd < (sRad + oRad) * 0.82) {
            if (s.desiredLength < other.desiredLength) {
              this.killSnake(s, other);
              killed = true;
            } else if (other.desiredLength < s.desiredLength) {
              this.killSnake(other, s);
            } else if (s.isBot && !other.isBot) {
              this.killSnake(s, other);
              killed = true;
            }
          }
        } else {
          const iStart = Math.max(1, ref.segIndex - 1);
          const iEnd = Math.min(other.segments.length - 1, ref.segIndex + 1);
          for (let k = iStart; k <= iEnd; k++) {
            const seg = other.segments[k];
            if (Math.hypot(hx - seg.x, hy - seg.y) < sRad * 0.72 + oRad * 0.78) {
              this.killSnake(s, other);
              killed = true;
              break;
            }
          }
        }
      });
    }
  }

  private killSnake(victim: ServerSnake, killer: ServerSnake | null): void {
    if (!victim.alive) return;
    victim.alive = false;

    // Turn dead snake's body into food drops visible to all players
    for (let i = 0; i < victim.segments.length; i += 2) {
      const pt = victim.segments[i];
      const isCoin = i % 8 === 0;
      this.spawnFoodAt(
        pt.x + (Math.random() * 18 - 9),
        pt.y + (Math.random() * 18 - 9),
        isCoin ? FoodKindWire.COIN : FoodKindWire.DEATH_DROP,
        (victim.id + i) % 6
      );
    }

    if (killer && killer.alive) {
      killer.kills += 1;
      killer.score += 150;
      this.broadcastKillFeed(killer.name, victim.name);
    } else {
      this.broadcastKillFeed("Border", victim.name);
    }

    if (victim.sessionToken) {
      const sess = this.sessions.get(victim.sessionToken);
      if (sess && sess.ws && sess.ws.readyState === WebSocket.OPEN) {
        const rank = this.computeRank(victim.score, victim.id);
        const coinsEarned = Math.max(
          3,
          Math.floor(victim.score / 30) + victim.kills * 6 + 5
        );
        this.sendJson(sess.ws, {
          t: "dead",
          sc: victim.score,
          ln: Math.floor(victim.desiredLength),
          kl: victim.kills,
          rk: rank,
          cn: coinsEarned
        });
      }
    }
  }

  private broadcastSnapshots(): void {
    if (this.connectedSocketCount === 0) return;

    // Build sorted leaderboard once per tick
    const sorted = Array.from(this.snakes.values())
      .filter((s) => s.alive)
      .sort((a, b) => b.score - a.score);

    const top5: WireLeaderboardItem[] = sorted.slice(0, 5).map((s) => ({
      n: s.name,
      s: s.score,
      i: s.id
    }));

    const rankById = new Map<number, number>();
    sorted.forEach((s, idx) => rankById.set(s.id, idx + 1));

    // Compact flat minimap array [id, intX, intY, ...]
    const minimapDots: number[] = [];
    for (const s of sorted) {
      minimapDots.push(
        s.id,
        Math.round(s.segments[0].x),
        Math.round(s.segments[0].y)
      );
    }

    for (const session of this.sessions.values()) {
      const ws = session.ws;
      if (!ws || ws.readyState !== WebSocket.OPEN) continue;

      const mySnake = this.snakes.get(session.snakeId);
      const camX = mySnake && mySnake.alive ? mySnake.segments[0].x : 0;
      const camY = mySnake && mySnake.alive ? mySnake.segments[0].y : 0;
      const minX = camX - VIEWPORT_HALF_W;
      const maxX = camX + VIEWPORT_HALF_W;
      const minY = camY - VIEWPORT_HALF_H;
      const maxY = camY + VIEWPORT_HALF_H;

      // Interest-culled snakes
      const visibleSnakes: WireSnake[] = [];
      for (const s of sorted) {
        if (!this.isSnakeNearViewport(s, minX, minY, maxX, maxY) && s.id !== session.snakeId) {
          continue;
        }
        const pts: number[] = [];
        // Send every segment quantized to integer so curves stay silky-smooth
        for (let i = 0; i < s.segments.length; i++) {
          pts.push(Math.round(s.segments[i].x), Math.round(s.segments[i].y));
        }
        visibleSnakes.push({
          i: s.id,
          n: s.name,
          k: s.skinId,
          a: Math.round(s.angle * 1000),
          l: Math.round(s.desiredLength),
          s: s.score,
          b: s.activelyBoosting ? 1 : 0,
          p: pts
        });
      }

      // Interest-culled food deltas (fa = added tuples, fr = removed IDs)
      const currentInView = new Set<number>();
      const foodAdded: WireFoodTuple[] = [];
      this.foodGrid.queryBox(minX, minY, maxX, maxY, (f) => {
        if (!this.foods.has(f.id)) return;
        currentInView.add(f.id);
        if (!session.knownFoodIds.has(f.id)) {
          session.knownFoodIds.add(f.id);
          foodAdded.push([
            f.id,
            Math.round(f.x),
            Math.round(f.y),
            f.kind,
            f.colorIdx
          ]);
        }
      });

      const foodRemoved: number[] = [];
      for (const knownId of session.knownFoodIds) {
        if (!currentInView.has(knownId)) {
          session.knownFoodIds.delete(knownId);
          foodRemoved.push(knownId);
        }
      }

      const pendingCoins = session.coinsEarnedPending;
      session.coinsEarnedPending = 0;

      const myRank = mySnake ? rankById.get(mySnake.id) ?? sorted.length + 1 : sorted.length + 1;

      this.sendJson(ws, {
        t: "s",
        q: session.lastSeq,
        e: mySnake ? Math.round(mySnake.boostEnergy * 100) : 100,
        sc: mySnake ? mySnake.score : 0,
        kl: mySnake ? mySnake.kills : 0,
        rk: myRank,
        ac: sorted.length,
        cn: pendingCoins,
        lb: top5,
        sn: visibleSnakes,
        fa: foodAdded.length > 0 ? foodAdded : undefined,
        fr: foodRemoved.length > 0 ? foodRemoved : undefined,
        mm: minimapDots
      });
    }
  }

  private isSnakeNearViewport(
    s: ServerSnake,
    minX: number,
    minY: number,
    maxX: number,
    maxY: number
  ): boolean {
    const step = Math.max(1, Math.floor(s.segments.length / 6));
    for (let i = 0; i < s.segments.length; i += step) {
      const pt = s.segments[i];
      if (pt.x >= minX && pt.x <= maxX && pt.y >= minY && pt.y <= maxY) {
        return true;
      }
    }
    const tail = s.segments[s.segments.length - 1];
    return tail.x >= minX && tail.x <= maxX && tail.y >= minY && tail.y <= maxY;
  }

  private rebalanceBots(): void {
    if (!this.botsEnabled) return;
    const minTotal = this.isPrivate ? 8 : PUBLIC_MIN_SNAKES;
    const realSnakes = Array.from(this.snakes.values()).filter((s) => !s.isBot && s.alive).length;
    const botSnakes = Array.from(this.snakes.values()).filter((s) => s.isBot && s.alive);
    const desiredBots = Math.max(0, minTotal - realSnakes);

    if (botSnakes.length < desiredBots) {
      const toSpawn = desiredBots - botSnakes.length;
      for (let i = 0; i < toSpawn; i++) {
        const rawName = BOT_NAMES[(this.nextEntityId + i) % BOT_NAMES.length];
        const skinId = SKIN_LIST[(this.nextEntityId + i) % SKIN_LIST.length];
        this.spawnSnake(this.makeUniqueName(rawName, -1), skinId, true, null);
      }
    } else if (botSnakes.length > desiredBots) {
      const excess = botSnakes.length - desiredBots;
      for (let i = 0; i < excess; i++) {
        this.snakes.delete(botSnakes[i].id);
      }
    }
  }

  private removeOneBotIfNeededForJoin(): void {
    for (const [id, s] of this.snakes.entries()) {
      if (s.isBot) {
        this.snakes.delete(id);
        return;
      }
    }
  }

  private assignNewHostIfNeeded(): void {
    if (!this.isPrivate) return;
    for (const s of this.sessions.values()) {
      if (s.isHost) return;
    }
    const next = this.sessions.values().next().value as ClientSession | undefined;
    if (next) {
      next.isHost = true;
      this.broadcastRoomMeta();
    }
  }

  private broadcastRoomMeta(): void {
    for (const s of this.sessions.values()) {
      if (s.ws && s.ws.readyState === WebSocket.OPEN) {
        this.sendJson(s.ws, {
          t: "meta",
          rm: this.roomId,
          hst: s.isHost ? 1 : 0,
          bots: this.botsEnabled ? 1 : 0
        });
      }
    }
  }

  private broadcastKillFeed(killer: string, victim: string): void {
    const msg = JSON.stringify({ t: "k", kr: killer, vc: victim });
    for (const s of this.sessions.values()) {
      if (s.ws && s.ws.readyState === WebSocket.OPEN) {
        try {
          s.ws.send(msg);
        } catch {
          // ignore
        }
      }
    }
  }

  private updateBotAi(bot: ServerSnake, dt: number): void {
    const hx = bot.segments[0].x;
    const hy = bot.segments[0].y;
    const distCenter = Math.hypot(hx, hy);

    if (distCenter > ARENA_RADIUS - 260) {
      bot.targetAngle = Math.atan2(-hy, -hx);
      bot.wantsBoost = distCenter > ARENA_RADIUS - 130 && bot.boostEnergy > 0.3;
      return;
    }

    let avoidX = 0;
    let avoidY = 0;
    let hazard = false;
    const lookR = 175 + this.bodyRadius(bot);

    this.segGrid.queryRadius(hx, hy, lookR, (ref) => {
      if (ref.snake.id !== bot.id && ref.snake.alive) {
        const dx = hx - ref.x;
        const dy = hy - ref.y;
        const d = Math.hypot(dx, dy);
        if (d > 1 && d < lookR) {
          const w = (lookR - d) / lookR;
          avoidX += (dx / d) * w * 2.2;
          avoidY += (dy / d) * w * 2.2;
          hazard = true;
        }
      }
    });

    if (hazard) {
      bot.targetAngle = Math.atan2(avoidY, avoidX);
      bot.wantsBoost = bot.personality === 1 && bot.boostEnergy > 0.4;
      return;
    }

    bot.aiTimer -= dt;
    if (bot.aiTimer > 0) return;
    bot.aiTimer = 0.12 + Math.random() * 0.12;

    let bestFood: ServerFood | null = null;
    let bestVal = -1;
    this.foodGrid.queryRadius(hx, hy, 380, (f) => {
      if (!this.foods.has(f.id)) return;
      const d = Math.max(15, Math.hypot(f.x - hx, f.y - hy));
      const val = ((FOOD_STATS[f.kind]?.score ?? 8) * 10) / d;
      if (val > bestVal) {
        bestVal = val;
        bestFood = f;
      }
    });

    if (bestFood) {
      const target = bestFood as ServerFood;
      bot.targetAngle = Math.atan2(target.y - hy, target.x - hx);
      bot.wantsBoost = target.kind === FoodKindWire.DEATH_DROP && bot.boostEnergy > 0.5;
    } else {
      bot.aiWander += (Math.random() - 0.5) * 0.7;
      bot.targetAngle = bot.angle + Math.sin(bot.aiWander) * 0.4;
      bot.wantsBoost = false;
    }
  }

  private spawnSnake(
    name: string,
    skinId: string,
    isBot: boolean,
    sessionToken: string | null
  ): ServerSnake {
    const id = this.nextEntityId++;
    let sx = 0;
    let sy = 0;
    for (let tries = 0; tries < 12; tries++) {
      const ang = Math.random() * Math.PI * 2;
      const dist = (0.12 + Math.random() * 0.68) * (ARENA_RADIUS - 260);
      sx = Math.cos(ang) * dist;
      sy = Math.sin(ang) * dist;
      let safe = true;
      this.segGrid.queryRadius(sx, sy, 260, () => {
        safe = false;
      });
      if (safe) break;
    }

    const angle = Math.random() * Math.PI * 2 - Math.PI;
    const startLen = isBot ? 18 + Math.floor(Math.random() * 16) : 20;
    const segments: Segment[] = [];
    for (let i = 0; i < startLen; i++) {
      segments.push({
        x: sx - Math.cos(angle) * i * SEGMENT_SPACING,
        y: sy - Math.sin(angle) * i * SEGMENT_SPACING
      });
    }

    const snake: ServerSnake = {
      id,
      name,
      skinId,
      isBot,
      sessionToken,
      segments,
      angle,
      targetAngle: angle,
      desiredLength: startLen,
      score: isBot ? startLen * 8 : 0,
      kills: 0,
      wantsBoost: false,
      activelyBoosting: false,
      boostEnergy: 1.0,
      alive: true,
      invulnSec: 2.0,
      pelletTimer: 0,
      aiTimer: 0,
      aiWander: Math.random() * 6.28,
      personality: id % 3
    };
    this.snakes.set(id, snake);
    return snake;
  }

  private spawnRandomFood(): void {
    const ang = Math.random() * Math.PI * 2;
    const r = Math.sqrt(Math.random()) * (ARENA_RADIUS - 95);
    const roll = Math.random();
    const kind =
      roll < 0.60
        ? FoodKindWire.GLOW_ORB
        : roll < 0.76
          ? FoodKindWire.APPLE
          : roll < 0.88
            ? FoodKindWire.CUPCAKE
            : roll < 0.96
              ? FoodKindWire.CROISSANT
              : FoodKindWire.COIN;
    this.spawnFoodAt(Math.cos(ang) * r, Math.sin(ang) * r, kind, Math.floor(Math.random() * 6));
  }

  private spawnFoodAt(x: number, y: number, kind: number, colorIdx: number): void {
    if (this.foods.size >= 750) return;
    const id = this.nextFoodId++;
    const radius = FOOD_STATS[kind]?.radius ?? 9;
    this.foods.set(id, { id, x, y, kind, colorIdx, radius });
  }

  private makeUniqueName(base: string, ignoreSnakeId: number): string {
    const taken = new Set<string>();
    for (const s of this.snakes.values()) {
      if (s.alive && s.id !== ignoreSnakeId) {
        taken.add(s.name.toLowerCase());
      }
    }
    if (!taken.has(base.toLowerCase())) return base;
    let suffix = 2;
    while (suffix < 99) {
      const candidate = `${base.slice(0, 9)} ${suffix}`;
      if (!taken.has(candidate.toLowerCase())) return candidate;
      suffix++;
    }
    return base;
  }

  private computeRank(score: number, snakeId: number): number {
    let better = 0;
    for (const s of this.snakes.values()) {
      if (s.alive && s.id !== snakeId && s.score > score) better++;
    }
    return better + 1;
  }

  private bodyRadius(s: ServerSnake): number {
    const bonus = Math.min(15, Math.sqrt(Math.max(10, s.desiredLength)) * 1.15);
    return 14.5 + bonus;
  }

  private shortestAngleDiff(from: number, to: number): number {
    let diff = (to - from) % (Math.PI * 2);
    if (diff > Math.PI) diff -= Math.PI * 2;
    if (diff < -Math.PI) diff += Math.PI * 2;
    return diff;
  }

  private sendJson(ws: WebSocket, payload: unknown): void {
    if (ws.readyState !== WebSocket.OPEN) return;
    try {
      ws.send(JSON.stringify(payload));
    } catch {
      // ignore transient socket error
    }
  }
}
