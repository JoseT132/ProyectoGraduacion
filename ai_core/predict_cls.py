import os
import glob
from ultralytics import YOLO

# Obtener la ruta base del proyecto (subir un nivel desde ai_core)
CURRENT_DIR = os.path.dirname(os.path.abspath(__file__))
PROJECT_ROOT = os.path.abspath(os.path.join(CURRENT_DIR, ".."))

# Ruta exacta donde el entrenamiento guardó los pesos finales
MODEL_PATH = os.path.join(
    PROJECT_ROOT, 
    "runs", "classify", "runs", "classify", "insect_yolov11_cls", "weights", "best.pt"
)

# Respaldo en caso de que la ruta no tenga la carpeta duplicada
if not os.path.exists(MODEL_PATH):
    MODEL_PATH = os.path.join(
        PROJECT_ROOT, 
        "runs", "classify", "insect_yolov11_cls", "weights", "best.pt"
    )

def test_inference():
    if not os.path.exists(MODEL_PATH):
        print(f"❌ Error: No se encuentra el modelo.")
        print(f"Buscado en: {MODEL_PATH}")
        return

    model = YOLO(MODEL_PATH)

    # Buscar una imagen de prueba en dataset_cls/test
    test_images = glob.glob(os.path.join(CURRENT_DIR, "dataset_cls", "test", "*", "*.jpg"))
    
    if not test_images:
        print("❌ No se encontraron imágenes en dataset_cls/test/")
        return

    # Seleccionar la primera imagen disponible
    sample_img = test_images[0]
    expected_species = sample_img.split(os.sep)[-2]  # Obtener el nombre de la carpeta real

    print("\n==================================================")
    print("      PRUEBA DE INFERENCIA EN TIEMPO REAL         ")
    print("==================================================")
    print(f"Imagen evaluada : {os.path.basename(sample_img)}")
    print(f"Especie Real    : {expected_species}")
    print("--------------------------------------------------")

    # Ejecutar predicción
    results = model.predict(source=sample_img, imgsz=224, verbose=False)[0]

    # Extraer el Top 5 de predicciones
    top5_probs = results.probs.top5
    top5_conf = results.probs.top5conf.tolist()

    print("\nDIAGNÓSTICO DE LA IA (Top 5 Diagnósticos):")
    for rank, (class_idx, conf) in enumerate(zip(top5_probs, top5_conf), 1):
        class_name = results.names[class_idx]
        print(f"  {rank}. {class_name:<30} -> {conf * 100:.2f}% de certeza")
    print("==================================================\n")

if __name__ == "__main__":
    test_inference()