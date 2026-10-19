"""
Copies the backend's production settings from the Vercel project (khabar-api) into the Hugging Face Space's
secrets, so both use the same database, keys and encryption key. Values are never printed; the temporary file
from `vercel env pull` is deleted straight away.

    python deploy/hf-space/copy_settings.py --space USER/khabar-api
"""
import argparse
import os
import subprocess
import tempfile
from pathlib import Path

SERVICES = Path(__file__).resolve().parent.parent.parent / "services"

# AGENTS_SERVICE_URL and SPRING_PROFILES_ACTIVE are set by the Space's Dockerfile, not copied.
KEYS = [
    "DB_POSTGRES_URL", "INTERNAL_SERVICE_KEY", "SUPABASE_JWT_SECRET", "SUPABASE_JWKS_URL", "FIELD_ENCRYPTION_KEY",
    "WEB_ALLOWED_ORIGINS", "WEB_APP_URL", "NEO4J_URI", "NEO4J_USERNAME", "NEO4J_PASSWORD",
    "GEMINI_API_KEY", "GROQ_API_KEY", "KHABAR_TELEGRAM_BOT_TOKEN", "KHABAR_TELEGRAM_WEBHOOK_SECRET",
]


def parse(text: str) -> dict:
    values = {}
    for line in text.splitlines():
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        value = value.strip()
        if len(value) >= 2 and value[0] == value[-1] == '"':
            value = value[1:-1].replace('\\n', '\n').replace('\\"', '"')
        values[key.strip()] = value
    return values


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--space", required=True)
    parser.add_argument("--extra-origin", help="Also allow this web origin (added to WEB_ALLOWED_ORIGINS)")
    args = parser.parse_args()

    handle, path = tempfile.mkstemp(suffix=".env")
    os.close(handle)
    try:
        subprocess.run("npx vercel env pull " + f'"{path}"' + " --environment=production --yes",
                       cwd=SERVICES, shell=True, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        values = parse(Path(path).read_text(encoding="utf-8"))
    finally:
        os.remove(path)

    from huggingface_hub import HfApi

    api = HfApi()
    copied, missing = [], []
    for key in KEYS:
        value = values.get(key, "")
        if key == "WEB_ALLOWED_ORIGINS" and args.extra_origin and args.extra_origin not in value:
            value = ",".join(v for v in [value, args.extra_origin] if v)
        if value:
            api.add_space_secret(args.space, key, value)
            copied.append(key)
        else:
            missing.append(key)
    print("Copied:", ", ".join(copied))
    if missing:
        print("Empty in Vercel (not copied):", ", ".join(missing))


if __name__ == "__main__":
    main()
