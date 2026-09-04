import os
import glob
import random
import shutil
import yaml

RAW_DATA_DIR = "raw_data"
DET_DATASET_DIR = "dataset_det"

TRAIN_RATIO = 0.7
VAL_RATIO = 0.2

SPECIES_LIST = [
    "Bemisia tabaci", "Trialeurodes vaporariorum", "Aleurocanthus woglumi", "Aleurodicus dispersus", "Bemisia argentifolii",
    "Hippodamia convergens", "Cycloneda sanguinea", "Harmonia axyridis", "Coccinella septempunctata", "Coleomegilla maculata",
    "Myzus persicae", "Brevicoryne brassicae", "Aphis gossypii", "Macrosiphum euphorbiae", "Aphis craccivora",
    "Spodoptera frugiperda", "Helicoverpa zea", "Trichoplusia ni", "Spodoptera exigua", "Agrotis ipsilon",
    "Nezara viridula", "Murgantia histrionica", "Halyomorpha halys", "Euschistus servus", "Chinavia hilaris"
]


def prepare_det_dataset():
    print("=== ESTRUCTURANDO DATASET PARA DETECCIÓN DE PLAGAS ===")

    if not os.path.exists(RAW_DATA_DIR):
        print(f"[ERROR] No se encuentra la carpeta '{RAW_DATA_DIR}'.")
        return

    species_names = [s.replace(" ", "_") for s in SPECIES_LIST]
    os.makedirs(DET_DATASET_DIR, exist_ok=True)

    total_images = 0

    for idx, species in enumerate(species_names):
        src_folder = os.path.join(RAW_DATA_DIR, species)
        if not os.path.isdir(src_folder):
            print(f"[WARN] Saltando {species}: carpeta no encontrada.")
            continue

        images = sorted(glob.glob(os.path.join(src_folder, "*.jpg")))
        if not images:
            continue

        random.seed(42)
        random.shuffle(images)

        n = len(images)
        n_train = int(n * TRAIN_RATIO)
        n_val = int(n * VAL_RATIO)

        splits = {
            "train": images[:n_train],
            "val": images[n_train:n_train + n_val],
            "test": images[n_train + n_val:]
        }

        for split_name, img_list in splits.items():
            img_dir = os.path.join(DET_DATASET_DIR, split_name, "images")
            lbl_dir = os.path.join(DET_DATASET_DIR, split_name, "labels")
            os.makedirs(img_dir, exist_ok=True)
            os.makedirs(lbl_dir, exist_ok=True)

            for img_path in img_list:
                filename = os.path.basename(img_path)
                base, _ = os.path.splitext(filename)

                src_label = os.path.join(src_folder, f"{base}.txt")
                if not os.path.exists(src_label):
                    continue

                shutil.copy(img_path, os.path.join(img_dir, filename))

                with open(src_label, "r") as f:
                    lines = f.readlines()

                out_label_path = os.path.join(lbl_dir, f"{base}.txt")
                with open(out_label_path, "w") as f:
                    for line in lines:
                        parts = line.strip().split()
                        if len(parts) != 5:
                            continue
                        f.write(f"{idx} {' '.join(parts[1:])}\n")

        total_images += n
        print(f"[OK] {species}: {n} imágenes (Train: {len(splits['train'])}, Val: {len(splits['val'])}, Test: {len(splits['test'])})")

    data_yaml = {
        "path": os.path.abspath(DET_DATASET_DIR).replace("\\", "/"),
        "train": "train/images",
        "val": "val/images",
        "test": "test/images",
        "nc": 25,
        "names": [s.replace(" ", "_") for s in SPECIES_LIST]
    }

    yaml_path = os.path.join(DET_DATASET_DIR, "data.yaml")
    with open(yaml_path, "w", encoding="utf-8") as f:
        yaml.dump(data_yaml, f, sort_keys=False, allow_unicode=True)

    print(f"\n[OK] {total_images} imágenes organizadas en '{DET_DATASET_DIR}/'")
    print(f"[OK] Archivo de configuración guardado en '{yaml_path}'")


if __name__ == "__main__":
    prepare_det_dataset()
