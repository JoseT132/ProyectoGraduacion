from sqlalchemy import Column, Integer, String, Text, Float, DateTime, ForeignKey, JSON
from sqlalchemy.orm import relationship
from datetime import datetime
from .database import Base


class Species(Base):
    __tablename__ = "species"

    id = Column(Integer, primary_key=True, index=True)
    slug = Column(String, unique=True, index=True, nullable=False)
    scientific_name = Column(String, nullable=False)
    common_name = Column(String)
    family = Column(String)
    description = Column(Text)
    life_cycle = Column(Text)
    hosts = Column(Text)
    damage = Column(Text)
    biological_control = Column(Text)
    cultural_control = Column(Text)
    chemical_control = Column(Text)
    threshold = Column(Text)
    references = Column(Text)
    image_url = Column(String)
    created_at = Column(DateTime, default=datetime.utcnow)

    detections = relationship("Detection", back_populates="species")


class Detection(Base):
    __tablename__ = "detections"

    id = Column(Integer, primary_key=True, index=True)
    species_id = Column(Integer, ForeignKey("species.id"))
    confidence = Column(Float)
    top_predictions = Column(JSON)
    image_path = Column(String)
    created_at = Column(DateTime, default=datetime.utcnow)

    species = relationship("Species", back_populates="detections")
