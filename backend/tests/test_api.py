import io

from PIL import Image

EMAIL = "test@plagueid.dev"
PASSWORD = "Test1234!"
NEW_PASSWORD = "Nueva1234!"


def _auth(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


def _make_image() -> bytes:
    img = Image.new("RGB", (224, 224), (120, 200, 60))
    buf = io.BytesIO()
    img.save(buf, format="JPEG")
    return buf.getvalue()


def test_register_login_me(client):
    r = client.post("/auth/register", json={
        "first_name": "Test",
        "last_name": "User",
        "email": EMAIL,
        "password": PASSWORD,
    })
    assert r.status_code == 200, r.text
    token = r.json()["access_token"]

    r = client.post("/auth/login", json={"email": EMAIL, "password": PASSWORD})
    assert r.status_code == 200, r.text

    me = client.get("/auth/me", headers=_auth(token))
    assert me.status_code == 200
    assert me.json()["email"] == EMAIL


def test_duplicate_register_and_bad_login(client):
    r = client.post("/auth/register", json={
        "first_name": "Test",
        "last_name": "User",
        "email": EMAIL,
        "password": PASSWORD,
    })
    assert r.status_code == 400

    r = client.post("/auth/login", json={"email": EMAIL, "password": "mala"})
    assert r.status_code == 400


def test_password_reset_flow(client):
    r = client.post("/auth/forgot-password", json={"email": EMAIL})
    assert r.status_code == 200, r.text
    code = r.json().get("dev_code")
    assert code, "sin SMTP el endpoint debe devolver dev_code"

    wrong = "000000" if code != "000000" else "111111"
    r = client.post("/auth/verify-reset-code", json={"email": EMAIL, "code": wrong})
    assert r.status_code == 400

    r = client.post("/auth/verify-reset-code", json={"email": EMAIL, "code": code})
    assert r.status_code == 200

    r = client.post("/auth/reset-password", json={
        "email": EMAIL, "code": code, "new_password": NEW_PASSWORD,
    })
    assert r.status_code == 200

    r = client.post("/auth/login", json={"email": EMAIL, "password": PASSWORD})
    assert r.status_code == 400
    r = client.post("/auth/login", json={"email": EMAIL, "password": NEW_PASSWORD})
    assert r.status_code == 200


def test_predict_and_detections(client):
    r = client.post("/auth/login", json={"email": EMAIL, "password": NEW_PASSWORD})
    token = r.json()["access_token"]

    r = client.post(
        "/predict?latitude=14.6349&longitude=-90.5069",
        files={"file": ("test.jpg", _make_image(), "image/jpeg")},
        headers=_auth(token),
    )
    assert r.status_code == 200, r.text
    det_id = r.json()["detection_id"]
    assert det_id is not None

    dets = client.get("/detections", headers=_auth(token))
    assert dets.status_code == 200
    assert any(d["id"] == det_id for d in dets.json())

    r = client.delete(f"/detections/{det_id}", headers=_auth(token))
    assert r.status_code == 200
    dets = client.get("/detections", headers=_auth(token))
    assert not any(d["id"] == det_id for d in dets.json())


def test_species_list(client):
    r = client.get("/species")
    assert r.status_code == 200
    assert len(r.json()) >= 20
