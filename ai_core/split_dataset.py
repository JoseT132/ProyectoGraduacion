import os
import glob
import random
import shutil

RAW_DATA_DIR = "raw_data"
DATASET_DIR = "dataset"

# 70% Entrenamiento, 20% Validación, 10% Prueba
TRAIN_RATIO = 0.7
VAL_RATIO = 0.2

def split_dataset():
    print("=== REPARTIENDO DATASET EN TRAIN / VALID / TEST ===")
    
    # Crear carpetas si no existen
    for split in ["train", "valid", "test"]:
        os.makedirs(os.path.join(DATASET_DIR, split, "images"), exist_ok=True)
        os.makedirs(os.path.join(DATASET_DIR, split, "labels"), exist_ok=True)
        
    species_folders = [f for f in os.listdir(RAW_DATA_DIR) if os.path.isdir(os.path.join(RAW_DATA_DIR, f))]
    
    total_imgs = 0
    for species in species_folders:
        species_path = os.path.join(RAW_DATA_DIR, species)
        images = glob.glob(os.path.join(species_path, "*.jpg"))
        
        random.seed(42)  # Semilla para repetibilidad
        random.shuffle(images)
        
        n_imgs = len(images)
        total_imgs += n_imgs
        
        n_train = int(n_imgs * TRAIN_RATIO)
        n_val = int(n_imgs * VAL_RATIO)
        
        train_imgs = images[:n_train]
        val_imgs = images[n_train:n_train + n_val]
        test_imgs = images[n_train + n_val:]
        
        splits = [("train", train_imgs), ("valid", val_imgs), ("test", test_imgs)]
        
        for split_name, img_list in splits:
            for img_path in img_list:
                filename = os.path.splitext(os.path.basename(img_path))[0]
                label_path = os.path.join(species_path, f"{filename}.txt")
                
                dest_img = os.path.join(DATASET_DIR, split_name, "images", f"{filename}.jpg")
                dest_label = os.path.join(DATASET_DIR, split_name, "labels", f"{filename}.txt")
                
                shutil.copy(img_path, dest_img)
                if os.path.exists(label_path):
                    shutil.copy(label_path, dest_label)

    print(f"\n[OK] {total_imgs} imágenes y etiquetas organizadas en '{DATASET_DIR}/'")

if __name__ == "__main__":
    split_dataset()