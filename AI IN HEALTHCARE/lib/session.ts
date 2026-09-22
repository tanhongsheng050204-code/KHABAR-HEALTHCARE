export const TOKEN_KEY = "khabar.web.token"

export function getToken() {
  if (typeof window === "undefined") return null
  try { return window.sessionStorage.getItem(TOKEN_KEY) } catch { return null }
}

export function setToken(token: string) { window.sessionStorage.setItem(TOKEN_KEY, token) }
export function clearToken() { if (typeof window !== "undefined") window.sessionStorage.removeItem(TOKEN_KEY) }
