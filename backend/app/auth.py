import os
import requests
from datetime import date
from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer
from jose import JWTError
from sqlalchemy.orm import Session
from google.oauth2 import id_token
from google.auth.transport import requests as google_requests

from . import crud, models, schemas
from .database import get_db
from .security import (
    create_access_token,
    decode_access_token,
    get_password_hash,
    verify_password,
)

router = APIRouter(prefix="/auth", tags=["auth"])

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/auth/login")

GOOGLE_CLIENT_ID = os.getenv("GOOGLE_CLIENT_ID", "")
FACEBOOK_APP_ID = os.getenv("FACEBOOK_APP_ID", "")


def get_current_user(
    token: str = Depends(oauth2_scheme),
    db: Session = Depends(get_db),
) -> models.User:
    credentials_exception = HTTPException(
        status_code=status.HTTP_401_UNAUTHORIZED,
        detail="No se pudo validar el token",
        headers={"WWW-Authenticate": "Bearer"},
    )
    payload = decode_access_token(token)
    if payload is None:
        raise credentials_exception
    user_id = payload.get("sub")
    if user_id is None:
        raise credentials_exception
    user = crud.get_user(db, int(user_id))
    if user is None:
        raise credentials_exception
    return user


@router.post("/register", response_model=schemas.Token)
def register(user_in: schemas.UserRegister, db: Session = Depends(get_db)):
    existing = crud.get_user_by_email(db, user_in.email)
    if existing:
        raise HTTPException(status_code=400, detail="El correo ya está registrado")

    user_data = {
        "first_name": user_in.first_name,
        "last_name": user_in.last_name,
        "email": user_in.email,
        "password_hash": get_password_hash(user_in.password),
        "birth_date": user_in.birth_date,
    }
    user = crud.create_user(db, user_data)
    access_token = create_access_token(data={"sub": user.id})
    return {"access_token": access_token, "token_type": "bearer"}


@router.post("/login", response_model=schemas.Token)
def login(user_in: schemas.UserLogin, db: Session = Depends(get_db)):
    user = crud.get_user_by_email(db, user_in.email)
    if not user or not user.password_hash:
        raise HTTPException(status_code=400, detail="Correo o contraseña incorrectos")
    if not verify_password(user_in.password, user.password_hash):
        raise HTTPException(status_code=400, detail="Correo o contraseña incorrectos")

    access_token = create_access_token(data={"sub": user.id})
    return {"access_token": access_token, "token_type": "bearer"}


@router.get("/me", response_model=schemas.UserProfile)
def read_users_me(current_user: models.User = Depends(get_current_user)):
    return current_user


@router.post("/google", response_model=schemas.Token)
def login_google(data: schemas.OAuthLogin, db: Session = Depends(get_db)):
    if not GOOGLE_CLIENT_ID:
        raise HTTPException(status_code=501, detail="Google OAuth no está configurado")

    try:
        idinfo = id_token.verify_oauth2_token(
            data.token,
            google_requests.Request(),
            GOOGLE_CLIENT_ID,
        )
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Token de Google inválido: {exc}")

    google_id = idinfo.get("sub")
    email = idinfo.get("email")
    first_name = idinfo.get("given_name", "")
    last_name = idinfo.get("family_name", "")

    user = crud.get_user_by_google_id(db, google_id)
    if not user:
        existing_email = crud.get_user_by_email(db, email) if email else None
        if existing_email:
            raise HTTPException(
                status_code=400,
                detail="Este correo ya está registrado con otro método",
            )
        user = crud.create_user(
            db,
            {
                "first_name": first_name,
                "last_name": last_name,
                "email": email or f"{google_id}@google.local",
                "google_id": google_id,
            },
        )

    access_token = create_access_token(data={"sub": user.id})
    return {"access_token": access_token, "token_type": "bearer"}


@router.post("/facebook", response_model=schemas.Token)
def login_facebook(data: schemas.OAuthLogin, db: Session = Depends(get_db)):
    if not FACEBOOK_APP_ID:
        raise HTTPException(status_code=501, detail="Facebook OAuth no está configurado")

    try:
        resp = requests.get(
            "https://graph.facebook.com/me",
            params={
                "access_token": data.token,
                "fields": "id,email,first_name,last_name",
            },
        )
        resp.raise_for_status()
        profile = resp.json()
    except Exception as exc:
        raise HTTPException(status_code=400, detail=f"Token de Facebook inválido: {exc}")

    facebook_id = profile.get("id")
    email = profile.get("email")
    first_name = profile.get("first_name", "")
    last_name = profile.get("last_name", "")

    user = crud.get_user_by_facebook_id(db, facebook_id)
    if not user:
        existing_email = crud.get_user_by_email(db, email) if email else None
        if existing_email:
            raise HTTPException(
                status_code=400,
                detail="Este correo ya está registrado con otro método",
            )
        user = crud.create_user(
            db,
            {
                "first_name": first_name,
                "last_name": last_name,
                "email": email or f"{facebook_id}@facebook.local",
                "facebook_id": facebook_id,
            },
        )

    access_token = create_access_token(data={"sub": user.id})
    return {"access_token": access_token, "token_type": "bearer"}
