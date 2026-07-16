import { afterEach, describe, expect, it, vi } from "vitest";
import { sanitizeAppearance } from "../src/appearance";
import { verifyCertifiedChallenge } from "../src/index";

afterEach(() => vi.unstubAllGlobals());

describe("sanitizeAppearance", () => {
  it("only accepts bundled assets and clamps geometry", () => {
    expect(sanitizeAppearance({
      model: { enabled: true, id: "unknown", showHeads: true },
      cape: { enabled: true, id: "uploaded.gif" },
      cone: { enabled: true, id: "bad.png", height: 9, radius: -2, yOffset: 4, rotation: -1, spinSpeed: 999 },
      skin: { enabled: true, id: "bad.png" },
      size: { enabled: true, x: -9, y: 9, z: 2 },
      neckHider: { enabled: true, nickname: "Floyd\u0000Name that is much longer than thirty-two characters" },
    })).toEqual({
      version: 1,
      model: { enabled: true, id: "Tung Tung Sahur", showHeads: true },
      cape: { enabled: true, id: "default" },
      cone: { enabled: true, id: "default", height: 1.5, radius: 0.05, yOffset: 0.5, rotation: 0, spinSpeed: 360 },
      skin: { enabled: true, id: "default" },
      size: { enabled: true, x: -1, y: 5, z: 2 },
      neckHider: { enabled: true, nickname: "FloydName that is much longer th" },
    });
  });
});

describe("shared cosmetics authentication", () => {
  it("verifies a challenge signed by a Mojang-certified profile key", async () => {
    const serviceKeys = await crypto.subtle.generateKey(
      { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-1" },
      true,
      ["sign", "verify"],
    );
    const profileKeys = await crypto.subtle.generateKey(
      { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
      true,
      ["sign", "verify"],
    );
    const uuid = "c3431b64c1644fd282571cb724baee65";
    const expiresAt = Date.now() + 60_000;
    const profileSpki = new Uint8Array(await crypto.subtle.exportKey("spki", profileKeys.publicKey));
    const serviceSpki = new Uint8Array(await crypto.subtle.exportKey("spki", serviceKeys.publicKey));
    const certificatePayload = joinBytes(hexBytes(uuid), longBytes(expiresAt), profileSpki);
    const certificateSignature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", serviceKeys.privateKey, certificatePayload.slice().buffer as ArrayBuffer));
    const challengePayload = new TextEncoder().encode(`floyd-cosmetics-v1:challenge-id:proof-id:${uuid}`);
    const challengeSignature = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", profileKeys.privateKey, challengePayload.slice().buffer as ArrayBuffer));
    await expect(verifyCertifiedChallenge(
      { id: "challenge-id", uuid, server_id: "proof-id" },
      {
        expiresAt,
        publicKey: base64(profileSpki),
        certificateSignature: base64(certificateSignature),
        challengeSignature: base64(challengeSignature),
      },
      [base64(serviceSpki)],
    )).resolves.toBe("verified");
  });
});

function base64(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes));
}

function hexBytes(value: string): Uint8Array {
  return Uint8Array.from(value.match(/../g) ?? [], (hex) => Number.parseInt(hex, 16));
}

function longBytes(value: number): Uint8Array {
  const bytes = new Uint8Array(8);
  new DataView(bytes.buffer).setBigInt64(0, BigInt(value), false);
  return bytes;
}

function joinBytes(...parts: Uint8Array[]): Uint8Array {
  const result = new Uint8Array(parts.reduce((length, part) => length + part.length, 0));
  let offset = 0;
  for (const part of parts) {
    result.set(part, offset);
    offset += part.length;
  }
  return result;
}
