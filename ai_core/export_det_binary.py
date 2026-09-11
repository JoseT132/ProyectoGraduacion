from pathlib import Path
from ultralytics import YOLO


def main():
    project_root = Path(__file__).parent.parent
    best = project_root / "runs" / "detect_binary" / "insect_yolov11s_bin" / "weights" / "best.pt"
    if not best.exists():
        print(f"[ERROR] No se encontró {best}. Entrena primero con train_det_binary.py")
        return

    print(f"[INFO] Exportando detector binario desde {best}...")
    model = YOLO(str(best))
    model.export(format="onnx", imgsz=640, half=False, simplify=True)
    print("[OK] Exportación completada.")


if __name__ == "__main__":
    main()
