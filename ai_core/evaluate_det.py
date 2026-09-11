import torch
from pathlib import Path
from ultralytics import YOLO


def main():
    project_root = Path(__file__).parent.parent
    best = project_root / "runs" / "detect" / "insect_yolov11_det" / "weights" / "best.pt"
    data = project_root / "ai_core" / "dataset_det" / "data.yaml"

    if not best.exists():
        print(f"[ERROR] No se encontró {best}")
        return

    print(f"[INFO] Evaluando detector multi-clase: {best}")
    model = YOLO(str(best))
    device = 0 if torch.cuda.is_available() else "cpu"
    metrics = model.val(data=str(data), split="test", imgsz=640, device=device)

    print("\n=== MÉTRICAS DETECTOR MULTI-CLASE (test) ===")
    print(f"mAP@0.5:      {metrics.box.map50:.4f}")
    print(f"mAP@0.5:0.95: {metrics.box.map:.4f}")
    print(f"Precision:    {metrics.box.mp:.4f}")
    print(f"Recall:       {metrics.box.mr:.4f}")


if __name__ == "__main__":
    main()
