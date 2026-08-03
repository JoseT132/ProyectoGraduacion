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

def get_inaturalist_images(taxon_name, limit=150):
    """Consulta la API de iNaturalist para obtener URLs de fotos clasificadas por investigación."""
    url = f"https://api.inaturalist.org/v1/observations?taxon_name={taxon_name}&quality_grade=research&per_page={limit}&photos=true"
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

def download_species_dataset():
    """Descarga e independiza las imágenes por subcarpetas de especies."""
    if not os.path.exists(RAW_DATA_DIR):
        os.makedirs(RAW_DATA_DIR)
        
    print("=== INICIANDO DESCARGA AUTOMÁTICA DESDE iNATURALIST ===")
    
    for idx, species in enumerate(SPECIES_LIST):
        species_folder_name = species.replace(" ", "_")
        species_path = os.path.join(RAW_DATA_DIR, species_folder_name)
        os.makedirs(species_path, exist_ok=True)
        
        print(f"\n[{idx+1}/25] Procesando: {species}...")
        urls = get_inaturalist_images(species, limit=IMAGES_PER_SPECIES)
        print(f" -> Encontradas {len(urls)} imágenes de grado de investigación.")
        
        downloaded_count = 0
        for i, url in enumerate(urls):
            try:
                img_data = requests.get(url, timeout=10).content
                file_path = os.path.join(species_path, f"{species_folder_name}_{i+1:04d}.jpg")
                with open(file_path, 'wb') as handler:
                    handler.write(img_data)
                downloaded_count += 1
            except Exception as e:
                continue
                
        print(f" -> {downloaded_count} imágenes guardadas en '{species_path}'")
        time.sleep(1) # Pausa amigable con la API

if __name__ == "__main__":
    download_species_dataset()