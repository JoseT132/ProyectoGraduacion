import argparse
import os
import time

import requests

# Fuente complementaria a iNaturalist: GBIF agrega observaciones research-grade
# de iNaturalist + colecciones de museos digitalizadas (mayor rigor taxonómico).
SPECIES_LIST = [
    "Bemisia tabaci", "Trialeurodes vaporariorum", "Aleurocanthus woglumi", "Aleurodicus dispersus",
    "Hippodamia convergens", "Cycloneda sanguinea", "Harmonia axyridis", "Coccinella septempunctata", "Coleomegilla maculata",
    "Myzus persicae", "Brevicoryne brassicae", "Aphis gossypii", "Macrosiphum euphorbiae", "Aphis craccivora",
    "Spodoptera frugiperda", "Helicoverpa zea", "Trichoplusia ni", "Spodoptera exigua", "Agrotis ipsilon",
    "Nezara viridula", "Murgantia histrionica", "Halyomorpha halys", "Euschistus servus", "Chinavia hilaris"
]

RAW_DATA_DIR = "raw_data"
GBIF_API = "https://api.gbif.org/v1/occurrence/search"


def get_gbif_images(taxon_name, limit=300):
    """Consulta GBIF por occurrences con imágenes de una especie."""
    urls = []
    offset = 0
    page_size = 300
    while len(urls) < limit:
        params = {
            "scientific_name": taxon_name,
            "media_type": "StillImage",
            "limit": min(page_size, limit - len(urls)),
            "offset": offset,
        }
        try:
            resp = requests.get(GBIF_API, params=params, timeout=30)
        except requests.RequestException:
            break
        if resp.status_code != 200:
            break
        results = resp.json().get("results", [])
        if not results:
            break
        for rec in results:
            for media in rec.get("media", []):
                url = media.get("identifier")
                if url and url.lower().startswith("http"):
                    urls.append(url)
        offset += len(results)
        if len(results) < page_size:
            break
        time.sleep(1)
    return urls


def next_index(species_path):
    existing = [p for p in os.listdir(species_path) if p.endswith(".jpg")]
    max_idx = 0
    for name in existing:
        try:
            max_idx = max(max_idx, int(name.rsplit("_", 1)[-1].split(".")[0]))
        except ValueError:
            continue
    return max_idx + 1


def download_one(species, target_count):
    folder = species.replace(" ", "_")
    species_path = os.path.join(RAW_DATA_DIR, folder)
    os.makedirs(species_path, exist_ok=True)

    existing = len([p for p in os.listdir(species_path) if p.endswith(".jpg")])
    needed = target_count - existing
    if needed <= 0:
        print(f"[OK] {species}: ya tiene {existing} imágenes.")
        return

    print(f"\n[GBIF] {species}: {existing} -> objetivo {target_count} ({needed} nuevas)")
    urls = list(dict.fromkeys(get_gbif_images(species, limit=needed * 3)))
    print(f" -> {len(urls)} URLs candidatas")

    idx = next_index(species_path)
    downloaded = 0
    for url in urls:
        if downloaded >= needed:
            break
        try:
            resp = requests.get(url, timeout=15)
            if resp.status_code != 200 or len(resp.content) < 5000:
                continue
            file_path = os.path.join(species_path, f"{folder}_gbif_{idx:04d}.jpg")
            with open(file_path, "wb") as f:
                f.write(resp.content)
            idx += 1
            downloaded += 1
        except requests.RequestException:
            continue
    print(f" -> {downloaded} imágenes nuevas en '{species_path}'")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--species", nargs="*", default=None, help="Especies (por defecto todas)")
    parser.add_argument("--count", type=int, default=280, help="Objetivo de imágenes por especie")
    args = parser.parse_args()

    targets = args.species if args.species else SPECIES_LIST
    print("=== DESCARGA COMPLEMENTARIA DESDE GBIF ===")
    for species in targets:
        download_one(species, args.count)
        time.sleep(1)


if __name__ == "__main__":
    main()
