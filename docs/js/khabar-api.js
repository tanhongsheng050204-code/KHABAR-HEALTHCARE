/*
 * Tiny client for the Khabar clinical API, shared by the screens in docs/.
 *
 * Where the API is:
 *   - Pages served from localhost/127.0.0.1 talk to http://localhost:8080 (the API's `local` profile).
 *   - ?api=https://your-api.example.com sets another address (remembered in this browser); ?api=off forgets it.
 *   - Anywhere else (e.g. the public Vercel site) the pages stay in demo mode and never contact the
 *     visitor's own machine.
 *
 * Sign-in: until Supabase is connected, the local API hands out demo tokens at /dev/token.
 */
(function () {
  const LOCAL_HOSTS = new Set(["localhost", "127.0.0.1"]);
  const TOKEN_KEY = "khabar.token";
  const API_KEY = "khabar.api";

  function safeGet(storage, key) {
    try { return storage.getItem(key); } catch { return null; }
  }
  function safeSet(storage, key, value) {
    try { value == null ? storage.removeItem(key) : storage.setItem(key, value); } catch { /* storage blocked */ }
  }

  function base() {
    const fromQuery = new URLSearchParams(location.search).get("api");
    if (fromQuery === "off") { safeSet(localStorage, API_KEY, null); return null; }
    if (fromQuery) { safeSet(localStorage, API_KEY, fromQuery.replace(/\/$/, "")); }
    const saved = safeGet(localStorage, API_KEY);
    if (saved) return saved;
    return LOCAL_HOSTS.has(location.hostname) ? "http://localhost:8080" : null;
  }

  const token = () => safeGet(sessionStorage, TOKEN_KEY);
  const signOut = () => safeSet(sessionStorage, TOKEN_KEY, null);

  class ApiError extends Error {
    constructor(status, message) { super(message); this.status = status; }
  }

  async function call(path, options = {}) {
    const root = base();
    if (!root) throw new ApiError(0, "No API configured (demo mode)");
    const headers = { "Content-Type": "application/json", ...(options.headers || {}) };
    if (token()) headers.Authorization = "Bearer " + token();
    let res;
    try {
      res = await fetch(root + path, { ...options, headers });
    } catch (e) {
      throw new ApiError(0, "The Khabar API is not reachable at " + root);
    }
    if (res.status === 401) { signOut(); throw new ApiError(401, "Your session has ended. Sign in again."); }
    if (!res.ok) {
      // The API sends its own sentence for people as {message}; fall back to the status code.
      let message = "The API answered " + res.status;
      try { const body = await res.json(); if (body && body.message) message = body.message; } catch { /* not JSON */ }
      throw new ApiError(res.status, message);
    }
    return res.status === 204 ? null : res.json();
  }

  /** Signs in as the demo doctor on a local API. Returns the /api/me profile, or null in demo mode. */
  async function signInAsDemoDoctor() {
    const root = base();
    if (!root) return null;
    try {
      const res = await fetch(root + "/dev/token?as=doctor", { method: "POST" });
      if (!res.ok) return null;
      const { token: fresh } = await res.json();
      safeSet(sessionStorage, TOKEN_KEY, fresh);
      return await call("/api/me");
    } catch {
      return null;
    }
  }

  window.KhabarApi = {
    base,
    enabled: () => base() !== null,
    signedIn: () => token() !== null,
    call,
    signInAsDemoDoctor,
    signOut,
    ApiError,
  };
})();
