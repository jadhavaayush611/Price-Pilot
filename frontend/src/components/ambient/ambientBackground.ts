/**
 * Ambient Dot-Grid and Comet Cursor Trail Engine
 * Refined visual model:
 * - 28px static dot grid with calibrated 4% (0.04) inactive baseline opacity against #030303
 * - 95px concentrated radial cursor glow
 * - Directional dot energy bias (concentrated behind motion vector)
 * - Tapered comet profile (Head -> Body -> Tail)
 * - True dynamic core color cycling driven by cursor distance traveled
 * - Persistent color phase continuity (freezes on pause, resumes on next move)
 * - Layered rendering with white luminous edges, dynamic colored core, and outer glow
 * - Dynamic velocity scaling and exponential temporal decay
 */

export interface Point {
  x: number;
  y: number;
}

export interface RGBColor {
  r: number;
  g: number;
  b: number;
}

export interface TrailPoint extends Point {
  timestamp: number;
  vx: number;
  vy: number;
  speed: number;
  color: RGBColor;
  colorPhase: number;
}

export interface EnergizedDot {
  x: number;
  y: number;
  energy: number;
  lastUpdated: number;
}

export interface AmbientEngineConfig {
  gridSpacing: number;
  dotRadius: number;
  baseDotOpacity: number;
  interactionRadius: number;
  trailMaxDurationMs: number;
  maxTrailPoints: number;
  decayRatePerSec: number;
  energyThreshold: number;
  colorShiftRate: number;
  maxDpr: number;
}

export const DEFAULT_CONFIG: AmbientEngineConfig = {
  gridSpacing: 28,
  dotRadius: 0.75,
  baseDotOpacity: 0.04, // 4% inactive baseline opacity (subtle, calibrated against #030303)
  interactionRadius: 95,
  trailMaxDurationMs: 650,
  maxTrailPoints: 26,
  decayRatePerSec: 3.2,
  energyThreshold: 0.005,
  colorShiftRate: 0.0008, // Full palette cycle every ~1250px of movement
  maxDpr: 2,
};

/**
 * Determines whether interactive canvas animation should be activated
 * based on accessibility preferences and pointer capabilities.
 * If reduced motion is requested or device is touch-only, returns false
 * so that only the lightweight static CSS dot grid is presented.
 */
export function shouldEnableInteractiveAmbient(
  prefersReducedMotion: boolean,
  hasFinePointer: boolean
): boolean {
  return !prefersReducedMotion && hasFinePointer;
}

/**
 * Muted color progression along the comet trail palette.
 * Atmospheric, restrained tones.
 */
export const TRAIL_PALETTE: RGBColor[] = [
  { r: 79, g: 209, b: 197 }, // 0: Muted Teal (#4FD1C5)
  { r: 96, g: 165, b: 250 }, // 1: Soft Sky Blue (#60A5FA)
  { r: 139, g: 124, b: 246 }, // 2: Soft Violet (#8B7CF6)
  { r: 192, g: 132, b: 252 }, // 3: Muted Magenta (#C084FC)
];

/**
 * Smoothstep function for non-linear soft interpolation.
 */
export function smoothstep(min: number, max: number, value: number): number {
  const x = Math.max(0, Math.min(1, (value - min) / (max - min)));
  return x * x * (3 - 2 * x);
}

/**
 * Interpolates color cyclically along the palette based on phase in [0, 1).
 * Smoothly transitions: Teal -> Blue -> Violet -> Magenta -> Teal ...
 */
export function getPaletteColorFromPhase(phase: number): RGBColor {
  // Normalize and wrap phase strictly to [0, 1)
  const wrappedPhase = ((phase % 1) + 1) % 1;
  const count = TRAIL_PALETTE.length;
  const position = wrappedPhase * count;
  const index = Math.floor(position);
  const frac = position - index;

  // Smooth easing for organic transition between color stops
  const easedFrac = frac * frac * (3 - 2 * frac);

  const c1 = TRAIL_PALETTE[index % count];
  const c2 = TRAIL_PALETTE[(index + 1) % count];

  return {
    r: Math.round(c1.r + (c2.r - c1.r) * easedFrac),
    g: Math.round(c1.g + (c2.g - c1.g) * easedFrac),
    b: Math.round(c1.b + (c2.b - c1.b) * easedFrac),
  };
}

/**
 * Legacy helper for ratio-based palette indexing, now backed by continuous cyclic interpolation.
 */
export function getInterpolatedPaletteColor(ratio: number): RGBColor {
  return getPaletteColorFromPhase(ratio);
}

/**
 * Calculates radial falloff influence between a dot and the cursor.
 * Returns a value between 0 and 1 with a concentrated core and fast non-linear perimeter falloff.
 */
export function calculateRadialInfluence(
  dotX: number,
  dotY: number,
  cursorX: number,
  cursorY: number,
  radius: number
): number {
  const dx = dotX - cursorX;
  const dy = dotY - cursorY;
  const dist = Math.hypot(dx, dy);
  if (dist >= radius) return 0;
  const normalized = dist / radius;
  const falloff = 1 - normalized;
  return Math.pow(falloff, 2.2);
}

/**
 * Calculates velocity-aware directional bias for dot illumination.
 * When cursor moves in direction (vx, vy), dots BEHIND the motion vector receive stronger illumination.
 * Stationary cursor (speed near 0) returns 1.0 (pure radial behavior).
 */
export function calculateDirectionalBias(
  dotX: number,
  dotY: number,
  cursorX: number,
  cursorY: number,
  vx: number,
  vy: number,
  speed: number
): number {
  if (speed <= 0.02) {
    return 1.0;
  }

  const dx = dotX - cursorX;
  const dy = dotY - cursorY;
  const dist = Math.hypot(dx, dy);
  if (dist < 0.1) {
    return 1.0;
  }

  // Normalized motion direction
  const ux = vx / speed;
  const uy = vy / speed;

  // Normalized vector from cursor to dot
  const ndx = dx / dist;
  const ndy = dy / dist;

  // Cosine of angle between motion vector and cursor->dot vector
  // cos(theta) > 0 means dot is ahead of motion (cursor moving toward dot)
  // cos(theta) < 0 means dot is behind motion (cursor moving away from dot)
  const cosTheta = ndx * ux + ndy * uy;

  // Normalized speed factor clamped to [0, 1]
  const speedFactor = Math.min(1.0, speed / 1.0);

  // Directional factor: behind (cosTheta < 0) gets up to +50% boost, ahead (cosTheta > 0) gets up to -40% reduction
  const dirFactor = 1.0 - 0.5 * cosTheta * speedFactor;

  // Bounded clamp between 0.4 and 1.6
  return Math.max(0.4, Math.min(1.6, dirFactor));
}

/**
 * Exponential decay of energy over a time delta in milliseconds.
 */
export function decayEnergy(currentEnergy: number, deltaMs: number, decayRatePerSec: number): number {
  if (currentEnergy <= 0 || deltaMs <= 0) return currentEnergy;
  const decayFactor = Math.exp(-decayRatePerSec * (deltaMs / 1000));
  return currentEnergy * decayFactor;
}

/**
 * Computes midpoint spline control points for fluid curve rendering.
 */
export function computeSmoothPathSegments(points: Point[]): { start: Point; control: Point; end: Point }[] {
  if (points.length < 2) return [];
  const segments: { start: Point; control: Point; end: Point }[] = [];

  for (let i = 0; i < points.length - 1; i++) {
    const pCurrent = points[i];
    const pNext = points[i + 1];
    const pPrev = i > 0 ? points[i - 1] : pCurrent;

    const start = i === 0 ? pCurrent : { x: (pPrev.x + pCurrent.x) / 2, y: (pPrev.y + pCurrent.y) / 2 };
    const end = { x: (pCurrent.x + pNext.x) / 2, y: (pCurrent.y + pNext.y) / 2 };

    segments.push({
      start,
      control: pCurrent,
      end,
    });
  }

  return segments;
}

/**
 * Distance from a point to a line segment.
 */
export function distanceToSegment(px: number, py: number, x1: number, y1: number, x2: number, y2: number): number {
  const l2 = (x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1);
  if (l2 === 0) return Math.hypot(px - x1, py - y1);
  let t = ((px - x1) * (x2 - x1) + (py - y1) * (y2 - y1)) / l2;
  t = Math.max(0, Math.min(1, t));
  return Math.hypot(px - (x1 + t * (x2 - x1)), py - (y1 + t * (y2 - y1)));
}

/**
 * Main state container for ambient animation.
 */
export class AmbientAnimationState {
  config: AmbientEngineConfig;
  trailPoints: TrailPoint[] = [];
  activeDots: Map<string, EnergizedDot> = new Map();
  lastCursorX: number | null = null;
  lastCursorY: number | null = null;
  lastCursorTime: number = 0;
  lastFrameTime: number = 0;
  isPointerActive: boolean = false;
  currentVx: number = 0;
  currentVy: number = 0;
  currentSpeed: number = 0;
  colorPhase: number = 0;

  constructor(config: Partial<AmbientEngineConfig> = {}) {
    this.config = { ...DEFAULT_CONFIG, ...config };
  }

  /**
   * Records pointer movement, advances persistent color phase with distance traveled,
   * and injects directional energy into nearby grid points.
   */
  onPointerMove(x: number, y: number, now: number = performance.now()): void {
    this.isPointerActive = true;
    let vx = 0;
    let vy = 0;
    let speed = 0;
    let moveDist = 0;

    if (this.lastCursorX !== null && this.lastCursorY !== null && this.lastCursorTime > 0) {
      const dt = Math.max(1, now - this.lastCursorTime);
      const dx = x - this.lastCursorX;
      const dy = y - this.lastCursorY;
      moveDist = Math.hypot(dx, dy);
      vx = dx / dt;
      vy = dy / dt;
      speed = Math.min(2.5, Math.hypot(vx, vy));

      // Advance persistent color phase with distance traveled (continuous cyclic progression)
      if (moveDist > 0) {
        this.colorPhase = (this.colorPhase + moveDist * this.config.colorShiftRate) % 1;
      }
    }

    this.currentVx = vx;
    this.currentVy = vy;
    this.currentSpeed = speed;
    this.lastCursorX = x;
    this.lastCursorY = y;
    this.lastCursorTime = now;

    // Snapshot current dynamic color
    const currentColor = getPaletteColorFromPhase(this.colorPhase);

    // Add trail point with historical color snapshot
    this.trailPoints.unshift({
      x,
      y,
      timestamp: now,
      vx,
      vy,
      speed,
      color: currentColor,
      colorPhase: this.colorPhase,
    });

    if (this.trailPoints.length > this.config.maxTrailPoints) {
      this.trailPoints.length = this.config.maxTrailPoints;
    }

    // Inject energy into nearby grid dots with directional bias
    this.injectGridEnergyAt(x, y, now, vx, vy, speed, 0.75 + speed * 0.25);
  }

  onPointerLeave(): void {
    this.isPointerActive = false;
    this.lastCursorX = null;
    this.lastCursorY = null;
    this.currentVx = 0;
    this.currentVy = 0;
    this.currentSpeed = 0;
    // NOTE: this.colorPhase is deliberately NOT reset, maintaining persistent energy state
  }

  /**
   * Injects energy into grid dots within interaction radius of (cx, cy) with directional bias.
   */
  injectGridEnergyAt(
    cx: number,
    cy: number,
    now: number,
    vx: number = 0,
    vy: number = 0,
    speed: number = 0,
    multiplier: number = 1.0
  ): void {
    const spacing = this.config.gridSpacing;
    const radius = this.config.interactionRadius;

    const minGridX = Math.floor((cx - radius) / spacing) * spacing;
    const maxGridX = Math.ceil((cx + radius) / spacing) * spacing;
    const minGridY = Math.floor((cy - radius) / spacing) * spacing;
    const maxGridY = Math.ceil((cy + radius) / spacing) * spacing;

    for (let gx = minGridX; gx <= maxGridX; gx += spacing) {
      for (let gy = minGridY; gy <= maxGridY; gy += spacing) {
        const influence = calculateRadialInfluence(gx, gy, cx, cy, radius);
        if (influence > 0) {
          const dirBias = calculateDirectionalBias(gx, gy, cx, cy, vx, vy, speed);
          const effectiveEnergy = influence * dirBias * multiplier * 0.85;

          const key = `${gx},${gy}`;
          const existing = this.activeDots.get(key);
          const currentEnergy = existing ? existing.energy : 0;
          // Additive energy capped at 1.0
          const newEnergy = Math.min(1.0, currentEnergy + effectiveEnergy);
          this.activeDots.set(key, {
            x: gx,
            y: gy,
            energy: newEnergy,
            lastUpdated: now,
          });
        }
      }
    }
  }

  /**
   * Updates state for the current animation frame: decays dots & prunes expired trail points.
   * Returns true if there is active visual activity to render.
   */
  update(now: number = performance.now()): boolean {
    this.lastFrameTime = now;

    // Prune old trail points
    const maxAge = this.config.trailMaxDurationMs;
    while (this.trailPoints.length > 0 && now - this.trailPoints[this.trailPoints.length - 1].timestamp > maxAge) {
      this.trailPoints.pop();
    }

    // Energize dots along recent comet wake
    if (this.trailPoints.length >= 2) {
      for (let i = 0; i < Math.min(4, this.trailPoints.length - 1); i++) {
        const p1 = this.trailPoints[i];
        const p2 = this.trailPoints[i + 1];
        const midX = (p1.x + p2.x) / 2;
        const midY = (p1.y + p2.y) / 2;
        const age = now - p1.timestamp;
        const ageFactor = Math.max(0, 1 - age / maxAge);
        if (ageFactor > 0.3) {
          this.injectGridEnergyAt(midX, midY, now, p1.vx, p1.vy, p1.speed, 0.35 * ageFactor);
        }
      }
    }

    // Decay active dots
    for (const [key, dot] of this.activeDots.entries()) {
      const dotDelta = now - dot.lastUpdated;
      const decayed = decayEnergy(dot.energy, dotDelta, this.config.decayRatePerSec);
      if (decayed <= this.config.energyThreshold) {
        this.activeDots.delete(key);
      } else {
        dot.energy = decayed;
        dot.lastUpdated = now;
      }
    }

    const hasActiveContent = this.trailPoints.length > 0 || this.activeDots.size > 0 || this.isPointerActive;
    return hasActiveContent;
  }

  /**
   * Renders the current state onto the 2D canvas context.
   * Features:
   * - Luminous energized dots
   * - Tapered Comet trail (Head -> Body -> Tail) with white luminous edges, dynamic colored core, and outer glow
   * - White-hot cursor head with dynamic color aura
   */
  render(ctx: CanvasRenderingContext2D, width: number, height: number, now: number = performance.now()): void {
    ctx.clearRect(0, 0, width, height);

    // 1. Render Energized Dots
    if (this.activeDots.size > 0) {
      for (const dot of this.activeDots.values()) {
        if (dot.x < -20 || dot.x > width + 20 || dot.y < -20 || dot.y > height + 20) continue;
        const energy = Math.min(1.0, dot.energy);
        if (energy <= 0.01) continue;

        // Base illuminated dot
        const dotRadius = this.config.dotRadius + energy * 0.9;
        const dotAlpha = 0.15 + energy * 0.65;

        // Outer soft glow for high-energy dots
        if (energy > 0.25) {
          ctx.beginPath();
          ctx.arc(dot.x, dot.y, dotRadius * 3.5, 0, Math.PI * 2);
          ctx.fillStyle = `rgba(139, 190, 255, ${(energy * 0.12).toFixed(3)})`;
          ctx.fill();
        }

        ctx.beginPath();
        ctx.arc(dot.x, dot.y, dotRadius, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(230, 245, 255, ${dotAlpha.toFixed(3)})`;
        ctx.fill();
      }
    }

    // 2. Render Tapered Comet Trail with Dynamic Color Core (Head -> Body -> Tail)
    if (this.trailPoints.length >= 2) {
      const maxAge = this.config.trailMaxDurationMs;
      const segments = computeSmoothPathSegments(this.trailPoints);
      const headPoint = this.trailPoints[0];
      const speedBoost = Math.min(1.0, headPoint.speed / 1.2);
      const widthScale = 1.0 + 0.35 * speedBoost;
      const glowScale = 1.0 + 0.4 * speedBoost;

      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';

      const totalSegments = segments.length;

      for (let i = 0; i < totalSegments; i++) {
        const seg = segments[i];
        const point = this.trailPoints[i];
        const age = now - point.timestamp;
        const ageFactor = Math.max(0, 1 - age / maxAge);
        if (ageFactor <= 0) continue;

        const ratio = i / totalSegments; // 0 = head, 1 = tail
        const life = Math.pow(ageFactor, 1.1) * Math.pow(1 - ratio * 0.75, 1.2);
        if (life <= 0.005) continue;

        // Use the snapshot color stored in the historical trail point
        const color = point.color || getPaletteColorFromPhase(this.colorPhase);

        // Layer 1: Soft Outer Glow Tinted with Segment Color (18px -> 4px taper)
        const outerWidth = Math.max(4, (18 - 12 * ratio) * glowScale);
        ctx.beginPath();
        ctx.moveTo(seg.start.x, seg.start.y);
        ctx.quadraticCurveTo(seg.control.x, seg.control.y, seg.end.x, seg.end.y);
        ctx.lineWidth = outerWidth;
        ctx.strokeStyle = `rgba(${color.r}, ${color.g}, ${color.b}, ${(life * 0.045 * (1 + 0.25 * speedBoost)).toFixed(3)})`;
        ctx.stroke();

        // Layer 2: Dynamic Colored Comet Body (7.5px -> 1.5px taper)
        const bodyWidth = Math.max(1.5, (7.5 - 5.5 * ratio) * widthScale);
        ctx.beginPath();
        ctx.moveTo(seg.start.x, seg.start.y);
        ctx.quadraticCurveTo(seg.control.x, seg.control.y, seg.end.x, seg.end.y);
        ctx.lineWidth = bodyWidth;
        ctx.strokeStyle = `rgba(${color.r}, ${color.g}, ${color.b}, ${(life * 0.38 * (1 + 0.2 * speedBoost)).toFixed(3)})`;
        ctx.stroke();

        // Layer 3: White Luminous Core / Edges (1.8px -> 0.6px taper)
        const coreWidth = Math.max(0.6, 1.8 - 1.2 * ratio);
        ctx.beginPath();
        ctx.moveTo(seg.start.x, seg.start.y);
        ctx.quadraticCurveTo(seg.control.x, seg.control.y, seg.end.x, seg.end.y);
        ctx.lineWidth = coreWidth;
        ctx.strokeStyle = `rgba(255, 255, 255, ${(life * 0.50 * (1 - ratio * 0.5)).toFixed(3)})`;
        ctx.stroke();
      }

      // 3. White-Hot Luminous Comet Head with Dynamic Color Aura
      if (now - headPoint.timestamp < 100) {
        const headColor = headPoint.color || getPaletteColorFromPhase(this.colorPhase);

        // Dynamic colored soft aura
        ctx.beginPath();
        ctx.arc(headPoint.x, headPoint.y, 8 + 4 * speedBoost, 0, Math.PI * 2);
        ctx.fillStyle = `rgba(${headColor.r}, ${headColor.g}, ${headColor.b}, ${(0.22 * (1 + 0.3 * speedBoost)).toFixed(3)})`;
        ctx.fill();

        // White-hot luminous core
        ctx.beginPath();
        ctx.arc(headPoint.x, headPoint.y, 2.5 + 0.8 * speedBoost, 0, Math.PI * 2);
        ctx.fillStyle = 'rgba(255, 255, 255, 0.95)';
        ctx.fill();
      }
    }
  }
}
