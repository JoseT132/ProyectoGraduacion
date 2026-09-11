import torch
from pathlib import Path
from ultralytics import YOLO


def main():
    project_root = Path(__file__).parent.parent
    best = project_root / "runs" / "detect_binary" / "insect_yolov11s_bin" / "weights" / "best.pt"
    data = project_root / "ai_core" / "dataset_det_binary" / "data.yaml"

    if not best.exists():
        print(f"[ERROR] No se encontró {best}. Entrena primero con train_det_binary.py")
        return

    print(f"[INFO] Evaluando detector binario: {best}")
    model = YOLO(str(best))
    device = 0 if torch.cuda.is_available() else "cpu"
    metrics = model.val(data=str(data), split="test", imgsz=640, device=device)

    print("\n=== MÉTRICAS DETECTOR BINARIO (test) ===")
    print(f"mAP@0.5:      {metrics.box.map50:.4f}")
    print(f"mAP@0.5:0.95: {metrics.box.map:.4f}")
    print(f"Precision:    {metrics.box.mp:.4f}")
    print(f"Recall:       {metrics.box.mr:.4f}")


if __name__ == "__main__":
    main()
