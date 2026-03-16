/**
 * PixelBackground – animated canvas component.
 *
 * Renders a faint 8×8 chess-board grid pattern with floating particle dots.
 * Canvas is positioned absolute, inset: 0, z-index: 0.
 *
 * Behaviours:
 * - Theme-aware: dark mode → lighter particles; light mode → darker particles.
 * - Pauses the rAF loop when document is hidden (visibilitychange).
 * - Uses ResizeObserver for responsive canvas sizing.
 * - Respects prefers-reduced-motion (shows static grid only, no animation).
 * - Gracefully no-ops when canvas.getContext('2d') returns null (jsdom).
 */
import React, { useCallback, useEffect, useRef, useState } from "react";
import { useTheme } from "../theme/ThemeContext.js";

// ─── Public types ──────────────────────────────────────────────────────────────

export interface DataPoint {
  x: number;
  y: number;
  value?: number;
}

export interface PixelBackgroundProps {
  /**
   * Multiplier for grid opacity and particle count / brightness.
   * Range 0–2. Default: 1.
   */
  intensity?: number;
  /**
   * Optional data-driven particle anchors (reserved for future use).
   */
  dataPoints?: DataPoint[];
}

// ─── Internal types ────────────────────────────────────────────────────────────

interface Particle {
  x: number;
  y: number;
  vx: number;
  vy: number;
  radius: number;
  alpha: number;
}

// ─── Constants ─────────────────────────────────────────────────────────────────

/** Pixel size of each chess-board cell. */
const GRID_CELL_PX = 64;

/** Base opacity for alternating grid squares. */
const GRID_BASE_ALPHA = 0.05;

/** Default number of floating particles. */
const BASE_PARTICLE_COUNT = 40;

// ─── Pure helpers ──────────────────────────────────────────────────────────────

function detectCanvasSupport(): boolean {
  if (typeof document === "undefined") {
    return false;
  }

  const probe = document.createElement("canvas");
  if (typeof probe.getContext !== "function") {
    return false;
  }

  try {
    return probe.getContext("2d") !== null;
  } catch {
    return false;
  }
}

function initParticles(w: number, h: number, count: number): Particle[] {
  return Array.from({ length: Math.max(1, count) }, () => ({
    x: Math.random() * w,
    y: Math.random() * h,
    vx: (Math.random() - 0.5) * 0.4,
    vy: (Math.random() - 0.5) * 0.4,
    radius: Math.random() * 1.5 + 0.5,
    alpha: Math.random() * 0.35 + 0.1,
  }));
}

function tickParticles(particles: Particle[], w: number, h: number): void {
  const pad = 16;
  for (const p of particles) {
    p.x += p.vx;
    p.y += p.vy;
    if (p.x < -pad) p.x = w + pad;
    if (p.x > w + pad) p.x = -pad;
    if (p.y < -pad) p.y = h + pad;
    if (p.y > h + pad) p.y = -pad;
  }
}

function drawScene(
  ctx: CanvasRenderingContext2D,
  w: number,
  h: number,
  particles: Particle[],
  isDark: boolean,
  intensity: number,
  showParticles: boolean,
): void {
  ctx.clearRect(0, 0, w, h);

  // ── Chess-board grid ──────────────────────────────────────────────────────
  const gridAlpha = GRID_BASE_ALPHA * intensity;
  ctx.fillStyle = isDark
    ? `rgba(255,255,255,${gridAlpha})`
    : `rgba(0,0,0,${gridAlpha})`;

  const cols = Math.ceil(w / GRID_CELL_PX) + 1;
  const rows = Math.ceil(h / GRID_CELL_PX) + 1;
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      if ((r + c) % 2 === 0) {
        ctx.fillRect(
          c * GRID_CELL_PX,
          r * GRID_CELL_PX,
          GRID_CELL_PX,
          GRID_CELL_PX,
        );
      }
    }
  }

  if (!showParticles) {
    return;
  }

  // ── Floating particles ───────────────────────────────────────────────────
  const rgb = isDark ? "220,230,255" : "30,40,80";
  for (const p of particles) {
    const a = Math.min(1, p.alpha * intensity);

    // Soft glow halo
    const glow = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, p.radius * 5);
    glow.addColorStop(0, `rgba(${rgb},${a * 0.55})`);
    glow.addColorStop(1, `rgba(${rgb},0)`);
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.radius * 5, 0, Math.PI * 2);
    ctx.fillStyle = glow;
    ctx.fill();

    // Solid core dot
    ctx.beginPath();
    ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
    ctx.fillStyle = `rgba(${rgb},${Math.min(1, a * 1.6)})`;
    ctx.fill();
  }
}

// ─── Component ─────────────────────────────────────────────────────────────────

export const PixelBackground: React.FC<PixelBackgroundProps> = ({
  intensity = 1,
  dataPoints: _dataPoints,
}) => {
  const { theme } = useTheme();
  const [hasCanvasSupport] = useState(detectCanvasSupport);

  const canvasRef = useRef<HTMLCanvasElement>(null);
  const particlesRef = useRef<Particle[]>([]);
  const rafRef = useRef<number>(0);
  const sizeRef = useRef<{ w: number; h: number }>({ w: 0, h: 0 });

  // Mutable refs so the rAF loop always reads the latest values.
  const isDarkRef = useRef<boolean>(theme === "dark");
  const intensityRef = useRef<number>(intensity);

  isDarkRef.current = theme === "dark";
  intensityRef.current = intensity;

  const stopAnim = useCallback(() => {
    cancelAnimationFrame(rafRef.current);
    rafRef.current = 0;
  }, []);

  const startAnim = useCallback(
    (ctx: CanvasRenderingContext2D) => {
      cancelAnimationFrame(rafRef.current);

      const tick = () => {
        if (!document.hidden) {
          const { w, h } = sizeRef.current;
          tickParticles(particlesRef.current, w, h);
          drawScene(
            ctx,
            w,
            h,
            particlesRef.current,
            isDarkRef.current,
            intensityRef.current,
            true,
          );
        }
        rafRef.current = requestAnimationFrame(tick);
      };

      rafRef.current = requestAnimationFrame(tick);
    },
    [],
  );

  useEffect(() => {
    if (!hasCanvasSupport) {
      return;
    }

    const canvas = canvasRef.current;
    if (!canvas) return;

    let ctx: CanvasRenderingContext2D | null = null;
    try {
      ctx = canvas.getContext("2d");
    } catch {
      return;
    }
    if (!ctx) return;

    const mq = typeof window !== "undefined" && typeof window.matchMedia === "function"
      ? window.matchMedia("(prefers-reduced-motion: reduce)")
      : null;
    let prefersReduced = mq?.matches ?? false;

    const applySize = (w: number, h: number) => {
      const safeW = Math.max(1, w);
      const safeH = Math.max(1, h);
      canvas.width = safeW;
      canvas.height = safeH;
      sizeRef.current = { w: safeW, h: safeH };
      particlesRef.current = prefersReduced
        ? []
        : initParticles(
          safeW,
          safeH,
          Math.round(BASE_PARTICLE_COUNT * intensityRef.current),
        );
      drawScene(
        ctx,
        safeW,
        safeH,
        particlesRef.current,
        isDarkRef.current,
        intensityRef.current,
        !prefersReduced,
      );
    };

    const parent = canvas.parentElement ?? canvas;
    applySize(parent.offsetWidth || 300, parent.offsetHeight || 300);

    if (!prefersReduced) {
      startAnim(ctx);
    }

    const onVisibilityChange = () => {
      if (document.hidden) {
        stopAnim();
      } else if (!prefersReduced) {
        startAnim(ctx);
      }
    };
    document.addEventListener("visibilitychange", onVisibilityChange);

    const onMotionChange = (e: MediaQueryListEvent) => {
      prefersReduced = e.matches;
      stopAnim();

      const { w, h } = sizeRef.current;
      particlesRef.current = prefersReduced
        ? []
        : initParticles(
          w,
          h,
          Math.round(BASE_PARTICLE_COUNT * intensityRef.current),
        );

      drawScene(
        ctx,
        w,
        h,
        particlesRef.current,
        isDarkRef.current,
        intensityRef.current,
        !prefersReduced,
      );

      if (!prefersReduced && !document.hidden) {
        startAnim(ctx);
      }
    };

    if (mq) {
      if (typeof mq.addEventListener === "function") {
        mq.addEventListener("change", onMotionChange);
      } else {
        mq.addListener(onMotionChange);
      }
    }

    let disconnectResize: () => void = () => {};
    if (typeof ResizeObserver !== "undefined") {
      const ro = new ResizeObserver((entries) => {
        const entry = entries[0];
        if (!entry) return;
        applySize(
          Math.round(entry.contentRect.width),
          Math.round(entry.contentRect.height),
        );
      });
      ro.observe(parent);
      disconnectResize = () => ro.disconnect();
    } else if (typeof window !== "undefined") {
      const onResize = () => {
        applySize(parent.clientWidth || 300, parent.clientHeight || 300);
      };
      window.addEventListener("resize", onResize);
      disconnectResize = () => window.removeEventListener("resize", onResize);
    }

    return () => {
      stopAnim();
      document.removeEventListener("visibilitychange", onVisibilityChange);
      if (mq) {
        if (typeof mq.removeEventListener === "function") {
          mq.removeEventListener("change", onMotionChange);
        } else {
          mq.removeListener(onMotionChange);
        }
      }
      disconnectResize();
    };
  }, [hasCanvasSupport, intensity, startAnim, stopAnim, theme]);

  if (!hasCanvasSupport) {
    return null;
  }

  return (
    <canvas
      ref={canvasRef}
      data-testid="pixel-background"
      aria-hidden="true"
      style={{
        position: "absolute",
        inset: 0,
        width: "100%",
        height: "100%",
        zIndex: 0,
        pointerEvents: "none",
        display: "block",
      }}
    />
  );
};
