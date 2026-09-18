import argparse
import os
import requests
import time

# Definición de las 25 especies y su ID de clase correspondiente
SPECIES_LIST = [
    # Aleyrodidae
    "Bemisia tabaci", "Trialeurodes vaporariorum", "Aleurocanthus woglumi", "Aleurodicus dispersus", "Bemisia argentifolii",
    # Coccinellidae
    "Hippodamia convergens", "Cycloneda sanguinea", "Harmonia axyridis", "Coccinella septempunctata", "Coleomegilla maculata",
    # Aphididae
    "Myzus persicae", "Brevicoryne brassicae", "Aphis gossypii", "Macrosiphum euphorbiae", "Aphis craccivora",
    # Noctuidae
    "Spodoptera frugiperda", "Helicoverpa zea", "Trichoplusia ni", "Spodoptera exigua", "Agrotis ipsilon",
    # Pentatomidae
    "Nezara viridula", "Murgantia histrionica", "Halyomorpha halys", "Euschistus servus", "Chinavia hilaris"
]

RAW_DATA_DIR = "raw_data"
IMAGES_PER_SPECIES = 150  # Lote inicial sugerido para curación

def get_inaturalist_images(taxon_name, limit=150, page=1):
    """Consulta la API de iNaturalist para obtener URLs de fotos clasificadas por investigación."""
    url = (
        f"https://api.inaturalist.org/v1/observations?taxon_name={taxon_name}"
        f"&quality_grade=research&per_page={limit}&photos=true&page={page}"
    )
    response = requests.get(url)

    if response.status_code != 200:
        print(f"[ERROR] No se pudo obtener datos para {taxon_name}")
        return []

    data = response.json()
    image_urls = []
    for result in data.get('results', []):
        for photo in result.get('photos', []):
            # Cambiar tamaño de la imagen de 'square' o 'medium' a 'large' (1024px)
            img_url = photo.get('url', '').replace('square', 'large').replace('medium', 'large')
            if img_url:
                image_urls.append(img_url)
                if len(image_urls) >= limit:
                    break
        if len(image_urls) >= limit:
            break

    return image_urls


def next_index(species_path):
    """Próximo índice libre para no sobreescribir imágenes existentes."""
    existing = [p for p in os.listdir(species_path) if p.endswith(".jpg")]
    max_idx = 0
    for name in existing:
        try:
            max_idx = max(max_idx, int(name.rsplit("_", 1)[-1].split(".")[0]))
        except ValueError:
            continue
    return max_idx + 1


def download_one(species, target_count):
    """Descarga imágenes de una especie hasta llegar a target_count."""
    species_folder_name = species.replace(" ", "_")
    species_path = os.path.join(RAW_DATA_DIR, species_folder_name)
    os.makedirs(species_path, exist_ok=True)

    existing = len([p for p in os.listdir(species_path) if p.endswith(".jpg")])
    needed = target_count - existing
    if needed <= 0:
        print(f"[OK] {species}: ya tiene {existing} imágenes (objetivo {target_count}).")
        return

    print(f"\n[DESCARGA] {species}: {existing} -> objetivo {target_count} ({needed} nuevas)")

    urls = []
    page = 1
    while len(urls) < needed and page <= 5:
        urls.extend(get_inaturalist_images(species, limit=min(200, needed * 2), page=page))
        page += 1
        time.sleep(1)

    urls = list(dict.fromkeys(urls))
    idx = next_index(species_path)
    downloaded = 0
    for url in urls:
        if downloaded >= needed:
            break
        try:
            img_data = requests.get(url, timeout=15).content
            file_path = os.path.join(species_path, f"{species_folder_name}_{idx:04d}.jpg")
            with open(file_path, 'wb') as handler:
                handler.write(img_data)
            idx += 1
            downloaded += 1
        except Exception:
            continue

    print(f" -> {downloaded} imágenes nuevas guardadas en '{species_path}'")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--species", nargs="*", default=None,
                        help="Especies a descargar (por defecto todas)")
    parser.add_argument("--count", type=int, default=IMAGES_PER_SPECIES,
                        help="Objetivo de imágenes por especie")
    args = parser.parse_args()

    targets = args.species if args.species else SPECIES_LIST
    print("=== INICIANDO DESCARGA AUTOMÁTICA DESDE iNATURALIST ===")
    for species in targets:
        download_one(species, args.count)
        time.sleep(1)  # Pausa amigable con la API

if __name__ == "__main__":
    main()