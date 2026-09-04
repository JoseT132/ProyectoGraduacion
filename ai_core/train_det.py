import os
import torch
from pathlib import Path
from ultralytics import YOLO


def main():
    device = 0 if torch.cuda.is_available() else "cpu"
    print(f"=== INICIANDO ENTRENAMIENTO DE DETECCIÓN (GPU: {device}) ===")
    if device == 0:
        print(f"GPU activa: {torch.cuda.get_device_name(0)}")

    ai_core_dir = Path(__file__).parent.resolve()
    project_root = ai_core_dir.parent
    data_yaml = ai_core_dir / "dataset_det" / "data.yaml"
    project_dir = project_root / "runs" / "detect"
    project_dir.mkdir(parents=True, exist_ok=True)

    model = YOLO("yolo11n.pt")

    results = model.train(
        data=str(data_yaml),
        epochs=50,
        imgsz=640,
        batch=16,
        device=device,
        workers=2,
        project=str(project_dir),
        name="insect_yolov11_det",
        exist_ok=True,
        pretrained=True,
        optimizer="AdamW",
        lr0=0.001,
        verbose=True
    )

    save_dir = project_dir / "insect_yolov11_det" / "weights" / "best.pt"
    print("\n[OK] Entrenamiento de detección completado.")
    print(f"Pesos finales guardados en: {save_dir}")


if __name__ == "__main__":
    main()
