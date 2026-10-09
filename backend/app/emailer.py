import base64
import os
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart

import requests

# Gmail API (HTTPS) — necesario en Render free, que bloquea los puertos SMTP.
# Credenciales del cliente OAuth "App de escritorio" autorizado por la cuenta
# remitente (scope gmail.send). Generar con tools/get_gmail_token.py.
GMAIL_CLIENT_ID = os.getenv("GMAIL_SENDER_CLIENT_ID", "")
GMAIL_CLIENT_SECRET = os.getenv("GMAIL_SENDER_CLIENT_SECRET", "")
GMAIL_REFRESH_TOKEN = os.getenv("GMAIL_SENDER_REFRESH_TOKEN", "")
GMAIL_SEND_URL = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send"
GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"

# Fallback SMTP para desarrollo local.
SMTP_HOST = os.getenv("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USER = os.getenv("SMTP_USER", "")
SMTP_PASS = os.getenv("SMTP_PASS", "")
SMTP_FROM = os.getenv("SMTP_FROM") or SMTP_USER


def email_configured() -> bool:
    return bool(GMAIL_REFRESH_TOKEN or (SMTP_USER and SMTP_PASS))


def _gmail_access_token() -> str:
    r = requests.post(
        GOOGLE_TOKEN_URL,
        data={
            "client_id": GMAIL_CLIENT_ID,
            "client_secret": GMAIL_CLIENT_SECRET,
            "refresh_token": GMAIL_REFRESH_TOKEN,
            "grant_type": "refresh_token",
        },
        timeout=15,
    )
    r.raise_for_status()
    return r.json()["access_token"]


def send_reset_code(to_email: str, code: str) -> bool:
    """Envia el codigo de recuperacion. Devuelve False si el correo no esta
    configurado o el envio falla (el codigo se loguea para desarrollo)."""
    if not email_configured():
        print(f"[emailer] Correo no configurado. Codigo para {to_email}: {code}")
        return False

    text = (
        "Recibimos una solicitud para restablecer la contrasena de tu cuenta PlagueID.\n\n"
        f"Tu codigo de recuperacion es: {code}\n\n"
        "Para recuperar tu contrasena:\n"
        "1. Abre la aplicacion PlagueID en tu telefono.\n"
        "2. En la pantalla de inicio de sesion toca \"Olvide mi contrasena\".\n"
        "3. Ingresa este codigo de 6 digitos.\n"
        "4. Escribe tu nueva contrasena en la misma aplicacion.\n\n"
        "El codigo expira en 24 horas o cuando se genere uno nuevo.\n"
        "Si no solicitaste este codigo, ignora este mensaje."
    )
    html = f"""
    <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;border:1px solid #C8E6C9;border-radius:12px;padding:28px">
      <h2 style="color:#1B5E20;margin:0 0 16px">PlagueID</h2>
      <p style="color:#2E3B32;margin:0 0 8px">Recibimos una solicitud para restablecer la
      contrasena de tu cuenta.</p>
      <p style="color:#2E3B32;margin:16px 0 4px">Tu codigo de recuperacion es:</p>
      <p style="font-size:34px;font-weight:bold;letter-spacing:8px;color:#2E7D32;
      background:#F1F8E9;border-radius:10px;text-align:center;padding:14px 0;margin:8px 0 16px">{code}</p>
      <p style="color:#2E3B32;margin:0 0 6px"><b>Para recuperar tu contrasena:</b></p>
      <ol style="color:#2E3B32;margin:0 0 16px;padding-left:20px;line-height:1.6">
        <li>Abre la aplicacion <b>PlagueID</b> en tu telefono.</li>
        <li>En la pantalla de inicio de sesion toca <b>"Olvide mi contrasena"</b>.</li>
        <li>Ingresa este codigo de 6 digitos.</li>
        <li>Escribe tu nueva contrasena en la misma aplicacion.</li>
      </ol>
      <p style="color:#5F6B62;font-size:13px;margin:0 0 8px">El codigo expira en
      <b>24 horas</b> o cuando se genere uno nuevo.</p>
      <p style="color:#5F6B62;font-size:12px;margin:0">Si no solicitaste este codigo,
      ignora este mensaje — tu contrasena no cambiara.</p>
    </div>
    """

    msg = MIMEMultipart("alternative")
    msg["Subject"] = "PlagueID — Codigo de recuperacion"
    msg["From"] = SMTP_FROM
    msg["To"] = to_email
    msg.attach(MIMEText(text, "plain"))
    msg.attach(MIMEText(html, "html"))

    # Via Gmail API (HTTPS): funciona en Render free, donde SMTP esta bloqueado.
    if GMAIL_REFRESH_TOKEN:
        try:
            raw = base64.urlsafe_b64encode(msg.as_bytes()).decode()
            r = requests.post(
                GMAIL_SEND_URL,
                headers={"Authorization": f"Bearer {_gmail_access_token()}"},
                json={"raw": raw},
                timeout=15,
            )
            if r.status_code == 200:
                return True
            print(f"[emailer] Gmail API {r.status_code} enviando a {to_email}: {r.text}. Codigo: {code}")
            return False
        except Exception as e:
            print(f"[emailer] Error Gmail API a {to_email}: {e}. Codigo: {code}")
            return False

    try:
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15) as server:
            server.starttls()
            server.login(SMTP_USER, SMTP_PASS)
            server.sendmail(SMTP_FROM, to_email, msg.as_string())
        return True
    except Exception as e:
        print(f"[emailer] Error enviando a {to_email}: {e}. Codigo: {code}")
        return False
