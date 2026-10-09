"""Obtiene el refresh_token de Gmail para la cuenta remitente (plagueid1@gmail.com).

Uso:
    python tools/get_gmail_token.py <ruta_al_client_secret.json>

El JSON se descarga de Google Cloud Console -> Credenciales -> cliente OAuth
tipo "App de escritorio". Imprime las env vars GMAIL_SENDER_* listas para
copiar a .env / Render.
"""
import json
import sys
import urllib.parse

import requests

SCOPE = "https://www.googleapis.com/auth/gmail.send"
REDIRECT_URI = "http://localhost"


def main() -> None:
    with open(sys.argv[1], encoding="utf-8") as f:
        creds = json.load(f)["installed"]
    client_id = creds["client_id"]
    client_secret = creds["client_secret"]

    auth_url = "https://accounts.google.com/o/oauth2/v2/auth?" + urllib.parse.urlencode({
        "client_id": client_id,
        "redirect_uri": REDIRECT_URI,
        "response_type": "code",
        "scope": SCOPE,
        "access_type": "offline",
        "prompt": "consent",
    })
    print("\n1. Abre esta URL e inicia sesion con plagueid1@gmail.com:\n")
    print(auth_url)
    print("\n2. El navegador mostrara 'No se puede acceder a localhost' — es normal.")
    print("   Copia la URL completa de la barra de direcciones y pegala aqui.\n")

    redirected = input("URL de redireccion: ").strip()
    code = urllib.parse.parse_qs(urllib.parse.urlparse(redirected).query)["code"][0]

    r = requests.post("https://oauth2.googleapis.com/token", data={
        "client_id": client_id,
        "client_secret": client_secret,
        "code": code,
        "grant_type": "authorization_code",
        "redirect_uri": REDIRECT_URI,
    }, timeout=15)
    r.raise_for_status()
    data = r.json()
    print("\n=== ENV VARS (copiar a .env y a Render) ===")
    print(f"GMAIL_SENDER_CLIENT_ID={client_id}")
    print(f"GMAIL_SENDER_CLIENT_SECRET={client_secret}")
    print(f"GMAIL_SENDER_REFRESH_TOKEN={data['refresh_token']}")


if __name__ == "__main__":
    main()
