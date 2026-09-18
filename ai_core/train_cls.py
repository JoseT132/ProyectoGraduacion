import argparse
import torch
from ultralytics import YOLO


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", default="dataset_cls_crops", help="Carpeta raíz del dataset de clasificación")
    parser.add_argument("--name", default="insect_yolov11s_cls_crops_v2", help="Nombre del experimento")
    parser.add_argument("--model", default="yolo11s-cls.pt", help="Pesos base YOLO-cls")
    parser.add_argument("--epochs", type=int, default=40)
    parser.add_argument("--patience", type=int, default=8)
    parser.add_argument("--imgsz", type=int, default=224)
    parser.add_argument("--batch", type=int, default=32)
    parser.add_argument("--workers", type=int, default=0, help="0 evita errores de multiprocessing en Windows")
    return parser.parse_args()


def main():
    args = parse_args()

    device = 0 if torch.cuda.is_available() else "cpu"
    print(f"=== INICIANDO ENTRENAMIENTO DE CLASIFICACIÓN (GPU: {device}) ===")
    if device == 0:
        print(f"GPU activa: {torch.cuda.get_device_name(0)}")

    model = YOLO(args.model)

    results = model.train(
        data=args.data,
        epochs=args.epochs,
        patience=args.patience,
        imgsz=args.imgsz,
        batch=args.batch,
        device=device,
        workers=args.workers,
        cache=True,
        project="runs/classify",
        name=args.name,
        exist_ok=True,
        pretrained=True,
        optimizer="AdamW",
        lr0=0.001,
        verbose=True
    )

    print("\n[OK] ¡Entrenamiento de clasificación completado!")
    print(f"Pesos finales guardados en: runs/classify/{args.name}/weights/best.pt")


if __name__ == "__main__":
    main()
