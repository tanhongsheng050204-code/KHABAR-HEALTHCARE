"""
Publishes Khabar's backend (services/api + services/agents) to a Hugging Face Docker Space.

    python deploy/hf-space/publish.py --stage-only          # just assemble deploy/hf-space/build/ (for a local docker build)
    python deploy/hf-space/publish.py --space USER/khabar-api   # assemble and upload (needs `hf auth login` first)

Secrets are never read or uploaded here; set them in the Space's settings.
"""
import argparse
import shutil
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO = HERE.parent.parent
SERVICES = REPO / "services"
BUILD = HERE / "build"
SKIP = shutil.ignore_patterns(".venv", "__pycache__", ".pytest_cache", "tests", ".env", ".env.*", "target", "*.pyc")


def stage() -> Path:
    if BUILD.exists():
        shutil.rmtree(BUILD)
    BUILD.mkdir()
    (BUILD / "api").mkdir()
    shutil.copy2(SERVICES / "api" / "pom.xml", BUILD / "api" / "pom.xml")
    shutil.copytree(SERVICES / "api" / "src" / "main", BUILD / "api" / "src" / "main", ignore=SKIP)
    shutil.copytree(SERVICES / "agents", BUILD / "agents", ignore=SKIP)
    for name in ("Dockerfile", "start.sh", "README.md"):
        shutil.copy2(HERE / name, BUILD / name)
    # Shell scripts must keep Unix line endings or the container cannot run them.
    script = BUILD / "start.sh"
    script.write_bytes(script.read_bytes().replace(b"\r\n", b"\n"))
    return BUILD


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--space", help="USER/SPACE_NAME on Hugging Face")
    parser.add_argument("--stage-only", action="store_true")
    args = parser.parse_args()
    folder = stage()
    print(f"Staged {folder}")
    if args.stage_only:
        return
    if not args.space:
        parser.error("--space is required unless --stage-only")
    from huggingface_hub import HfApi

    api = HfApi()
    api.create_repo(args.space, repo_type="space", space_sdk="docker", exist_ok=True)
    api.upload_folder(folder_path=str(folder), repo_id=args.space, repo_type="space",
                      commit_message="Deploy Khabar backend", delete_patterns=["*"])
    print(f"Uploaded: https://huggingface.co/spaces/{args.space}")


if __name__ == "__main__":
    main()
