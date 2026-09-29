"use client";

import {
  useEffect,
  useRef,
  useState,
  useSyncExternalStore,
  type RefObject,
} from "react";

type Tilt = { x: number; y: number };
type Orientation = typeof DeviceOrientationEvent & {
  requestPermission?: () => Promise<"granted" | "denied">;
};

const clamp = (v: number) => Math.max(-1, Math.min(1, v));
const noSubscription = () => () => {};
const touchScreen = () =>
  "DeviceOrientationEvent" in window &&
  window.matchMedia("(pointer: coarse)").matches;

function apply(world: HTMLElement | null, store: Tilt, x: number, y: number) {
  store.x = clamp(x);
  store.y = clamp(y);
  world?.style.setProperty("--tilt-x", store.x.toFixed(3));
  world?.style.setProperty("--tilt-y", store.y.toFixed(3));
}

/**
 * Turns the town. Dragging the stage (mouse or finger) sets --tilt-x / --tilt-y on the world, from -1 to 1;
 * a press that moves less than 6 px stays a click, so selecting a home still works, and a drag never
 * selects. turn() steps it from buttons, for keyboards and single taps. Device tilt is offered only on touch
 * screens and only after a tap, because iOS asks permission then; refusing leaves drag working.
 */
export function useTilt(
  stage: RefObject<HTMLElement | null>,
  world: RefObject<HTMLElement | null>,
) {
  const canTilt = useSyncExternalStore(
    noSubscription,
    touchScreen,
    () => false,
  );
  const [tilting, setTilting] = useState(false);
  const [refused, setRefused] = useState(false);
  const tilt = useRef<Tilt>({ x: 0, y: 0 });

  useEffect(() => {
    const el = stage.current;
    if (!el) return;
    let start: {
      x: number;
      y: number;
      tx: number;
      ty: number;
      id: number;
    } | null = null;
    let dragged = false;
    const down = (e: PointerEvent) => {
      if (!e.isPrimary || e.button !== 0) return;
      start = {
        x: e.clientX,
        y: e.clientY,
        tx: tilt.current.x,
        ty: tilt.current.y,
        id: e.pointerId,
      };
      dragged = false;
    };
    const move = (e: PointerEvent) => {
      if (!start || e.pointerId !== start.id) return;
      // Released somewhere the stage never heard about (outside it, or under a context menu): not a drag.
      if (e.buttons === 0) {
        end();
        return;
      }
      const dx = e.clientX - start.x;
      const dy = e.clientY - start.y;
      if (!dragged && Math.hypot(dx, dy) < 6) return;
      if (!dragged) {
        dragged = true;
        el.setPointerCapture(e.pointerId);
        world.current?.setAttribute("data-dragging", "");
      }
      const r = el.getBoundingClientRect();
      apply(
        world.current,
        tilt.current,
        start.tx + (dx / r.width) * 2,
        start.ty + (dy / r.height) * 2,
      );
    };
    const end = () => {
      start = null;
      world.current?.removeAttribute("data-dragging");
      // The click a mouse drag ends with comes in this same task; a finger drag ends with none, and a
      // later keyboard press must not be swallowed.
      window.setTimeout(() => (dragged = false), 0);
    };
    const up = (e: PointerEvent) => {
      if (start && e.pointerId === start.id) end();
    };
    // Capture phase, before React's handlers: the click that ends a drag must not select a home. Keyboard
    // and assistive-technology clicks (detail 0) are never a drag's.
    const click = (e: MouseEvent) => {
      if (!dragged || e.detail === 0) return;
      dragged = false;
      e.stopPropagation();
      e.preventDefault();
    };
    el.addEventListener("pointerdown", down);
    el.addEventListener("pointermove", move);
    el.addEventListener("pointerup", up);
    el.addEventListener("pointercancel", up);
    el.addEventListener("click", click, true);
    return () => {
      el.removeEventListener("pointerdown", down);
      el.removeEventListener("pointermove", move);
      el.removeEventListener("pointerup", up);
      el.removeEventListener("pointercancel", up);
      el.removeEventListener("click", click, true);
    };
  }, [stage, world]);

  useEffect(() => {
    if (!tilting) return;
    const onTilt = (e: DeviceOrientationEvent) => {
      if (e.gamma == null || e.beta == null) return;
      apply(world.current, tilt.current, e.gamma / 45, (e.beta - 50) / 30);
    };
    window.addEventListener("deviceorientation", onTilt);
    return () => window.removeEventListener("deviceorientation", onTilt);
  }, [tilting, world]);

  const toggleDeviceTilt = async () => {
    if (tilting) {
      setTilting(false);
      return;
    }
    const orientation = window.DeviceOrientationEvent as Orientation;
    if (orientation.requestPermission) {
      const answer = await orientation
        .requestPermission()
        .catch(() => "denied" as const);
      if (answer !== "granted") {
        setRefused(true);
        return;
      }
    }
    setRefused(false);
    setTilting(true);
  };

  const turn = (step: number) =>
    apply(world.current, tilt.current, tilt.current.x + step, tilt.current.y);

  return { canTilt, tilting, refused, toggleDeviceTilt, turn };
}
