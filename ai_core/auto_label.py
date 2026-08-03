import os
import glob
from ultralytics import YOLO
from PIL import Image

model = YOLO("yolo11n.pt")

RAW_DATA_DIR = "raw_data"
DATASET_DIR = "dataset"

# Mapa de carpetas de cada especie
SPECIES_CLASSES = {
    # Aleyrodidae
    "Bemisia_tabaci": 0, "Trialeurodes_vaporariorum": 1, "Aleurocanthus_woglumi": 2, "Aleurodicus_dispersus": 3, "Bemisia_argentifolii": 4,
    # Coccinellidae
    "Hippodamia_convergens": 5, "Cycloneda_sanguinea": 6, "Harmonia_axyridis": 7, "Coccinella_septempunctata": 8, "Coleomegilla_maculata": 9,
    # Aphididae
    "Myzus_persicae": 10, "Brevicoryne_brassicae": 11, "Aphis_gossypii": 12, "Macrosiphum_euphorbiae": 13, "Aphis_craccivora": 14,
    # Noctuidae
    "Spodoptera_frugiperda": 15, "Helicoverpa_zea": 16, "Trichoplusia_ni": 17, "Spodoptera_exigua": 18, "Agrotis_ipsilon": 19,
    # Pentatomidae
    "Nezara_viridula": 20, "Murgantia_histrionica": 21, "Halyomorpha_halys": 22, "Euschistus_servus": 23, "Chinavia_hilaris": 24
}

def auto_label_dataset():
    print("=== INICIANDO AUTO-ETIQUETADO POR CLI CON YOLOv11 ===")
    
    for species_folder, class_id in SPECIES_CLASSES.items():
        folder_path = os.path.join(RAW_DATA_DIR, species_folder)
        if not os.path.exists(folder_path):
            continue
            
        images = glob.glob(os.path.join(folder_path, "*.jpg"))
        print(f"\nProcesando {species_folder} (ID: {class_id}) - {len(images)} imágenes...")
        
        for img_path in images:
            filename = os.path.splitext(os.path.basename(img_path))[0]
            
            # Ejecutar inferencia con YOLO
            results = model(img_path, verbose=False)
            
            img_width, img_height = Image.open(img_path).size
            labels = []
            
            for result in results:
                boxes = result.boxes
                for box in boxes:
                    # Obtener coordenadas xywh normalizadas
                    x_center, y_center, w, h = box.xywhn[0].tolist()
                    labels.append(f"{class_id} {x_center:.6f} {y_center:.6f} {w:.6f} {h:.6f}\n")
            
            # Si YOLO preentrenado no detectó objeto, asumimos la caja completa del insecto centrado
            if not labels:
                labels.append(f"{class_id} 0.500000 0.500000 0.800000 0.800000\n")
            
            # Guardar temporalmente en carpeta procesada
            out_label_path = os.path.join(folder_path, f"{filename}.txt")
            with open(out_label_path, "w") as f:
                f.writelines(labels)
                
    print("\n[OK] Auto-etiquetado finalizado exitosamente.")

if __name__ == "__main__":
    auto_label_dataset()