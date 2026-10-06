from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime, date


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
    distribution: Optional[List[str]] = None


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
    is_unknown: bool = False
    in_expected_range: Optional[bool] = None
    region: Optional[str] = None
    detection_id: Optional[int] = None


class UserBase(BaseModel):
    first_name: str
    last_name: str
    email: str


class UserCreate(UserBase):
    password: str
    birth_date: Optional[date] = None


class UserResponse(UserBase):
    id: int
    age: int
    created_at: datetime

    class Config:
        from_attributes = True


class UserRegister(UserBase):
    password: str
    birth_date: Optional[date] = None


class UserLogin(BaseModel):
    email: str
    password: str


class Token(BaseModel):
    access_token: str
    token_type: str = "bearer"


class TokenPayload(BaseModel):
    sub: Optional[int] = None


class OAuthLogin(BaseModel):
    token: str


class ForgotPasswordRequest(BaseModel):
    email: str


class VerifyCodeRequest(BaseModel):
    email: str
    code: str


class ResetPasswordRequest(BaseModel):
    email: str
    code: str
    new_password: str


class MessageResponse(BaseModel):
    message: str
    dev_code: Optional[str] = None


class UserProfile(UserBase):
    id: int
    age: int
    birth_date: Optional[date] = None
    created_at: datetime

    class Config:
        from_attributes = True


class UserUpdate(BaseModel):
    first_name: Optional[str] = None
    last_name: Optional[str] = None
    birth_date: Optional[date] = None


class DetectionBase(BaseModel):
    confidence: Optional[float] = None
    top_predictions: Optional[List[Prediction]] = None
    image_path: Optional[str] = None
    latitude: Optional[float] = None
    longitude: Optional[float] = None
    in_expected_range: Optional[bool] = None
    region: Optional[str] = None
    created_at: Optional[datetime] = None


class DetectionResponse(DetectionBase):
    id: int
    species: Optional[SpeciesResponse] = None

    class Config:
        from_attributes = True
