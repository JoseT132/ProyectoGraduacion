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


def create_user(db: Session, user_data: dict):
    db_user = models.User(**user_data)
    db.add(db_user)
    db.commit()
    db.refresh(db_user)
    return db_user


def get_user_by_email(db: Session, email: str):
    return db.query(models.User).filter(models.User.email == email).first()


def get_user_by_google_id(db: Session, google_id: str):
    return db.query(models.User).filter(models.User.google_id == google_id).first()


def get_user(db: Session, user_id: int):
    return db.query(models.User).filter(models.User.id == user_id).first()


def update_species_distribution(db: Session, slug: str, distribution):
    species = get_species(db, slug)
    if species is not None:
        species.distribution = distribution
        db.commit()
    return species


def create_detection(db: Session, species_id, user_id: int, confidence: float, top_predictions: list,
                     image_path: str = None, latitude: float = None, longitude: float = None,
                     in_expected_range: bool = None, region: str = None):
    db_detection = models.Detection(
        species_id=species_id,
        user_id=user_id,
        confidence=confidence,
        top_predictions=top_predictions,
        image_path=image_path,
        latitude=latitude,
        longitude=longitude,
        in_expected_range=in_expected_range,
        region=region
    )
    db.add(db_detection)
    db.commit()
    db.refresh(db_detection)
    return db_detection


def get_user_detections(db: Session, user_id: int, skip: int = 0, limit: int = 100):
    return (
        db.query(models.Detection)
        .filter(models.Detection.user_id == user_id)
        .order_by(models.Detection.created_at.desc())
        .offset(skip)
        .limit(limit)
        .all()
    )
