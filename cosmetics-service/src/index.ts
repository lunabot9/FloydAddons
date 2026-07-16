import { sanitizeAppearance } from "./appearance";

interface Env {
  DB: D1Database;
}

interface ChallengeRow {
  id: string;
  uuid: string;
  username: string;
  server_id: string;
  expires_at: number;
}

interface SessionRow {
  uuid: string;
  expires_at: number;
}

const CHALLENGE_TTL_MS = 5 * 60 * 1000;
const SESSION_TTL_MS = 30 * 24 * 60 * 60 * 1000;
const MAX_UUIDS_PER_LOOKUP = 100;
const UUID_PATTERN = /^[0-9a-f]{32}$/;
const USERNAME_PATTERN = /^[A-Za-z0-9_]{1,16}$/;
// Mojang's public player-certificate trust roots from api.minecraftservices.com/publickeys.
// Cloudflare egress is blocked by Mojang, so both active rotation keys are pinned here.
const TRUSTED_PLAYER_CERTIFICATE_KEYS = [
  "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAylB4B6m5lz7jwrcFz6Fd/fnfUhcvlxsTSn5kIK/2aGG1C3kMy4VjhwlxF6BFUSnfxhNswPjh3ZitkBxEAFY25uzkJFRwHwVA9mdwjashXILtR6OqdLXXFVyUPIURLOSWqGNBtb08EN5fMnG8iFLgEJIBMxs9BvF3s3/FhuHyPKiVTZmXY0WY4ZyYqvoKR+XjaTRPPvBsDa4WI2u1zxXMeHlodT3lnCzVvyOYBLXL6CJgByuOxccJ8hnXfF9yY4F0aeL080Jz/3+EBNG8RO4ByhtBf4Ny8NQ6stWsjfeUIvH7bU/4zCYcYOq4WrInXHqS8qruDmIl7P5XXGcabuzQstPf/h2CRAUpP/PlHXcMlvewjmGU6MfDK+lifScNYwjPxRo4nKTGFZf/0aqHCh/EAsQyLKrOIYRE0lDG3bzBh8ogIMLAugsAfBb6M3mqCqKaTMAf/VAjh5FFJnjS+7bE+bZEV0qwax1CEoPPJL1fIQjOS8zj086gjpGRCtSy9+bTPTfTR/SJ+VUB5G2IeCItkNHpJX2ygojFZ9n5Fnj7R9ZnOM+L8nyIjPu3aePvtcrXlyLhH/hvOfIOjPxOlqW+O5QwSFP4OEcyLAUgDdUgyW36Z5mB285uKW/ighzZsOTevVUG2QwDItObIV6i8RCxFbN2oDHyPaO5j1tTaBNyVt8CAwEAAQ==",
  "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt4t9NPuu7cktclnaH7eZj0omkLcJHeLz5MKsyJEntHZ0INtuBjSSul3Pp3pBeJN8k3ADdcdBLUN90bcAi7WsQqTx3Ft363q3W7TbM8j2iTEdp/0uVspoRt/DP1tkaWFs/w2WwUv9jbVoBUzfUc4pSTIxRwdjmqjZQfvjwKNDbOx3IhP2H0WXodbISejPi1wBZqNW4m1rnZAXp/EpUguxA8mobCa4vUCBkyFDyXdl69/wUSJHyCPmgcMJ364OlAhIqtwVPShBZObvrK/f0BYk6ShJD3N7TFDatSYsIIdcTKRknaIm91s+EsMrdB9U4Yw+ZJ/pyCB4S3vk8zfDCnb0DWIxYH3/EMzaxl77djmTmMzi/JDITup5z3jfWtRZmrAhU2/+W5IO5hEpo3/bCS9PXIY5xb41Lmp2ZO8dXKtyD66Chchy0W129n8vPl2GIruOdrxsjZAHnneyAb9jm0uaGaphwnEnuecX/qgHY6ZMtayvLLsPst8PO6R1vufMy8WqjK+j7LnC1krL7CPDg0NEhyQTmw5l+NCNjSlvB1juM9V4PARg0bYCOkGXm7ydRCjSSH8CJXZpwnd5cBB5WKAX3KPzutRgMi/LFwNSMZzFuUyXaYOZPpD259yqph1LmGqegEdDriACVU+dVEONFMm8eIuBofe7ljmsAFKW9BINwK0CAwEAAQ==",
] as const;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    try {
      return await route(request, env);
    } catch (error) {
      console.error(error);
      return json({ error: "internal_error" }, 500);
    }
  },
} satisfies ExportedHandler<Env>;

async function route(request: Request, env: Env): Promise<Response> {
  const url = new URL(request.url);
  if (request.method === "GET" && url.pathname === "/health") {
    return json({ ok: true, service: "floyd-cosmetics", protocolVersion: 1 });
  }
  if (request.method === "GET" && url.pathname === "/v1/appearances") {
    return lookupAppearances(url, env);
  }
  if (request.method === "POST" && url.pathname === "/v1/auth/challenge") {
    return createChallenge(request, env);
  }
  if (request.method === "POST" && url.pathname === "/v1/auth/complete") {
    return completeChallenge(request, env);
  }
  if (request.method === "PUT" && url.pathname === "/v1/appearances/me") {
    return publishAppearance(request, env);
  }
  return json({ error: "not_found" }, 404);
}

async function lookupAppearances(url: URL, env: Env): Promise<Response> {
  const uuids = [...new Set((url.searchParams.get("uuids") ?? "")
    .split(",")
    .map(normalizeUuid)
    .filter((uuid): uuid is string => uuid !== null))]
    .slice(0, MAX_UUIDS_PER_LOOKUP);
  if (uuids.length === 0) return json({ appearances: {} });

  const placeholders = uuids.map(() => "?").join(",");
  const result = await env.DB.prepare(
    `SELECT uuid, appearance_json FROM appearances WHERE uuid IN (${placeholders})`
  ).bind(...uuids).all<{ uuid: string; appearance_json: string }>();
  const appearances: Record<string, unknown> = {};
  for (const row of result.results) {
    try {
      appearances[dashedUuid(row.uuid)] = sanitizeAppearance(JSON.parse(row.appearance_json));
    } catch {
      // Ignore a damaged row instead of failing the whole batched lookup.
    }
  }
  return json({ appearances }, 200, { "Cache-Control": "public, max-age=5" });
}

async function createChallenge(request: Request, env: Env): Promise<Response> {
  const body = await readJson(request);
  const uuid = normalizeUuid(body.uuid);
  const username = typeof body.username === "string" ? body.username : "";
  if (uuid === null || !USERNAME_PATTERN.test(username)) return json({ error: "invalid_identity" }, 400);

  const now = Date.now();
  const id = crypto.randomUUID();
  const serverId = randomHex(20);
  await env.DB.batch([
    env.DB.prepare("DELETE FROM auth_challenges WHERE expires_at <= ? OR uuid = ?").bind(now, uuid),
    env.DB.prepare(
      "INSERT INTO auth_challenges (id, uuid, username, server_id, expires_at) VALUES (?, ?, ?, ?, ?)"
    ).bind(id, uuid, username, serverId, now + CHALLENGE_TTL_MS),
  ]);
  return json({ challengeId: id, serverId, expiresAt: now + CHALLENGE_TTL_MS });
}

async function completeChallenge(request: Request, env: Env): Promise<Response> {
  const body = await readJson(request);
  const challengeId = typeof body.challengeId === "string" ? body.challengeId : "";
  if (challengeId.length === 0 || challengeId.length > 64) return json({ error: "invalid_challenge" }, 400);
  const challenge = await env.DB.prepare(
    "SELECT id, uuid, username, server_id, expires_at FROM auth_challenges WHERE id = ?"
  ).bind(challengeId).first<ChallengeRow>();
  if (challenge === null || challenge.expires_at <= Date.now()) {
    return json({ error: "challenge_expired" }, 401);
  }

  const verification = await verifyCertifiedChallenge(challenge, body);
  if (verification === "invalid") {
    return json({ error: "identity_not_verified" }, 401);
  }
  if (verification === "unavailable") {
    return json({ error: "identity_service_unavailable" }, 503);
  }

  const token = randomBase64Url(32);
  const tokenHash = await sha256(token);
  const now = Date.now();
  const expiresAt = now + SESSION_TTL_MS;
  await env.DB.batch([
    env.DB.prepare("DELETE FROM auth_challenges WHERE id = ?").bind(challenge.id),
    env.DB.prepare("DELETE FROM sessions WHERE expires_at <= ? OR uuid = ?").bind(now, challenge.uuid),
    env.DB.prepare(
      "INSERT INTO sessions (token_hash, uuid, expires_at, created_at) VALUES (?, ?, ?, ?)"
    ).bind(tokenHash, challenge.uuid, expiresAt, now),
  ]);
  return json({ token, expiresAt, uuid: dashedUuid(challenge.uuid) });
}

export async function verifyCertifiedChallenge(
  challenge: Pick<ChallengeRow, "id" | "uuid" | "server_id">,
  body: Record<string, unknown>,
  trustedKeys: readonly string[] = TRUSTED_PLAYER_CERTIFICATE_KEYS,
): Promise<"verified" | "invalid" | "unavailable"> {
  const expiresAt = typeof body.expiresAt === "number" ? body.expiresAt : 0;
  const publicKey = decodeBase64(body.publicKey);
  const certificateSignature = decodeBase64(body.certificateSignature);
  const challengeSignature = decodeBase64(body.challengeSignature);
  if (expiresAt <= Date.now() || publicKey === null || certificateSignature === null || challengeSignature === null) {
    return "invalid";
  }

  const certificatePayload = concatBytes(uuidBytes(challenge.uuid), longBytes(expiresAt), publicKey);
  const trusted = await anyValidSignature(
    [...trustedKeys],
    "SHA-1",
    certificatePayload,
    certificateSignature,
  );
  if (!trusted) return "invalid";

  const challengePayload = new TextEncoder().encode(
    `floyd-cosmetics-v1:${challenge.id}:${challenge.server_id}:${challenge.uuid}`
  );
  return await verifyRsaSignature(publicKey, "SHA-256", challengePayload, challengeSignature)
    ? "verified"
    : "invalid";
}

async function anyValidSignature(keys: string[], hash: "SHA-1" | "SHA-256", payload: Uint8Array, signature: Uint8Array): Promise<boolean> {
  for (const key of keys) {
    const decoded = decodeBase64(key);
    if (decoded !== null && await verifyRsaSignature(decoded, hash, payload, signature)) return true;
  }
  return false;
}

async function verifyRsaSignature(
  spki: Uint8Array,
  hash: "SHA-1" | "SHA-256",
  payload: Uint8Array,
  signature: Uint8Array,
): Promise<boolean> {
  try {
    const key = await crypto.subtle.importKey(
      "spki",
      arrayBuffer(spki),
      { name: "RSASSA-PKCS1-v1_5", hash },
      false,
      ["verify"],
    );
    return await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, arrayBuffer(signature), arrayBuffer(payload));
  } catch {
    return false;
  }
}

function arrayBuffer(bytes: Uint8Array): ArrayBuffer {
  return bytes.slice().buffer as ArrayBuffer;
}

function decodeBase64(value: unknown): Uint8Array | null {
  if (typeof value !== "string" || value.length === 0 || value.length > 8192) return null;
  try {
    return Uint8Array.from(atob(value), (character) => character.charCodeAt(0));
  } catch {
    return null;
  }
}

function uuidBytes(uuid: string): Uint8Array {
  return Uint8Array.from(uuid.match(/../g) ?? [], (hex) => Number.parseInt(hex, 16));
}

function longBytes(value: number): Uint8Array {
  const bytes = new Uint8Array(8);
  new DataView(bytes.buffer).setBigInt64(0, BigInt(value), false);
  return bytes;
}

function concatBytes(...parts: Uint8Array[]): Uint8Array {
  const output = new Uint8Array(parts.reduce((length, part) => length + part.length, 0));
  let offset = 0;
  for (const part of parts) {
    output.set(part, offset);
    offset += part.length;
  }
  return output;
}

async function publishAppearance(request: Request, env: Env): Promise<Response> {
  const session = await authenticate(request, env);
  if (session === null) return json({ error: "unauthorized" }, 401);
  const appearance = sanitizeAppearance(await readJson(request));
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO appearances (uuid, appearance_json, updated_at) VALUES (?, ?, ?)
     ON CONFLICT(uuid) DO UPDATE SET appearance_json = excluded.appearance_json, updated_at = excluded.updated_at`
  ).bind(session.uuid, JSON.stringify(appearance), now).run();
  return json({ ok: true, appearance, updatedAt: now });
}

async function authenticate(request: Request, env: Env): Promise<SessionRow | null> {
  const header = request.headers.get("Authorization") ?? "";
  if (!header.startsWith("Bearer ")) return null;
  const token = header.slice(7);
  if (token.length < 32 || token.length > 128) return null;
  const row = await env.DB.prepare(
    "SELECT uuid, expires_at FROM sessions WHERE token_hash = ?"
  ).bind(await sha256(token)).first<SessionRow>();
  return row !== null && row.expires_at > Date.now() ? row : null;
}

async function readJson(request: Request): Promise<Record<string, unknown>> {
  const contentLength = Number(request.headers.get("Content-Length") ?? 0);
  if (contentLength > 16_384) throw new Error("request_too_large");
  const value: unknown = await request.json();
  return value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};
}

function normalizeUuid(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const normalized = value.toLowerCase().replaceAll("-", "");
  return UUID_PATTERN.test(normalized) ? normalized : null;
}

function dashedUuid(uuid: string): string {
  return `${uuid.slice(0, 8)}-${uuid.slice(8, 12)}-${uuid.slice(12, 16)}-${uuid.slice(16, 20)}-${uuid.slice(20)}`;
}

function randomHex(bytes: number): string {
  return [...crypto.getRandomValues(new Uint8Array(bytes))]
    .map((value) => value.toString(16).padStart(2, "0"))
    .join("");
}

function randomBase64Url(bytes: number): string {
  const binary = String.fromCharCode(...crypto.getRandomValues(new Uint8Array(bytes)));
  return btoa(binary).replaceAll("+", "-").replaceAll("/", "_").replaceAll("=", "");
}

async function sha256(value: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(digest)].map((byte) => byte.toString(16).padStart(2, "0")).join("");
}

function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return Response.json(body, {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", ...headers },
  });
}
