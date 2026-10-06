import { describe, it, expect, beforeEach } from 'vitest';
import {
  smoothstep,
  calculateRadialInfluence,
  calculateDirectionalBias,
  decayEnergy,
  getPaletteColorFromPhase,
  getInterpolatedPaletteColor,
  computeSmoothPathSegments,
  distanceToSegment,
  shouldEnableInteractiveAmbient,
  AmbientAnimationState,
  DEFAULT_CONFIG,
  TRAIL_PALETTE,
} from './ambientBackground.ts';

describe('Ambient Background Engine & Dynamic Comet Tests', () => {
  describe('Configuration Defaults & Inactive Dot Opacity', () => {
    it('uses 28px dot grid spacing, 95px interaction radius, and 0.04 (4%) calibrated baseline opacity', () => {
      expect(DEFAULT_CONFIG.gridSpacing).toBe(28);
      expect(DEFAULT_CONFIG.interactionRadius).toBe(95);
      expect(DEFAULT_CONFIG.dotRadius).toBe(0.75);
      expect(DEFAULT_CONFIG.baseDotOpacity).toBe(0.04); // 4% calibrated subtle baseline
      expect(DEFAULT_CONFIG.colorShiftRate).toBe(0.0008);
    });

    it('ensures baseline dot opacity is clearly perceptible against #030303 without overpowering UI', () => {
      expect(DEFAULT_CONFIG.baseDotOpacity).toBeGreaterThanOrEqual(0.02);
      expect(DEFAULT_CONFIG.baseDotOpacity).toBeLessThanOrEqual(0.08);
    });
  });

  describe('Accessibility & Device Capability Rules (shouldEnableInteractiveAmbient)', () => {
    it('enables interactive canvas animation on standard desktop with fine pointer', () => {
      const prefersReducedMotion = false;
      const hasFinePointer = true;
      expect(shouldEnableInteractiveAmbient(prefersReducedMotion, hasFinePointer)).toBe(true);
    });

    it('disables interactive canvas animation when user requests reduced motion', () => {
      const prefersReducedMotion = true;
      const hasFinePointer = true;
      expect(shouldEnableInteractiveAmbient(prefersReducedMotion, hasFinePointer)).toBe(false);
    });

    it('disables interactive canvas animation on touch / mobile devices lacking fine pointer', () => {
      const prefersReducedMotion = false;
      const hasFinePointer = false;
      expect(shouldEnableInteractiveAmbient(prefersReducedMotion, hasFinePointer)).toBe(false);
    });

    it('disables interactive canvas animation when touch device also has reduced motion', () => {
      const prefersReducedMotion = true;
      const hasFinePointer = false;
      expect(shouldEnableInteractiveAmbient(prefersReducedMotion, hasFinePointer)).toBe(false);
    });
  });

  describe('smoothstep', () => {
    it('clamps values below min to 0', () => {
      expect(smoothstep(0, 10, -5)).toBe(0);
      expect(smoothstep(0, 10, 0)).toBe(0);
    });

    it('clamps values above max to 1', () => {
      expect(smoothstep(0, 10, 15)).toBe(1);
      expect(smoothstep(0, 10, 10)).toBe(1);
    });

    it('smoothly interpolates intermediate values', () => {
      const mid = smoothstep(0, 10, 5);
      expect(mid).toBeCloseTo(0.5, 3);
    });
  });

  describe('calculateRadialInfluence (95px radius)', () => {
    const radius = 95;
    const cx = 200;
    const cy = 200;

    it('returns 1 (max influence) when dot is exactly at cursor center', () => {
      const influence = calculateRadialInfluence(cx, cy, cx, cy, radius);
      expect(influence).toBeCloseTo(1.0, 4);
    });

    it('returns 0 when dot is at or beyond 95px interaction radius', () => {
      const influenceAtEdge = calculateRadialInfluence(cx + radius, cy, cx, cy, radius);
      const influenceBeyond = calculateRadialInfluence(cx + radius + 10, cy, cx, cy, radius);
      expect(influenceAtEdge).toBe(0);
      expect(influenceBeyond).toBe(0);
    });

    it('falls off with concentrated core and fast perimeter decay', () => {
      const near = calculateRadialInfluence(cx + 20, cy, cx, cy, radius);
      const mid = calculateRadialInfluence(cx + 47.5, cy, cx, cy, radius);
      const far = calculateRadialInfluence(cx + 80, cy, cx, cy, radius);

      expect(near).toBeGreaterThan(mid);
      expect(mid).toBeGreaterThan(far);
      expect(far).toBeGreaterThan(0);
      expect(mid).toBeLessThan(0.35);
    });
  });

  describe('calculateDirectionalBias (Velocity-Aware Directional Dot Energy)', () => {
    const cx = 300;
    const cy = 300;

    it('returns 1.0 (pure radial) when cursor is stationary (speed = 0)', () => {
      const bias = calculateDirectionalBias(cx + 20, cy, cx, cy, 0, 0, 0);
      expect(bias).toBe(1.0);
    });

    it('returns 1.0 (pure radial) when speed is near zero (< 0.02)', () => {
      const bias = calculateDirectionalBias(cx + 20, cy, cx, cy, 0.005, 0.005, 0.01);
      expect(bias).toBe(1.0);
    });

    it('produces stronger influence behind cursor when moving right', () => {
      const vx = 1.0;
      const vy = 0.0;
      const speed = 1.0;

      // Dot behind cursor (to the left: x = cx - 20)
      const biasBehind = calculateDirectionalBias(cx - 20, cy, cx, cy, vx, vy, speed);
      // Dot ahead of cursor (to the right: x = cx + 20)
      const biasAhead = calculateDirectionalBias(cx + 20, cy, cx, cy, vx, vy, speed);

      expect(biasBehind).toBeGreaterThan(1.0);
      expect(biasAhead).toBeLessThan(1.0);
      expect(biasBehind).toBeGreaterThan(biasAhead);
    });

    it('produces stronger influence behind cursor when moving left', () => {
      const vx = -1.0;
      const vy = 0.0;
      const speed = 1.0;

      // Dot behind cursor (to the right: x = cx + 20)
      const biasBehind = calculateDirectionalBias(cx + 20, cy, cx, cy, vx, vy, speed);
      // Dot ahead of cursor (to the left: x = cx - 20)
      const biasAhead = calculateDirectionalBias(cx - 20, cy, cx, cy, vx, vy, speed);

      expect(biasBehind).toBeGreaterThan(1.0);
      expect(biasAhead).toBeLessThan(1.0);
      expect(biasBehind).toBeGreaterThan(biasAhead);
    });

    it('clamps directional influence within [0.4, 1.6] even at high velocity', () => {
      const vx = 10.0;
      const vy = 0.0;
      const speed = 10.0;

      const biasBehind = calculateDirectionalBias(cx - 20, cy, cx, cy, vx, vy, speed);
      const biasAhead = calculateDirectionalBias(cx + 20, cy, cx, cy, vx, vy, speed);

      expect(biasBehind).toBeLessThanOrEqual(1.6);
      expect(biasBehind).toBeGreaterThanOrEqual(1.0);
      expect(biasAhead).toBeGreaterThanOrEqual(0.4);
      expect(biasAhead).toBeLessThanOrEqual(1.0);
    });
  });

  describe('getPaletteColorFromPhase (True Dynamic Cyclic Core Color)', () => {
    it('returns exact palette anchor colors at quarterly phase checkpoints', () => {
      const colorAt0 = getPaletteColorFromPhase(0.0);
      const colorAt25 = getPaletteColorFromPhase(0.25);
      const colorAt50 = getPaletteColorFromPhase(0.50);
      const colorAt75 = getPaletteColorFromPhase(0.75);

      expect(colorAt0).toEqual(TRAIL_PALETTE[0]);   // Teal
      expect(colorAt25).toEqual(TRAIL_PALETTE[1]);  // Blue
      expect(colorAt50).toEqual(TRAIL_PALETTE[2]);  // Violet
      expect(colorAt75).toEqual(TRAIL_PALETTE[3]);  // Magenta
    });

    it('smoothly wraps phase cyclically at and beyond 1.0', () => {
      const colorAt0 = getPaletteColorFromPhase(0.0);
      const colorAt1 = getPaletteColorFromPhase(1.0);
      const colorAt2 = getPaletteColorFromPhase(2.0);
      const colorAt125 = getPaletteColorFromPhase(1.25);
      const colorAt25 = getPaletteColorFromPhase(0.25);

      expect(colorAt1).toEqual(colorAt0);
      expect(colorAt2).toEqual(colorAt0);
      expect(colorAt125).toEqual(colorAt25);
    });

    it('interpolates smoothly between colors without abrupt step jumps', () => {
      const c1 = getPaletteColorFromPhase(0.10);
      const c2 = getPaletteColorFromPhase(0.15);
      const c3 = getPaletteColorFromPhase(0.20);

      // Channel progression from Teal (r:79, g:209, b:197) -> Blue (r:96, g:165, b:250)
      expect(c2.r).toBeGreaterThanOrEqual(c1.r);
      expect(c3.r).toBeGreaterThanOrEqual(c2.r);
      expect(c2.b).toBeGreaterThanOrEqual(c1.b);
      expect(c3.b).toBeGreaterThanOrEqual(c2.b);
    });

    it('legacy getInterpolatedPaletteColor delegates to continuous phase interpolation', () => {
      expect(getInterpolatedPaletteColor(0.5)).toEqual(getPaletteColorFromPhase(0.5));
    });
  });

  describe('decayEnergy', () => {
    it('exponentially decays energy over time delta', () => {
      const initial = 1.0;
      const decayRate = 3.2;

      const after100ms = decayEnergy(initial, 100, decayRate);
      const after500ms = decayEnergy(initial, 500, decayRate);
      const after1000ms = decayEnergy(initial, 1000, decayRate);

      expect(after100ms).toBeLessThan(initial);
      expect(after500ms).toBeLessThan(after100ms);
      expect(after1000ms).toBeLessThan(after500ms);
      expect(after1000ms).toBeCloseTo(Math.exp(-3.2), 3);
    });

    it('handles zero or negative deltas gracefully', () => {
      expect(decayEnergy(0.8, 0, 3.2)).toBe(0.8);
      expect(decayEnergy(0.8, -50, 3.2)).toBe(0.8);
    });
  });

  describe('computeSmoothPathSegments', () => {
    it('returns empty array for fewer than 2 points', () => {
      expect(computeSmoothPathSegments([])).toEqual([]);
      expect(computeSmoothPathSegments([{ x: 10, y: 10 }])).toEqual([]);
    });

    it('creates quadratic midpoint curve segments for point sequence', () => {
      const points = [
        { x: 0, y: 0 },
        { x: 50, y: 50 },
        { x: 100, y: 0 },
      ];

      const segments = computeSmoothPathSegments(points);
      expect(segments.length).toBe(2);
      expect(segments[0].start).toEqual({ x: 0, y: 0 });
      expect(segments[0].control).toEqual({ x: 0, y: 0 });
      expect(segments[0].end).toEqual({ x: 25, y: 25 });
    });
  });

  describe('distanceToSegment', () => {
    it('calculates orthogonal distance to line segment', () => {
      const dist = distanceToSegment(50, 20, 0, 0, 100, 0);
      expect(dist).toBeCloseTo(20, 4);
    });

    it('calculates distance to closest endpoint when outside segment projection', () => {
      const dist = distanceToSegment(-10, 0, 0, 0, 100, 0);
      expect(dist).toBeCloseTo(10, 4);
    });
  });

  describe('AmbientAnimationState Dynamic Color Evolution & Persistence', () => {
    let state: AmbientAnimationState;

    beforeEach(() => {
      state = new AmbientAnimationState({
        maxTrailPoints: 12,
        trailMaxDurationMs: 500,
        decayRatePerSec: 4.0,
        colorShiftRate: 0.001, // 1000px = 1.0 phase cycle for easy testing
      });
    });

    it('advances colorPhase with cursor movement distance', () => {
      expect(state.colorPhase).toBe(0);

      // First point establishes position
      state.onPointerMove(100, 100, 1000);
      expect(state.colorPhase).toBe(0);

      // Move 200px right (dx=200, dy=0)
      state.onPointerMove(300, 100, 1016);
      // Expected phase advance: 200 * 0.001 = 0.20
      expect(state.colorPhase).toBeCloseTo(0.20, 3);

      // Move 300px down (dx=0, dy=300)
      state.onPointerMove(300, 400, 1032);
      // Expected phase advance: 0.20 + 300 * 0.001 = 0.50
      expect(state.colorPhase).toBeCloseTo(0.50, 3);
    });

    it('does not advance colorPhase when cursor is stationary', () => {
      state.onPointerMove(100, 100, 1000);
      state.onPointerMove(200, 100, 1016);
      const phaseAfterMove = state.colorPhase;
      expect(phaseAfterMove).toBeGreaterThan(0);

      // Stationary event at same position
      state.onPointerMove(200, 100, 1032);
      expect(state.colorPhase).toBe(phaseAfterMove);
    });

    it('preserves colorPhase across pause and resume (freezes on stop, resumes next move)', () => {
      state.onPointerMove(100, 100, 1000);
      state.onPointerMove(350, 100, 1016); // 250px move => phase = 0.25
      expect(state.colorPhase).toBeCloseTo(0.25, 3);

      // Cursor leaves / stops
      state.onPointerLeave();
      expect(state.colorPhase).toBeCloseTo(0.25, 3);

      // New movement starts elsewhere later
      state.onPointerMove(500, 500, 2000);
      expect(state.colorPhase).toBeCloseTo(0.25, 3);

      // Continues moving 250px => phase advances to 0.50
      state.onPointerMove(750, 500, 2016);
      expect(state.colorPhase).toBeCloseTo(0.50, 3);
    });

    it('records and preserves historical color snapshots on individual TrailPoints', () => {
      // Step 1: Birth of Point A at phase ~0 (Teal)
      state.onPointerMove(100, 100, 1000);
      const colorA = state.trailPoints[0].color;
      expect(colorA).toEqual(TRAIL_PALETTE[0]); // Teal

      // Step 2: Move 250px => phase ~0.25 (Sky Blue)
      state.onPointerMove(350, 100, 1016);
      const colorB = state.trailPoints[0].color;
      expect(colorB).toEqual(TRAIL_PALETTE[1]); // Sky Blue

      // Point A still retains its original Teal color snapshot
      expect(state.trailPoints[1].color).toEqual(colorA);
      expect(state.trailPoints[0].color).not.toEqual(state.trailPoints[1].color);
    });

    it('decays dot energy and sleeps when idle without losing color phase', () => {
      state.onPointerMove(300, 300, 1000);
      state.onPointerMove(500, 300, 1016);
      const savedPhase = state.colorPhase;
      expect(savedPhase).toBeGreaterThan(0);

      // Advance time past trailMaxDurationMs (500ms)
      state.update(1600);
      expect(state.trailPoints.length).toBe(0);

      // Advance time so dot energy decays below threshold
      state.update(4000);
      expect(state.activeDots.size).toBe(0);

      state.onPointerLeave();
      const hasContentIdle = state.update(4100);
      expect(hasContentIdle).toBe(false); // RAF loop sleeps cleanly

      // Persistent color phase is intact
      expect(state.colorPhase).toBe(savedPhase);
    });
  });
});
