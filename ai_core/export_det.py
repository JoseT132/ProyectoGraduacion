import os
import json
from pathlib import Path
from ultralytics import YOLO


def find_det_model():
    root = Path(__file__).parent.parent.resolve()
    candidates = [
        root / "runs" / "detect" / "insect_yolov11_det" / "weights" / "best.pt",
        root / "runs" / "detect" / "runs" / "detect" / "insect_yolov11_det" / "weights" / "best.pt",
    ]
    for c in candidates:
        if c.exists():
            return str(c)
    raise FileNotFoundError("No se encontró el modelo de detección entrenado. Ejecuta train_det.py primero.")


def main():
    model_path = find_det_model()
    print(f"[INFO] Cargando modelo de detección: {model_path}")

    model = YOLO(model_path)
    export_dir = Path(__file__).parent / "models_export"
    export_dir.mkdir(exist_ok=True)

    labels = {int(k): v for k, v in model.names.items()}
    with open(export_dir / "det_labels.json", "w", encoding="utf-8") as f:
        json.dump(labels, f, ensure_ascii=False, indent=2)

    print("[INFO] Exportando detector a ONNX...")
    onnx_path = model.export(format="onnx", imgsz=640, dynamic=False, simplify=True)
    print(f"[OK] ONNX: {onnx_path}")
    print("\n[OK] Exportación de detección finalizada.")


if __name__ == "__main__":
    main()
