import io
from pathlib import Path
from PIL import Image
from ultralytics import YOLO

_MODEL = None


def find_cls_model() -> Path:
    root = Path(__file__).parents[3].resolve()
    candidates = [
        root / "runs" / "classify" / "insect_yolov11_cls" / "weights" / "best.pt",
        root / "runs" / "classify" / "runs" / "classify" / "insect_yolov11_cls" / "weights" / "best.pt",
    ]
    for c in candidates:
        if c.exists():
            return c
    raise FileNotFoundError("No se encontró el modelo de clasificación entrenado.")


def get_model() -> YOLO:
    global _MODEL
    if _MODEL is None:
        path = find_cls_model()
        _MODEL = YOLO(str(path))
    return _MODEL


def predict_image(image_bytes: bytes):
    image = Image.open(io.BytesIO(image_bytes)).convert("RGB")
    model = get_model()
    results = model.predict(image, imgsz=224, verbose=False)[0]

    top5_idx = results.probs.top5
    top5_conf = results.probs.top5conf.tolist()

    predictions = []
    for rank, (idx, conf) in enumerate(zip(top5_idx, top5_conf), start=1):
        name = results.names[idx]
        predictions.append({
            "rank": rank,
            "species": name.replace("_", " "),
            "slug": name,
            "confidence": round(float(conf), 4)
        })

    return predictions
