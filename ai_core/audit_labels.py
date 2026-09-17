import argparse
import json
import os
import random
import glob
import statistics
from pathlib import Path
from PIL import Image, ImageDraw

AI_CORE = Path(__file__).resolve().parent


def parse_args():
    parser = argparse.ArgumentParser(description="Audita etiquetas YOLO de un dataset")
    parser.add_argument("--dataset", default="dataset_det_binary", help="Carpeta del dataset YOLO")
    parser.add_argument("--split", default="train", help="train/val/test")
    parser.add_argument("--samples", type=int, default=24, help="Muestras a visualizar")
    parser.add_argument("--out_dir", default="audit_samples", help="Carpeta de salida")
    parser.add_argument("--seed", type=int, default=42)
    return parser.parse_args()


def parse_label_file(label_path: Path):
    boxes = []
    if not label_path.exists():
        return boxes
    with open(label_path, "r", encoding="utf-8") as f:
        for line in f:
            parts = line.strip().split()
            if len(parts) != 5:
                continue
            try:
                cls = int(parts[0])
                xc, yc, bw, bh = map(float, parts[1:])
            except ValueError:
                continue
            boxes.append({"cls": cls, "xc": xc, "yc": yc, "w": bw, "h": bh})
    return boxes


def draw_box(img_path: Path, label_path: Path, out_path: Path):
    img = Image.open(img_path).convert("RGB")
    draw = ImageDraw.Draw(img)
    w, h = img.size
    boxes = parse_label_file(label_path)
    for box in boxes:
        x1 = (box["xc"] - box["w"] / 2) * w
        y1 = (box["yc"] - box["h"] / 2) * h
        x2 = (box["xc"] + box["w"] / 2) * w
        y2 = (box["yc"] + box["h"] / 2) * h
        draw.rectangle([x1, y1, x2, y2], outline="red", width=3)
        draw.text((x1, y1 - 12), str(box["cls"]), fill="red")
    out_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(out_path)
    return len(boxes)


def compute_stats(dataset_dir: Path, split: str):
    img_dir = dataset_dir / split / "images"
    lbl_dir = dataset_dir / split / "labels"
    images = sorted(img_dir.glob("*.jpg"))
    if not images:
        return None

    all_boxes = []
    labels_per_image = []
    for img in images:
        label_path = lbl_dir / f"{img.stem}.txt"
        boxes = parse_label_file(label_path)
        labels_per_image.append(len(boxes))
        all_boxes.extend(boxes)

    if not all_boxes:
        return {
            "images": len(images),
            "labels": 0,
            "mean_labels_per_image": 0.0,
        }

    stats = {
        "images": len(images),
        "labels": len(all_boxes),
        "mean_labels_per_image": sum(labels_per_image) / len(labels_per_image),
        "xc_mean": statistics.mean(b["xc"] for b in all_boxes),
        "yc_mean": statistics.mean(b["yc"] for b in all_boxes),
        "w_mean": statistics.mean(b["w"] for b in all_boxes),
        "h_mean": statistics.mean(b["h"] for b in all_boxes),
        "xc_std": statistics.stdev(b["xc"] for b in all_boxes) if len(all_boxes) > 1 else 0.0,
        "yc_std": statistics.stdev(b["yc"] for b in all_boxes) if len(all_boxes) > 1 else 0.0,
        "w_std": statistics.stdev(b["w"] for b in all_boxes) if len(all_boxes) > 1 else 0.0,
        "h_std": statistics.stdev(b["h"] for b in all_boxes) if len(all_boxes) > 1 else 0.0,
    }
    return stats


def main():
    args = parse_args()
    dataset_dir = AI_CORE / args.dataset
    img_dir = dataset_dir / args.split / "images"
    lbl_dir = dataset_dir / args.split / "labels"
    out_dir = AI_CORE / args.out_dir / args.dataset / args.split

    if not img_dir.exists():
        print(f"[ERROR] No existe {img_dir}")
        return

    random.seed(args.seed)
    all_images = sorted(img_dir.glob("*.jpg"))
    samples = random.sample(all_images, min(args.samples, len(all_images)))

    print(f"[INFO] Dataset: {dataset_dir}")
    print(f"[INFO] Split: {args.split}, imágenes totales: {len(all_images)}")
    print(f"[INFO] Guardando muestras en: {out_dir}")

    for img_path in samples:
        label_path = lbl_dir / f"{img_path.stem}.txt"
        out_path = out_dir / f"{img_path.stem}_boxes.jpg"
        count = draw_box(img_path, label_path, out_path)
        print(f"  {img_path.name}: {count} box(es)")

    stats = compute_stats(dataset_dir, args.split)
    if stats:
        print("\n=== ESTADÍSTICAS DE ETIQUETAS ===")
        for k, v in stats.items():
            print(f"{k}: {v}")
        stats_path = AI_CORE / args.out_dir / f"{args.dataset}_{args.split}_stats.json"
        stats_path.parent.mkdir(parents=True, exist_ok=True)
        with open(stats_path, "w", encoding="utf-8") as f:
            json.dump(stats, f, indent=2)
        print(f"\n[OK] Estadísticas guardadas en {stats_path}")

        # Alerta de etiquetas sospechosas
        if stats["labels"] > 0:
            if stats["w_std"] < 0.15 and stats["h_std"] < 0.15:
                print("\n[WARN] Las cajas tienen variación muy baja; parecen genéricas.")
            if stats["xc_std"] < 0.15 and stats["yc_std"] < 0.15:
                print("[WARN] Los centros están muy concentrados; podrían ser placeholders.")


if __name__ == "__main__":
    main()
