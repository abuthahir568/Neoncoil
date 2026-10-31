/**
 * Shared protocol constants, skin validation, nickname sanitization, and compact quantized packet shapes.
 */

export const VALID_SKIN_IDS: ReadonlySet<string> = new Set([
  "rainbow_coil",
  "pearl_white",
  "sunny_tiger",
  "mint_clover",
  "grape_swirl",
  "bubblegum_pink",
  "sky_splash",
  "lemon_drop",
  "cotton_candy",
  "sunset_sherbet",
  "ocean_lagoon",
  "berry_cream",
  "carnival_twist",
  "party_confetti"
]);

export const SKIN_LIST: readonly string[] = Array.from(VALID_SKIN_IDS);

const BLOCKED_WORDS = [
  "fuck",
  "shit",
  "bitch",
  "asshole",
  "dick",
  "pussy",
  "cunt",
  "nigger",
  "faggot",
  "slut",
  "whore",
  "bastard"
];

export function sanitizeSkinId(raw: unknown): string {
  if (typeof raw !== "string") return "rainbow_coil";
  const trimmed = raw.trim();
  return VALID_SKIN_IDS.has(trimmed) ? trimmed : "rainbow_coil";
}

export function sanitizeNickname(raw: unknown): string {
  if (typeof raw !== "string") return "Player";
  // Keep letters, numbers, spaces, underscores, and hyphens
  const cleaned = raw.replace(/[^a-zA-Z0-9 _-]/g, "").trim().slice(0, 12);
  if (cleaned.length < 3) {
    return "Coil" + Math.floor(100 + Math.random() * 900);
  }
  const lower = cleaned.toLowerCase().replace(/[^a-z0-9]/g, "");
  for (const bad of BLOCKED_WORDS) {
    if (lower.includes(bad)) {
      return "Player" + Math.floor(10 + Math.random() * 89);
    }
  }
  return cleaned;
}

export function sanitizeRoomCode(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const code = raw.trim().toUpperCase();
  return /^[A-Z]{4}$/.test(code) ? code : null;
}

export const enum FoodKindWire {
  BOOST_PELLET = 0,
  GLOW_ORB = 1,
  APPLE = 2,
  CUPCAKE = 3,
  CROISSANT = 4,
  DEATH_DROP = 5,
  COIN = 6
}

/**
 * Quantized compact snake wire tuple/object:
 * i = snake id
 * n = name
 * k = skinId
 * a = angle quantized (milliradians integer)
 * l = desiredLength integer
 * s = score integer
 * b = boosting (0 | 1)
 * p = flat quantized [x0, y0, x1, y1, ...] integer coordinates
 */
export interface WireSnake {
  i: number;
  n: string;
  k: string;
  a: number;
  l: number;
  s: number;
  b: number;
  p: number[];
}

/**
 * Compact food wire tuple: [id, intX, intY, kindCode, colorIndex]
 */
export type WireFoodTuple = [number, number, number, number, number];

export interface WireLeaderboardItem {
  n: string;
  s: number;
  i: number;
}
