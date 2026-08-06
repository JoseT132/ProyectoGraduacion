import os
import glob
import random
import shutil

RAW_DATA_DIR = "raw_data"
CLS_DATASET_DIR = "dataset_cls"

# Proporciones estándar: 70% Train, 20% Val, 10% Test
TRAIN_RATIO = 0.7
VAL_RATIO = 0.2

def prepare_cls_dataset():
    print("=== ESTRUCTURANDO DATASET PARA CLASIFICACIÓN DE PLAGAS ===")
    
    if not os.path.exists(RAW_DATA_DIR):
        print(f"❌ Error: No se encuentra la carpeta '{RAW_DATA_DIR}'.")
        return

    species_folders = [f for f in os.listdir(RAW_DATA_DIR) if os.path.isdir(os.path.join(RAW_DATA_DIR, f))]
    
    total_processed = 0
    
    for species in species_folders:
        src_folder = os.path.join(RAW_DATA_DIR, species)
        images = glob.glob(os.path.join(src_folder, "*.jpg"))
        
        if not images:
            continue
            
        random.seed(42)  # Semilla para que el split sea reproducible
        random.shuffle(images)
        
        n_imgs = len(images)
        n_train = int(n_imgs * TRAIN_RATIO)
        n_val = int(n_imgs * VAL_RATIO)
        
        splits = {
            "train": images[:n_train],
            "val": images[n_train:n_train + n_val],
            "test": images[n_train + n_val:]
        }
        
        for split_name, img_list in splits.items():
            dest_dir = os.path.join(CLS_DATASET_DIR, split_name, species)
            os.makedirs(dest_dir, exist_ok=True)
            
            for img_path in img_list:
                filename = os.path.basename(img_path)
                shutil.copy(img_path, os.path.join(dest_dir, filename))
                
        total_processed += n_imgs
        print(f"✓ {species}: {n_imgs} imágenes divididas (Train: {len(splits['train'])}, Val: {len(splits['val'])}, Test: {len(splits['test'])})")

    print(f"\n[OK] ¡Éxito! {total_processed} imágenes organizadas en '{CLS_DATASET_DIR}/'")

if __name__ == "__main__":
    prepare_cls_dataset()