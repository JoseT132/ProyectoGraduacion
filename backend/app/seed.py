import json
from pathlib import Path
from sqlalchemy.orm import Session
from . import crud


def seed_species(db: Session):
    seed_path = Path(__file__).parent / "seed_data.json"
    if not seed_path.exists():
        return

    with open(seed_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    if crud.get_species_list(db, limit=1):
        updated = 0
        for item in data:
            if item.get("distribution") is not None:
                crud.update_species_distribution(db, item["slug"], item["distribution"])
                updated += 1
        print(f"[OK] Distribución actualizada en {updated} especies existentes.")
        return

    for item in data:
        crud.create_species(db, item)

    print(f"[OK] {len(data)} especies insertadas en la base de datos.")
