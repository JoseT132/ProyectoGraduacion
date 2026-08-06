import torch
from ultralytics import YOLO

def main():
    # 1. Detectar aceleración por GPU (RTX 4070)
    device = 0 if torch.cuda.is_available() else "cpu"
    print(f"=== INICIANDO ENTRENAMIENTO DE CLASIFICACIÓN (GPU: {device}) ===")
    if device == 0:
        print(f"GPU activa: {torch.cuda.get_device_name(0)}")

    # 2. Cargar el modelo base preentrenado para clasificación
    model = YOLO("yolo11s-cls.pt")

    # 3. Iniciar el entrenamiento
    results = model.train(
        data="dataset_cls",    # Carpeta raíz que contiene train/ y val/
        epochs=40,             # 40 épocas son ideales para fine-tuning en clasificación
        imgsz=224,             # Resolución estándar y ultra eficiente para clasificadores
        batch=32,              
        device=device,
        workers=2,
        project="runs/classify",
        name="insect_yolov11_cls",
        exist_ok=True,
        pretrained=True,
        optimizer="AdamW",
        lr0=0.001,
        verbose=True
    )

    print("\n[OK] ¡Entrenamiento de clasificación completado!")
    print("Pesos finales guardados en: runs/classify/insect_yolov11_cls/weights/best.pt")

if __name__ == "__main__":
    main()