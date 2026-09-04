from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime


class SpeciesBase(BaseModel):
    slug: str
    scientific_name: str
    common_name: Optional[str] = None
    family: Optional[str] = None
    description: Optional[str] = None
    life_cycle: Optional[str] = None
    hosts: Optional[str] = None
    damage: Optional[str] = None
    biological_control: Optional[str] = None
    cultural_control: Optional[str] = None
    chemical_control: Optional[str] = None
    threshold: Optional[str] = None
    references: Optional[str] = None
    image_url: Optional[str] = None


class SpeciesCreate(SpeciesBase):
    pass


class SpeciesResponse(SpeciesBase):
    id: int
    created_at: datetime

    class Config:
        from_attributes = True


class Prediction(BaseModel):
    rank: int
    species: str
    slug: str
    confidence: float


class PredictResponse(BaseModel):
    species: str
    slug: str
    confidence: float
    top_predictions: List[Prediction]
    ficha: Optional[SpeciesResponse] = None
