import { ApiError } from "@/lib/api"

/**
 * True when the API does not have this feature yet (an older deployment answers 404). Panels for features added
 * in Oct 2026 hide themselves then, instead of showing an error, until the API is redeployed.
 */
export function featureMissing(error: unknown) {
  return error instanceof ApiError && error.status === 404
}
