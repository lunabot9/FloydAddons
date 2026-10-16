export const APPEARANCE_VERSION = 1;
export const MODEL_IDS = ["Tung Tung Sahur", "George Floyd", "Jenny"] as const;

/**
 * Bundled model labels are owned by the client (`FloydPlayerModelSelection`): the three
 * original models, three later additions, and every vanilla mob. Keeping a copy of that list
 * here silently rewrote every other selection to `MODEL_IDS[0]` ("Tung Tung Sahur"), so a
 * published appearance could render a different model than the one the owner selected.
 * Receiving clients canonicalize the label and already fall back to their own default for
 * anything they cannot render, so the service only has to keep the stored label bounded and
 * printable instead of allowlisting it.
 */
export const MODEL_ID_MAX_LENGTH = 64;
const MODEL_ID_PATTERN = /^[A-Za-z0-9][A-Za-z0-9 '_.:()-]*$/;

const modelId = (value: unknown): string => {
  if (typeof value !== "string") return MODEL_IDS[0];
  const label = value.trim();
  if (label.length === 0 || label.length > MODEL_ID_MAX_LENGTH || !MODEL_ID_PATTERN.test(label)) {
    return MODEL_IDS[0];
  }
  return label;
};

const finite = (value: unknown, fallback: number): number =>
  typeof value === "number" && Number.isFinite(value) ? value : fallback;

const clamp = (value: unknown, min: number, max: number, fallback: number): number =>
  Math.min(max, Math.max(min, finite(value, fallback)));

const bool = (value: unknown): boolean => value === true;

const nickname = (value: unknown): string =>
  typeof value === "string" ? value.replace(/[\u0000-\u001f\u007f]/g, "").slice(0, 32) : "";

const record = (value: unknown): Record<string, unknown> =>
  value !== null && typeof value === "object" && !Array.isArray(value)
    ? value as Record<string, unknown>
    : {};

export type SharedAppearance = ReturnType<typeof sanitizeAppearance>;

export function sanitizeAppearance(input: unknown) {
  const root = record(input);
  const model = record(root.model);
  const cape = record(root.cape);
  const cone = record(root.cone);
  const skin = record(root.skin);
  const size = record(root.size);
  const neckHider = record(root.neckHider);
  const modelIdLabel = modelId(model.id);

  return {
    version: APPEARANCE_VERSION,
    model: {
      enabled: bool(model.enabled),
      id: modelIdLabel,
      showHeads: bool(model.showHeads),
    },
    cape: { enabled: bool(cape.enabled), id: "default" },
    cone: {
      enabled: bool(cone.enabled),
      id: "default",
      height: clamp(cone.height, 0.1, 1.5, 0.45),
      radius: clamp(cone.radius, 0.05, 0.8, 0.25),
      yOffset: clamp(cone.yOffset, -1.5, 0.5, -0.5),
      rotation: clamp(cone.rotation, 0, 360, 0),
      spinSpeed: clamp(cone.spinSpeed, 0, 360, 0),
    },
    skin: { enabled: bool(skin.enabled), id: "default" },
    size: {
      enabled: bool(size.enabled),
      x: clamp(size.x, -1, 5, 1),
      y: clamp(size.y, -1, 5, 1),
      z: clamp(size.z, -1, 5, 1),
    },
    neckHider: {
      enabled: bool(neckHider.enabled),
      nickname: nickname(neckHider.nickname),
    },
  };
}
