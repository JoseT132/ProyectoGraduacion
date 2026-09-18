from contextlib import asynccontextmanager
from fastapi import FastAPI, Depends, UploadFile, File, HTTPException
from sqlalchemy import text
from sqlalchemy.orm import Session
from typing import List, Optional

from . import models, crud, schemas, seed
from . import auth, regions
from .database import engine, get_db
from .services.predict import predict_image

UNKNOWN_THRESHOLD = 0.80


def _migrate(engine):
    """ALTER TABLE ligero para columnas nuevas en una base existente."""
    new_columns = {
        "detections": [
            ("latitude", "FLOAT"),
            ("longitude", "FLOAT"),
            ("in_expected_range", "BOOLEAN"),
            ("region", "VARCHAR"),
        ],
        "species": [
            ("distribution", "JSON"),
        ],
    }
    with engine.connect() as conn:
        for table, cols in new_columns.items():
            existing = {row[1] for row in conn.execute(text(f"PRAGMA table_info({table})"))}
            for name, col_type in cols:
                if name not in existing:
                    conn.execute(text(f"ALTER TABLE {table} ADD COLUMN {name} {col_type}"))
        conn.commit()


@asynccontextmanager
async def lifespan(app: FastAPI):
    models.Base.metadata.create_all(bind=engine)
    _migrate(engine)
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
    latitude: Optional[float] = None,
    longitude: Optional[float] = None,
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
    is_unknown = top["confidence"] < UNKNOWN_THRESHOLD
    species = None if is_unknown else crud.get_species(db, slug=top["slug"])

    in_range = None
    region_name = None
    if latitude is not None and longitude is not None:
        if species is not None:
            in_range, matched = regions.in_distribution_range(
                species.distribution, latitude, longitude
            )
        else:
            matched = regions.regions_for(latitude, longitude)
        region_name = matched[0] if matched else None

    crud.create_detection(
        db,
        species_id=species.id if species else None,
        user_id=current_user.id,
        confidence=top["confidence"],
        top_predictions=predictions,
        image_path=file.filename,
        latitude=latitude,
        longitude=longitude,
        in_expected_range=in_range,
        region=region_name,
    )

    return {
        "species": top["species"],
        "slug": top["slug"],
        "confidence": top["confidence"],
        "top_predictions": predictions,
        "ficha": species,
        "is_unknown": is_unknown,
        "in_expected_range": in_range,
        "region": region_name,
    }


@app.get("/detections", response_model=List[schemas.DetectionResponse])
def get_detections(
    skip: int = 0,
    limit: int = 100,
    db: Session = Depends(get_db),
    current_user: models.User = Depends(auth.get_current_user),
):
    return crud.get_user_detections(db, current_user.id, skip=skip, limit=limit)
