from contextlib import asynccontextmanager
from fastapi import FastAPI, Depends, UploadFile, File, HTTPException
from sqlalchemy.orm import Session
from typing import List

from . import models, crud, schemas, seed
from . import auth
from .database import engine, get_db
from .services.predict import predict_image


@asynccontextmanager
async def lifespan(app: FastAPI):
    models.Base.metadata.create_all(bind=engine)
    db = next(get_db())
    seed.seed_species(db)
    db.close()
    yield


app = FastAPI(
    title="PlagueID API",
    description="Backend para identificación de insectos plaga y fichas técnicas de MIP.",
    version="0.1.0",
    lifespan=lifespan
)

app.include_router(auth.router)


@app.get("/")
def root():
    return {"message": "PlagueID API activa"}


@app.get("/species", response_model=List[schemas.SpeciesResponse])
def list_species(skip: int = 0, limit: int = 100, db: Session = Depends(get_db)):
    return crud.get_species_list(db, skip=skip, limit=limit)


@app.get("/species/{slug}", response_model=schemas.SpeciesResponse)
def get_species(slug: str, db: Session = Depends(get_db)):
    species = crud.get_species(db, slug=slug)
    if not species:
        raise HTTPException(status_code=404, detail="Especie no encontrada")
    return species


@app.post("/predict", response_model=schemas.PredictResponse)
async def predict(
    file: UploadFile = File(...),
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth.get_current_user),
):
    if file.content_type and not file.content_type.startswith("image/"):
        raise HTTPException(status_code=400, detail="El archivo debe ser una imagen")

    image_bytes = await file.read()
    try:
        predictions = predict_image(image_bytes)
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Error en inferencia: {e}")

    top = predictions[0]
    species = crud.get_species(db, slug=top["slug"])

    if species:
        crud.create_detection(
            db,
            species_id=species.id,
            user_id=current_user.id,
            confidence=top["confidence"],
            top_predictions=predictions,
            image_path=file.filename,
        )

    return {
        "species": top["species"],
        "slug": top["slug"],
        "confidence": top["confidence"],
        "top_predictions": predictions,
        "ficha": species,
    }


@app.get("/detections", response_model=List[schemas.DetectionResponse])
def get_detections(
    skip: int = 0,
    limit: int = 100,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth.get_current_user),
):
    return crud.get_user_detections(db, current_user.id, skip=skip, limit=limit)
