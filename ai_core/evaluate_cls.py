import argparse
import torch
from pathlib import Path
from ultralytics import YOLO


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="runs/classify/runs/classify/insect_yolov11n_cls_crops/weights/best.pt")
    parser.add_argument("--data", default="dataset_cls_crops")
    return parser.parse_args()


def main():
    args = parse_args()
    project_root = Path(__file__).parent.parent
    best = project_root / args.model
    data = project_root / "ai_core" / args.data

    if not best.exists():
        print(f"[ERROR] No se encontró {best}. Entrena primero con train_cls.py")
        return

    print(f"[INFO] Evaluando clasificador: {best}")
    model = YOLO(str(best))
    device = 0 if torch.cuda.is_available() else "cpu"
    metrics = model.val(data=str(data), split="test", imgsz=224, device=device)

    print("\n=== MÉTRICAS CLASIFICADOR (test) ===")
    print(f"Top-1 accuracy: {metrics.top1:.4f}")
    print(f"Top-5 accuracy: {metrics.top5:.4f}")


if __name__ == "__main__":
    main()
