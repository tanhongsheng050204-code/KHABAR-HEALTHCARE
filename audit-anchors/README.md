# Audit anchors

One file per day, written by `.github/workflows/audit-anchor.yml`: the newest position and hash of the
record-access chain on the public demo API. If an anchored hash ever stops matching the live chain, the log was
rewritten after that day. From the repository root, check every anchor with
`node services/scripts/check-audit-anchors.mjs`.
