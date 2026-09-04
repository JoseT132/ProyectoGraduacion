import os
import glob
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
    raise FileNotFoundError("No se encontró el modelo de detección entrenado.")


def test_detection():
    model_path = find_det_model()
    print(f"[INFO] Cargando detector: {model_path}")
    model = YOLO(model_path)

    test_images = glob.glob(
        os.path.join(os.path.dirname(__file__), "dataset_det", "test", "images", "*.jpg")
    )
    if not test_images:
        print("[ERROR] No se encontraron imágenes de prueba.")
        return

    sample = test_images[0]
    print(f"[INFO] Imagen de prueba: {sample}")
    results = model.predict(source=sample, imgsz=640, conf=0.25, verbose=False)
    result = results[0]

    print("\nDetecciones encontradas:")
    for box in result.boxes:
        cls_id = int(box.cls)
        conf = float(box.conf)
        name = result.names[cls_id]
        print(f"  - {name}: {conf * 100:.2f}%")


if __name__ == "__main__":
    test_detection()
