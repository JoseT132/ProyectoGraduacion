import argparse
import hashlib
import json
from pathlib import Path

import torch
from PIL import Image
from ultralytics import YOLO

AI_CORE = Path(__file__).resolve().parent
RAW_DATA = AI_CORE / "raw_data"
REPORT_PATH = AI_CORE / "curation_report.json"

PLACEHOLDER_CENTER_TOL = 0.02
PLACEHOLDER_SIZE_TOL = 0.05


def is_placeholder_box(xc, yc, w, h):
    return (
        abs(xc - 0.5) <= PLACEHOLDER_CENTER_TOL
        and abs(yc - 0.5) <= PLACEHOLDER_CENTER_TOL
        and abs(w - 0.8) <= PLACEHOLDER_SIZE_TOL
        and abs(h - 0.8) <= PLACEHOLDER_SIZE_TOL
    )


def parse_label(label_path: Path):
    boxes = []
    if not label_path.exists():
        return boxes
    with open(label_path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) != 5:
                continue
            try:
                xc, yc, w, h = map(float, parts[1:])
            except ValueError:
                continue
            boxes.append((xc, yc, w, h))
    return boxes


def image_sha1(path: Path) -> str:
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def image_is_valid(path: Path) -> bool:
    try:
        with Image.open(path) as img:
            img.verify()
        return True
    except Exception:
        return False


def predict_box(model, img_path: Path):
    results = model.predict(str(img_path), imgsz=640, conf=0.25, verbose=False)[0]
    if results.boxes is None or len(results.boxes) == 0:
        return None
    best = max(results.boxes, key=lambda b: float(b.conf[0]))
    xc, yc, w, h = best.xywhn[0].tolist()
    return xc, yc, w, h


def main():
    parser = argparse.ArgumentParser(description="Curación de etiquetas YOLO en raw_data")
    parser.add_argument("--model", default="../runs/detect_binary/insect_yolov11n_bin/weights/best.pt")
    parser.add_argument("--dry-run", action="store_true", help="Solo reporta, no modifica")
    parser.add_argument("--no-relabel", action="store_true", help="No pseudo-etiquetar placeholders")
    args = parser.parse_args()

    model_path = Path(args.model)
    if not model_path.is_absolute():
        model_path = (AI_CORE / args.model).resolve()

    model = None
    if not args.no_relabel:
        if not model_path.exists():
            print(f"[ERROR] No se encontró el detector: {model_path}")
            return
        device = 0 if torch.cuda.is_available() else "cpu"
        model = YOLO(str(model_path))
        print(f"[INFO] Pseudo-etiquetado con {model_path.name} (device={device})")

    report = {}
    totals = {"images": 0, "duplicates": 0, "corrupt": 0, "placeholder": 0,
              "relabeled": 0, "unlabeled": 0, "real": 0}

    for species_dir in sorted(RAW_DATA.iterdir()):
        if not species_dir.is_dir():
            continue
        species = species_dir.name
        stats = {"images": 0, "duplicates": 0, "corrupt": 0, "placeholder": 0,
                 "relabeled": 0, "unlabeled": 0, "real": 0}
        seen_hashes = set()

        for img_path in sorted(species_dir.glob("*.jpg")):
            stats["images"] += 1
            label_path = img_path.with_suffix(".txt")

            if not image_is_valid(img_path):
                stats["corrupt"] += 1
                if not args.dry_run:
                    img_path.unlink()
                    if label_path.exists():
                        label_path.unlink()
                continue

            digest = image_sha1(img_path)
            if digest in seen_hashes:
                stats["duplicates"] += 1
                if not args.dry_run:
                    img_path.unlink()
                    if label_path.exists():
                        label_path.unlink()
                continue
            seen_hashes.add(digest)

            boxes = parse_label(label_path)
            if not boxes:
                stats["placeholder"] += 1
            elif all(is_placeholder_box(*b) for b in boxes):
                stats["placeholder"] += 1
            else:
                stats["real"] += 1
                continue

            if model is None:
                continue

            box = predict_box(model, img_path)
            if box is None:
                stats["unlabeled"] += 1
                if not args.dry_run and label_path.exists():
                    label_path.unlink()
                continue

            stats["relabeled"] += 1
            if not args.dry_run:
                xc, yc, w, h = box
                with open(label_path, "w", encoding="utf-8") as f:
                    f.write(f"0 {xc:.6f} {yc:.6f} {w:.6f} {h:.6f}\n")

        report[species] = stats
        for k, v in stats.items():
            totals[k] += v
        print(f"[{species}] {stats}")

    print("\n=== TOTALES ===")
    for k, v in totals.items():
        print(f"{k}: {v}")

    report["_totals"] = totals
    report["_dry_run"] = args.dry_run
    with open(REPORT_PATH, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2)
    print(f"\n[OK] Reporte en {REPORT_PATH}")


if __name__ == "__main__":
    main()
