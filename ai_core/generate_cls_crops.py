import os
import glob
import shutil
from pathlib import Path
from PIL import Image

AI_CORE = Path(__file__).resolve().parent
CLS_DIR = AI_CORE / "dataset_cls"
DET_DIR = AI_CORE / "dataset_det"
OUT_DIR = AI_CORE / "dataset_cls_crops"


def find_label(stem: str) -> Path | None:
    for split in ["train", "val", "test"]:
        label = DET_DIR / split / "labels" / f"{stem}.txt"
        if label.exists():
            return label
    return None


def largest_box(label_path: Path):
    best = None
    best_area = 0.0
    with open(label_path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) != 5:
                continue
            try:
                xc, yc, w, h = map(float, parts[1:])
            except ValueError:
                continue
            area = w * h
            if area > best_area:
                best_area = area
                best = (xc, yc, w, h)
    return best


def crop_with_box(img_path: Path, label_path: Path | None, out_path: Path):
    img = Image.open(img_path).convert("RGB")
    if label_path is not None:
        box = largest_box(label_path)
        if box is not None:
            xc, yc, w, h = box
            width, height = img.size
            x1 = max(0, int((xc - w / 2) * width))
            y1 = max(0, int((yc - h / 2) * height))
            x2 = min(width, int((xc + w / 2) * width))
            y2 = min(height, int((yc + h / 2) * height))
            if x2 > x1 and y2 > y1:
                img = img.crop((x1, y1, x2, y2))
    out_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(out_path)


def main():
    if not CLS_DIR.exists():
        print(f"[ERROR] No existe {CLS_DIR}")
        return

    total = 0
    no_label = 0
    for split in ["train", "val", "test"]:
        split_dir = CLS_DIR / split
        if not split_dir.exists():
            continue
        for species_dir in split_dir.iterdir():
            if not species_dir.is_dir():
                continue
            out_species = OUT_DIR / split / species_dir.name
            for img_path in species_dir.glob("*.jpg"):
                label = find_label(img_path.stem)
                if label is None:
                    no_label += 1
                crop_with_box(img_path, label, out_species / img_path.name)
                total += 1

    print(f"[OK] {total} imágenes procesadas en {OUT_DIR}")
    print(f"[INFO] {no_label} imágenes sin etiqueta (se guardaron completas)")


if __name__ == "__main__":
    main()
