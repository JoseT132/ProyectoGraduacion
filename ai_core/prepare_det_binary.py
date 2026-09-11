import os
import glob
import shutil
import yaml
from pathlib import Path

DET_DIR = "dataset_det"
BINARY_DIR = "dataset_det_binary"


def link_or_copy_image(src: Path, dst: Path):
    try:
        os.link(str(src), str(dst))
    except Exception:
        shutil.copy2(str(src), str(dst))


def prepare_binary_dataset():
    print("=== PREPARANDO DATASET BINARIO (insecto vs fondo) ===")

    bin_root = Path(BINARY_DIR)
    bin_root.mkdir(exist_ok=True)

    for split in ["train", "val", "test"]:
        img_src = Path(DET_DIR) / split / "images"
        lbl_src = Path(DET_DIR) / split / "labels"

        if not img_src.exists() or not lbl_src.exists():
            print(f"[WARN] No se encontró {img_src} o {lbl_src}; saltando split '{split}'.")
            continue

        img_dst = bin_root / split / "images"
        lbl_dst = bin_root / split / "labels"
        img_dst.mkdir(parents=True, exist_ok=True)
        lbl_dst.mkdir(parents=True, exist_ok=True)

        # Limpiar anteriores para evitar mezclas
        for old in img_dst.glob("*"):
            if old.is_dir():
                shutil.rmtree(old)
            else:
                old.unlink()

        # Enlaces duros o copia de imágenes
        images = sorted(img_src.glob("*.jpg"))
        for img_path in images:
            link_or_copy_image(img_path, img_dst / img_path.name)

        # Labels con clase 0 (insecto)
        for txt_path in sorted(lbl_src.glob("*.txt")):
            base = txt_path.name
            with open(txt_path, "r") as f:
                lines = f.readlines()

            new_lines = []
            for line in lines:
                parts = line.strip().split()
                if len(parts) == 5:
                    new_lines.append(f"0 {' '.join(parts[1:])}\n")

            with open(lbl_dst / base, "w") as f:
                f.writelines(new_lines)

        print(f"[OK] {split}: {len(images)} imágenes, {len(list(lbl_dst.glob('*.txt')))} labels binarios.")

    data_yaml = {
        "path": str(bin_root.resolve()).replace("\\", "/"),
        "train": "train/images",
        "val": "val/images",
        "test": "test/images",
        "nc": 1,
        "names": ["insect"],
    }

    yaml_path = bin_root / "data.yaml"
    with open(yaml_path, "w", encoding="utf-8") as f:
        yaml.dump(data_yaml, f, sort_keys=False, allow_unicode=True)

    print(f"\n[OK] Dataset binario listo en '{BINARY_DIR}'")


if __name__ == "__main__":
    prepare_binary_dataset()
