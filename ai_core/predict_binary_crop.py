import json
import sys
import argparse
import numpy as np
from pathlib import Path
from PIL import Image
from ultralytics import YOLO


def preprocess_cls(image: Image.Image):
    """Preprocesa imagen PIL a [1, 3, 224, 224] normalizado."""
    img = image.resize((224, 224))
    arr = np.array(img).astype(np.float32) / 255.0
    arr = arr.transpose(2, 0, 1)  # HWC -> CHW
    return np.expand_dims(arr, 0)


def predict(image_path: str, det_path: str, cls_path: str, labels_path: str):
    img_path = Path(image_path)
    if not img_path.exists():
        print(f"[ERROR] No existe {image_path}")
        return

    with open(labels_path) as f:
        labels = json.load(f)

    print("[INFO] Cargando detector binario...")
    det = YOLO(det_path)
    print("[INFO] Cargando clasificador...")
    cls = YOLO(cls_path)

    results = det(str(img_path), imgsz=640, conf=0.25, iou=0.7)
    boxes = results[0].boxes

    if boxes is None or len(boxes) == 0:
        print("[INFO] No se detectó ningún insecto.")
        return

    full_img = Image.open(img_path).convert("RGB")
    width, height = full_img.size

    for i, box in enumerate(boxes):
        x1, y1, x2, y2 = box.xyxy[0].cpu().numpy().astype(int)
        conf = float(box.conf[0])

        # Recortar dentro de la imagen
        x1, y1 = max(0, x1), max(0, y1)
        x2, y2 = min(width, x2), min(height, y2)
        crop = full_img.crop((x1, y1, x2, y2))

        # Clasificar recorte
        cls_res = cls.predict(crop, imgsz=224, verbose=False)[0]
        top_idx = int(cls_res.probs.top1)
        top_conf = float(cls_res.probs.top1conf)
        species = labels.get(str(top_idx), "unknown")

        print(f"Insecto {i+1}: box={x1},{y1},{x2},{y2} conf={conf:.3f} -> {species} ({top_conf*100:.1f}%)")

    print(f"\nTotal de detecciones: {len(boxes)}")


def main():
    parser = argparse.ArgumentParser(description="Detector binario + clasificador")
    parser.add_argument("--image", required=True, help="Ruta de la imagen")
    parser.add_argument("--det", default="runs/detect_binary/insect_yolov11s_bin/weights/best.pt", help="Detector binario")
    parser.add_argument("--cls", default="runs/classify/runs/classify/insect_yolov11_cls/weights/best.pt", help="Clasificador")
    parser.add_argument("--labels", default="ai_core/models_export/cls_labels.json", help="JSON de etiquetas")
    args = parser.parse_args()
    predict(args.image, args.det, args.cls, args.labels)


if __name__ == "__main__":
    main()
