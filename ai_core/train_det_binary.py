import os
import torch
from pathlib import Path
from ultralytics import YOLO


def main():
    device = 0 if torch.cuda.is_available() else "cpu"
    print(f"=== ENTRENAMIENTO BINARIO (GPU: {device}) ===")

    ai_core_dir = Path(__file__).parent.resolve()
    data_yaml = ai_core_dir / "dataset_det_binary" / "data.yaml"
    project_dir = ai_core_dir.parent / "runs" / "detect_binary"
    project_dir.mkdir(parents=True, exist_ok=True)

    model = YOLO("yolo11s.pt")

    results = model.train(
        data=str(data_yaml),
        epochs=100,
        imgsz=640,
        batch=16,
        device=device,
        workers=2,
        project=str(project_dir),
        name="insect_yolov11s_bin",
        exist_ok=True,
        pretrained=True,
        optimizer="AdamW",
        lr0=0.001,
        verbose=True,
    )

    best = project_dir / "insect_yolov11s_bin" / "weights" / "best.pt"
    print(f"\n[OK] Entrenamiento binario completado: {best}")


if __name__ == "__main__":
    main()
