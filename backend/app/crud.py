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


def get_user_by_facebook_id(db: Session, facebook_id: str):
    return db.query(models.User).filter(models.User.facebook_id == facebook_id).first()


def get_user(db: Session, user_id: int):
    return db.query(models.User).filter(models.User.id == user_id).first()


def create_detection(db: Session, species_id: int, user_id: int, confidence: float, top_predictions: list, image_path: str = None):
    db_detection = models.Detection(
        species_id=species_id,
        user_id=user_id,
        confidence=confidence,
        top_predictions=top_predictions,
        image_path=image_path
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
