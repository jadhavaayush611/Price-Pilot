import React, { useEffect, useRef } from 'react';
import { AmbientAnimationState, DEFAULT_CONFIG } from './ambientBackground.ts';

export const AmbientBackground: React.FC = () => {
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const stateRef = useRef<AmbientAnimationState | null>(null);
  const rafIdRef = useRef<number | null>(null);
  const isRunningRef = useRef<boolean>(false);

  useEffect(() => {
    // 1. Check for reduced motion preference
    const prefersReducedMotion = typeof window !== 'undefined' && 
      window.matchMedia && 
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    // 2. Check for fine pointer (mouse/trackpad vs touch)
    const hasFinePointer = typeof window !== 'undefined' &&
      window.matchMedia &&
      (window.matchMedia('(pointer: fine)').matches || !window.matchMedia('(pointer: coarse)').matches);

    // If reduced motion is requested or touch device, we keep the static CSS dot grid and skip canvas RAF
    if (prefersReducedMotion || !hasFinePointer) {
      return;
    }

    const canvas = canvasRef.current;
    if (!canvas) return;

    const ctx = canvas.getContext('2d', { alpha: true });
    if (!ctx) return;

    // Initialize engine state
    const state = new AmbientAnimationState(DEFAULT_CONFIG);
    stateRef.current = state;

    let width = 0;
    let height = 0;
    let dpr = 1;

    const updateDimensions = () => {
      if (!canvas) return;
      dpr = Math.min(window.devicePixelRatio || 1, DEFAULT_CONFIG.maxDpr);
      width = window.innerWidth;
      height = window.innerHeight;

      canvas.width = Math.floor(width * dpr);
      canvas.height = Math.floor(height * dpr);
      canvas.style.width = `${width}px`;
      canvas.style.height = `${height}px`;

      ctx.setTransform(1, 0, 0, 1, 0, 0);
      ctx.scale(dpr, dpr);
    };

    updateDimensions();

    // Render loop
    const renderLoop = (now: number) => {
      if (!isRunningRef.current) return;

      const hasActiveContent = state.update(now);
      state.render(ctx, width, height, now);

      if (hasActiveContent && document.visibilityState === 'visible') {
        rafIdRef.current = requestAnimationFrame(renderLoop);
      } else {
        // Sleep when idle
        isRunningRef.current = false;
        rafIdRef.current = null;
      }
    };

    const startAnimation = () => {
      if (!isRunningRef.current && document.visibilityState === 'visible') {
        isRunningRef.current = true;
        state.lastFrameTime = performance.now();
        rafIdRef.current = requestAnimationFrame(renderLoop);
      }
    };

    // Pointer event handlers
    const handlePointerMove = (e: PointerEvent) => {
      // Only track fine pointer (mouse/pen)
      if (e.pointerType === 'touch') return;
      state.onPointerMove(e.clientX, e.clientY, performance.now());
      startAnimation();
    };

    const handlePointerLeave = () => {
      state.onPointerLeave();
    };

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        startAnimation();
      } else if (rafIdRef.current) {
        cancelAnimationFrame(rafIdRef.current);
        isRunningRef.current = false;
        rafIdRef.current = null;
      }
    };

    const handleResize = () => {
      updateDimensions();
      startAnimation();
    };

    window.addEventListener('pointermove', handlePointerMove, { passive: true });
    document.addEventListener('pointerleave', handlePointerLeave, { passive: true });
    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('resize', handleResize, { passive: true });

    return () => {
      if (rafIdRef.current) {
        cancelAnimationFrame(rafIdRef.current);
      }
      isRunningRef.current = false;
      window.removeEventListener('pointermove', handlePointerMove);
      document.removeEventListener('pointerleave', handlePointerLeave);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('resize', handleResize);
    };
  }, []);

  return (
    <div 
      className="fixed inset-0 pointer-events-none z-0 overflow-hidden" 
      aria-hidden="true"
      data-testid="ambient-background"
    >
      {/* 1. Subtle Static CSS Dot Grid (0.1% baseline opacity) */}
      <div 
        className="absolute inset-0 pointer-events-none opacity-90"
        style={{
          backgroundImage: 'radial-gradient(circle 0.75px at center, rgba(255, 255, 255, 0.001) 0.75px, transparent 0.75px)',
          backgroundSize: '28px 28px',
          backgroundPosition: 'center center',
        }}
      />

      {/* 2. Interactive Dynamic Illumination & Cursor Trail Canvas */}
      <canvas
        ref={canvasRef}
        className="absolute inset-0 pointer-events-none w-full h-full"
      />
    </div>
  );
};

export default AmbientBackground;
