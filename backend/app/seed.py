import json
from pathlib import Path
from sqlalchemy.orm import Session
from . import crud


def seed_species(db: Session):
    if crud.get_species_list(db, limit=1):
        return

    seed_path = Path(__file__).parent / "seed_data.json"
    if not seed_path.exists():
        return

    with open(seed_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    for item in data:
        crud.create_species(db, item)

    print(f"[OK] {len(data)} especies insertadas en la base de datos.")
