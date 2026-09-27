"use client";
import { useSyncExternalStore } from "react";
const sections = [
  "overview",
  "plan",
  "checkin",
  "readings",
  "people",
  "schedule",
  "staff",
] as const;
export type WorkspaceSection = (typeof sections)[number];
function snapshot(): WorkspaceSection {
  const hash = window.location.hash.slice(1);
  const value =
    hash === "check-in" ? "checkin" : hash === "privacy" ? "people" : hash;
  return sections.includes(value as WorkspaceSection)
    ? (value as WorkspaceSection)
    : "overview";
}
function subscribe(callback: () => void) {
  window.addEventListener("hashchange", callback);
  window.addEventListener("popstate", callback);
  return () => {
    window.removeEventListener("hashchange", callback);
    window.removeEventListener("popstate", callback);
  };
}
export function useWorkspaceNavigation() {
  const section = useSyncExternalStore(
    subscribe,
    snapshot,
    () => "overview" as WorkspaceSection,
  );
  function navigate(next: WorkspaceSection) {
    if (snapshot() !== next) window.history.pushState(null, "", `#${next}`);
    window.dispatchEvent(new Event("hashchange"));
    const target =
      document.getElementById(
        next === "overview" ? "overview" : "care-panel",
      ) || document.getElementById(next);
    target?.scrollIntoView({ block: "start" });
  }
  return { section, navigate };
}
