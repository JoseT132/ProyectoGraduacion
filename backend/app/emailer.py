import os
import smtplib
from email.mime.text import MIMEText
from email.mime.multipart import MIMEMultipart

SMTP_HOST = os.getenv("SMTP_HOST", "smtp.gmail.com")
SMTP_PORT = int(os.getenv("SMTP_PORT", "587"))
SMTP_USER = os.getenv("SMTP_USER", "")
SMTP_PASS = os.getenv("SMTP_PASS", "")
SMTP_FROM = os.getenv("SMTP_FROM") or SMTP_USER


def smtp_configured() -> bool:
    return bool(SMTP_USER and SMTP_PASS)


def send_reset_code(to_email: str, code: str) -> bool:
    """Envia el codigo de recuperacion. Devuelve False si SMTP no esta
    configurado o el envio falla (el codigo se loguea para desarrollo)."""
    if not smtp_configured():
        print(f"[emailer] SMTP no configurado. Codigo para {to_email}: {code}")
        return False

    msg = MIMEMultipart("alternative")
    msg["Subject"] = "PlagueID - Codigo de recuperacion"
    msg["From"] = SMTP_FROM
    msg["To"] = to_email

    text = (
        f"Tu codigo de recuperacion de PlagueID es: {code}\n\n"
        "Expira en 24 horas o cuando se genere uno nuevo.\n"
        "Si no solicitaste este codigo, ignora este mensaje."
    )
    html = f"""
    <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto">
      <h2 style="color:#1B5E20">PlagueID</h2>
      <p>Tu codigo de recuperacion es:</p>
      <p style="font-size:32px;font-weight:bold;letter-spacing:6px;color:#2E7D32">{code}</p>
      <p>Expira en <b>24 horas</b> o cuando se genere uno nuevo.</p>
      <p style="color:#5F6B62;font-size:12px">Si no solicitaste este codigo, ignora este mensaje.</p>
    </div>
    """
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
