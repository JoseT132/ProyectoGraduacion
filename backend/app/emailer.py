import os
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart

import requests

# Brevo (API HTTPS) — necesario en Render free, que bloquea los puertos SMTP.
BREVO_API_KEY = os.getenv("BREVO_API_KEY", "")
BREVO_URL = "https://api.brevo.com/v3/smtp/email"

SMTP_HOST = os.getenv("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USER = os.getenv("SMTP_USER", "")
SMTP_PASS = os.getenv("SMTP_PASS", "")
SMTP_FROM = os.getenv("SMTP_FROM") or SMTP_USER


def smtp_configured() -> bool:
    return bool(BREVO_API_KEY or (SMTP_USER and SMTP_PASS))


def send_reset_code(to_email: str, code: str) -> bool:
    """Envia el codigo de recuperacion. Devuelve False si SMTP no esta
    configurado o el envio falla (el codigo se loguea para desarrollo)."""
    if not smtp_configured():
        print(f"[emailer] SMTP no configurado. Codigo para {to_email}: {code}")
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
    # Via API HTTPS (Brevo): funciona en Render free, donde SMTP esta bloqueado.
    if BREVO_API_KEY:
        try:
            from_name, _, rest = SMTP_FROM.partition("<")
            sender_email = rest.rstrip("> ").strip() or SMTP_FROM
            r = requests.post(
                BREVO_URL,
                headers={
                    "api-key": BREVO_API_KEY,
                    "Content-Type": "application/json",
                },
                json={
                    "sender": {
                        "name": from_name.strip() or "PlagueID",
                        "email": sender_email,
                    },
                    "to": [{"email": to_email}],
                    "subject": "PlagueID — Codigo de recuperacion",
                    "textContent": text,
                    "htmlContent": html,
                },
                timeout=15,
            )
            if r.status_code in (200, 201, 202):
                return True
            print(f"[emailer] Brevo {r.status_code} enviando a {to_email}: {r.text}. Codigo: {code}")
            return False
        except Exception as e:
            print(f"[emailer] Error Brevo a {to_email}: {e}. Codigo: {code}")
            return False

    msg = MIMEMultipart("alternative")
    msg["Subject"] = "PlagueID — Codigo de recuperacion"
    msg["From"] = SMTP_FROM
    msg["To"] = to_email
    msg.attach(MIMEText(text, "plain"))
    msg.attach(MIMEText(html, "html"))

    try:
        with smtplib.SMTP(SMTP_HOST, SMTP_PORT, timeout=15) as server:
            server.starttls()
            server.login(SMTP_USER, SMTP_PASS)
            server.sendmail(SMTP_FROM, to_email, msg.as_string())
        return True
    except Exception as e:
        print(f"[emailer] Error enviando a {to_email}: {e}. Codigo: {code}")
        return False
