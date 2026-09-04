from sqlalchemy.orm import Session
from . import models


def get_species(db: Session, slug: str):
    return db.query(models.Species).filter(models.Species.slug == slug).first()


def get_species_list(db: Session, skip: int = 0, limit: int = 100):
    return db.query(models.Species).offset(skip).limit(limit).all()


def create_species(db: Session, species: dict):
    db_species = models.Species(**species)
    db.add(db_species)
    db.commit()
    db.refresh(db_species)
    return db_species


def create_detection(db: Session, species_id: int, confidence: float, top_predictions: list, image_path: str = None):
    db_detection = models.Detection(
        species_id=species_id,
        confidence=confidence,
        top_predictions=top_predictions,
        image_path=image_path
    )
    db.add(db_detection)
    db.commit()
    db.refresh(db_detection)
    return db_detection
