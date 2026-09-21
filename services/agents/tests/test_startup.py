import subprocess
import sys
from pathlib import Path

SERVICE_DIR = Path(__file__).resolve().parents[1]


def test_app_imports_the_way_the_dockerfile_starts_it():
    # The Dockerfile runs `uvicorn main:app` from the service folder,
    # so `main` must import as a top-level module, not only as part of a package.
    result = subprocess.run(
        [sys.executable, "-c", "import main; print(main.app.title)"],
        cwd=SERVICE_DIR,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr
    assert "Khabar" in result.stdout
