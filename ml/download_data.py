"""Download the Kaggle "Credit Risk Dataset" (laotse/credit-risk-dataset) into ml/data/.

Usage:  python download_data.py

The dataset is public; Kaggle serves it without an API token. The CSV's SHA-256
is checked so training always runs on exactly the same data.
"""
import hashlib
import io
import sys
import urllib.request
import zipfile
from pathlib import Path

URL = "https://www.kaggle.com/api/v1/datasets/download/laotse/credit-risk-dataset"
FILENAME = "credit_risk_dataset.csv"
SHA256 = "ce3c6d2167717bf1627d1c0c81cbccd28323cd4aa7b96d542599366d5ff6aac8"

DATA_DIR = Path(__file__).resolve().parent / "data"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def main() -> int:
    target = DATA_DIR / FILENAME
    if target.exists() and sha256(target.read_bytes()) == SHA256:
        print(f"Already present and verified: {target}")
        return 0

    print(f"Downloading {URL} ...")
    request = urllib.request.Request(URL, headers={"User-Agent": "risklens-ml/1.0"})
    with urllib.request.urlopen(request, timeout=120) as response:
        archive = response.read()

    with zipfile.ZipFile(io.BytesIO(archive)) as zf:
        csv_bytes = zf.read(FILENAME)

    digest = sha256(csv_bytes)
    if digest != SHA256:
        print(f"Checksum mismatch: expected {SHA256}, got {digest}", file=sys.stderr)
        return 1

    DATA_DIR.mkdir(parents=True, exist_ok=True)
    target.write_bytes(csv_bytes)
    print(f"Saved {target} ({len(csv_bytes):,} bytes, checksum OK)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
