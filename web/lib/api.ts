import { clearToken, getToken } from "@/lib/session"

export const apiRoot = (process.env.NEXT_PUBLIC_KHABAR_API_URL || "http://localhost:8080").replace(/\/$/, "")

export class ApiError extends Error {
  constructor(message: string, readonly status = 0) { super(message); this.name = "ApiError" }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}, token: string | null = getToken()): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !(init.body instanceof FormData) && !headers.has("Content-Type")) headers.set("Content-Type", "application/json")
  if (token) headers.set("Authorization", `Bearer ${token}`)
  let response: Response
  try { response = await fetch(`${apiRoot}${path}`, { ...init, headers }) }
  catch { throw new ApiError(`Khabar cannot reach ${apiRoot}. Check that the local API is running.`) }
  if (response.status === 401) { clearToken(); throw new ApiError("Your secure session has ended. Please sign in again.", 401) }
  if (!response.ok) {
    let message = `Khabar could not complete that request (${response.status}).`
    try { const body = (await response.json()) as { message?: string }; if (body.message) message = body.message } catch { /* keep fallback */ }
    throw new ApiError(message, response.status)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
