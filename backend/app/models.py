from sqlalchemy import Column, Integer, String, Text, Float, DateTime, Date, ForeignKey, JSON
from sqlalchemy.orm import relationship
from datetime import datetime, date
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


class User(Base):
    __tablename__ = "users"

    id = Column(Integer, primary_key=True, index=True)
    first_name = Column(String, nullable=False)
    last_name = Column(String, nullable=False)
    email = Column(String, unique=True, index=True, nullable=False)
    password_hash = Column(String, nullable=True)
    birth_date = Column(Date, nullable=True)
    google_id = Column(String, unique=True, index=True, nullable=True)
    facebook_id = Column(String, unique=True, index=True, nullable=True)
    created_at = Column(DateTime, default=datetime.utcnow)

    detections = relationship("Detection", back_populates="user")

    @property
    def age(self) -> int:
        if not self.birth_date:
            return 0
        today = date.today()
        return today.year - self.birth_date.year - (
            (today.month, today.day) < (self.birth_date.month, self.birth_date.day)
        )


class Detection(Base):
    __tablename__ = "detections"

    id = Column(Integer, primary_key=True, index=True)
    species_id = Column(Integer, ForeignKey("species.id"))
    user_id = Column(Integer, ForeignKey("users.id"), nullable=True)
    confidence = Column(Float)
    top_predictions = Column(JSON)
    image_path = Column(String)
    created_at = Column(DateTime, default=datetime.utcnow)

    species = relationship("Species", back_populates="detections")
    user = relationship("User", back_populates="detections")
