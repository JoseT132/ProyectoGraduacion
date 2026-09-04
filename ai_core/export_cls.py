import os
import json
from pathlib import Path
from ultralytics import YOLO


def find_cls_model():
    ai_core_dir = Path(__file__).parent.resolve()
    candidates = [
        ai_core_dir.parent / "runs" / "classify" / "insect_yolov11_cls" / "weights" / "best.pt",
        ai_core_dir.parent / "runs" / "classify" / "runs" / "classify" / "insect_yolov11_cls" / "weights" / "best.pt",
    ]
    for c in candidates:
        if c.exists():
            return str(c)
    raise FileNotFoundError("No se encontró el modelo de clasificación entrenado.")


def main():
    model_path = find_cls_model()
    print(f"[INFO] Cargando modelo: {model_path}")

    model = YOLO(model_path)
    export_dir = Path(__file__).parent / "models_export"
    export_dir.mkdir(exist_ok=True)

    labels = {int(k): v for k, v in model.names.items()}
    with open(export_dir / "cls_labels.json", "w", encoding="utf-8") as f:
        json.dump(labels, f, ensure_ascii=False, indent=2)

    print("[INFO] Exportando a ONNX...")
    onnx_path = model.export(format="onnx", imgsz=224, dynamic=False, simplify=True)
    print(f"[OK] ONNX: {onnx_path}")

    print("[INFO] Exportando a TFLite...")
    try:
        tflite_path = model.export(format="tflite", imgsz=224)
        print(f"[OK] TFLite: {tflite_path}")
    except Exception as e:
        print(f"[WARN] No se pudo exportar a TFLite: {e}")

    print("\n[OK] Exportación de clasificación finalizada.")
    print(f"Etiquetas guardadas en: {export_dir / 'cls_labels.json'}")


if __name__ == "__main__":
    main()
