# PlagueID - Identificador de Insectos Plaga

Proyecto de graduación para la identificación de insectos plaga en cultivos mediante inteligencia artificial, integrado en una aplicación móvil Android (Kotlin) con soporte híbrido offline/online y ficha técnica de Manejo Integrado de Plagas (MIP).

## Objetivo

Desarrollar un sistema que permita a los agricultores identificar insectos plaga ya sea con una foto tomada en tiempo real con la cámara del móvil o desde la galería, y asociar el resultado con una ficha técnica de manejo integrado de plagas.

## Estructura del proyecto

```
ProyectoGraduacion/
├── ai_core/           # Pipeline de IA: descarga, preparación, entrenamiento, exportación
├── backend/           # API REST con FastAPI para predicción y fichas técnicas
├── database/          # (conceptual) Esquema y datos manejados por SQLAlchemy en backend
├── mobile_app/        # Aplicación Android en Kotlin
├── runs/              # Entrenamientos y modelos generados
└── README.md
```

## 1. Pipeline de IA (`ai_core/`)

El modelo de IA se basa en YOLOv11 (Ultralytics) para clasificación de 25 especies.

### Archivos principales

| Archivo | Descripción |
|---------|-------------|
| `download_data.py` | Descarga imágenes desde iNaturalist para las 25 especies. |
| `prepare_cls_dataset.py` | Divide `raw_data` en `dataset_cls/train`, `val` y `test`. |
| `prepare_det_dataset.py` | Crea `dataset_det/` con imágenes y etiquetas YOLO para detección. |
| `train_cls.py` | Entrena el clasificador YOLOv11s-cls. |
| `train_det.py` | Entrena un detector YOLOv11n. |
| `predict_cls.py` | Prueba de inferencia Top-5 del clasificador. |
| `predict_det.py` | Prueba de inferencia del detector. |
| `prepare_det_binary.py` | Crea `dataset_det_binary/` con una sola clase "insecto". |
| `train_det_binary.py` | Entrena un detector binario YOLO11s. |
| `evaluate_det.py` | Evalúa mAP del detector multi-clase. |
| `evaluate_det_binary.py` | Evalúa mAP del detector binario. |
| `predict_binary_crop.py` | Pipeline: detecta insecto, recorta y clasifica. |
| `export_cls.py` | Exporta el clasificador a ONNX/TFLite para el móvil. |
| `export_det.py` | Exporta el detector a ONNX. |
| `export_det_binary.py` | Exporta el detector binario a ONNX. |
| `check_env.py` | Verifica PyTorch, Ultralytics y CUDA. |
| `requirements.txt` | Dependencias del entorno virtual. |

### Resultados actuales

Pipeline usado por la app: detector binario (insecto / no insecto) → recorte → clasificador de 25 especies sobre el recorte.

- **Clasificación sobre recortes** (`dataset_cls_crops`, 224×224):
  - `yolo11s-cls` v3 (modelo actual de la app): top-1 test **0.7182**, top-5 test **0.9033**
  - `yolo11s-cls` v2 (etiquetas anteriores): top-1 test 0.7056, top-5 test 0.8972
  - `yolo11n-cls`: top-1 test 0.662, top-5 test 0.895
  - (Clasificador anterior sobre imagen completa: top-1 test ~75.3 % — no comparable porque la app clasifica recortes, no la imagen entera)
- **Detección multi-clase** (`yolo11n`, 50 épocas):
  - mAP@0.5 en test: 0.3392
- **Detección binaria** (clase única "insecto"):
  - `yolo11n` v2 con etiquetas curadas (modelo actual de la app): mAP@0.5 test **0.6737**, mAP@0.5:0.95 **0.5107**, Precision 0.6808, Recall 0.6318

> **Umbral de "desconocido"**: `calibrate_threshold.py` mide la confianza top-1 en el test set (mediana 0.997 en aciertos vs 0.725 en errores). El umbral elegido es **0.80**: retiene el 71 % de las entradas con 86 % de precisión y rechaza el 65 % de las predicciones erróneas. Se aplica tanto en la app como en `/predict`.

### Reproducir

```powershell
# Activar entorno virtual
cd ai_core
.\venv\Scripts\Activate.ps1

# Entrenar clasificador
python train_cls.py

# Entrenar detector
python train_det.py

# Exportar clasificador a ONNX para móvil
python export_cls.py
```

El ONNX exportado se usará en la aplicación Android.

## 2. Backend (`backend/`)

API REST construida con **FastAPI** y **SQLAlchemy** (SQLite por defecto, PostgreSQL mediante variable `DATABASE_URL`).

### Endpoints principales

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/` | Estado de la API. |
| `GET` | `/species` | Lista todas las especies con ficha técnica. |
| `GET` | `/species/{slug}` | Ficha técnica de una especie. |
| `POST` | `/predict` | Recibe una imagen y devuelve especie + Top 5 + ficha. |

### Ejecutar backend

```powershell
cd backend
python -m uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

La base de datos se crea automáticamente y se carga con las 25 fichas técnicas desde `backend/app/seed_data.json`.

### Variables de entorno

```bash
DATABASE_URL=sqlite:///./backend.db      # Por defecto
# DATABASE_URL=postgresql://user:pass@localhost/plagueid
GOOGLE_CLIENT_ID=                        # Client ID web de Google Cloud (ver sección OAuth)
```

### Configurar Google OAuth

El login con Google ya está implementado (`POST /auth/google` en el backend y el botón "Continuar con Google" en la app). Para activarlo se necesitan credenciales de **Google Cloud Console**:

1. Entra a <https://console.cloud.google.com/> y crea un proyecto (o usa uno existente).
2. En **APIs y servicios → Pantalla de consentimiento de OAuth**, configura el consentimiento (tipo "Externo", nombre de la app, correo de soporte).
3. En **APIs y servicios → Credenciales → Crear credenciales → ID de cliente de OAuth**:
   - **Tipo: Android** — nombre cualquiera, nombre de paquete `com.plagueid.app` y la huella **SHA-1** de tu keystore. Para el keystore de debug:
     ```powershell
     keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
     ```
     Copia el valor `SHA1` que imprime.
   - **Tipo: Aplicación web** — nombre cualquiera, sin URIs de redirección. Copia el **Client ID** generado (formato `xxxx.apps.googleusercontent.com`).
4. Pega el **Client ID web** en dos lugares:
   - `mobile_app/app/src/main/res/values/strings.xml` → `<string name="google_web_client_id">...</string>`
   - Variable de entorno del backend:
     ```powershell
     $env:GOOGLE_CLIENT_ID="xxxx.apps.googleusercontent.com"
     ..\ai_core\venv\Scripts\python.exe -m uvicorn app.main:app --host 0.0.0.0 --port 8000
     ```
5. Recompila la app. El botón de Google abrirá el selector de cuentas y el backend verificará el `idToken` antes de emitir el JWT.

> Si `google_web_client_id` está vacío, el botón muestra "Google OAuth no está configurado" y el endpoint responde 501.

## 3. Aplicación móvil (`mobile_app/`)

Aplicación nativa **Android** en **Kotlin**.

### Funcionalidades

- Seleccionar foto de la galería.
- Tomar foto con la cámara.
- Inferencia **offline** con el modelo ONNX exportado.
- Consulta **online** al backend para mostrar la ficha técnica MIP.

### Preparar el modelo

Antes de compilar, copia el ONNX del clasificador y las etiquetas a los assets:

```powershell
copy ai_core\models_export\cls_labels.json mobile_app\app\src\main\assets\labels.json

copy runs\classify\runs\classify\insect_yolov11_cls\weights\best.onnx mobile_app\app\src\main\assets\insect_classifier.onnx
```

### Requisitos previos

- Android Studio instalado.
- Android SDK 34 (descárgalo desde el SDK Manager).
- Emulador creado: **Device Manager > Create Device > Pixel 7 > API 34 x86_64**.

### Paso a paso para compilar y ejecutar

1. Abre `mobile_app/` en Android Studio.
2. Android Studio detectará el wrapper. Si te pide elegir JDK, selecciona **JDK 17**.
3. Sincroniza Gradle con el icono del elefante o `File > Sync Project with Gradle Files`.
4. Si no aparece el botón **Run**, crea la configuración:
   - `Run > Edit Configurations... > + > Android App`.
   - Selecciona el módulo `:app` y la actividad `MainActivity`.
5. Selecciona el emulador o dispositivo en la barra superior.
6. Presiona el botón verde **Run** (Shift + F10).
7. Si el emulador no arranca, verifica que la virtualización esté activada (Intel VT-x/AMD-V) y que tengas HAXM/WHPX instalado.
8. Para conectar con el backend en el emulador, levanta FastAPI y deja `ApiService.kt` con `http://10.0.2.2:8000`.

### Conexión con backend

Para usar el emulador, la URL base en `ApiService.kt` está configurada como `http://10.0.2.2:8000` (redirección al localhost del host). Para un dispositivo físico, cámbiala por la IP de tu computadora en la red local.

## 4. Ficha técnica de MIP

Cada especie cuenta con:

- Nombre común y científico.
- Familia taxonómica.
- Descripción y ciclo de vida.
- Hospederos.
- Daños que ocasiona.
- Control biológico, cultural y químico.
- Umbral de acción.
- Referencias.

Los datos iniciales están en `backend/app/seed_data.json` y se cargan automáticamente al iniciar el backend.

## 5. Modelo de datos

La base de datos incluye (entre otras) las tablas:

- `species`: información de cada especie y ficha técnica.
- `detections`: registro opcional de identificaciones realizadas.

## 6. Notas importantes

- Algunas especies incluidas (Coccinellidae) son **depredadores benéficos**, no plagas. Se incluyen para evitar falsos positivos y brindar información ecológica.
- El dataset presenta desbalance: `Aleurocanthus woglumi` y `Bemisia tabaci` tienen menos imágenes. Se recomienda completar datos o usar técnicas de balanceo.
- La exportación a TFLite con Ultralytics **no es compatible con Windows**. Se provee el modelo en ONNX, que es compatible con Android usando ONNX Runtime Mobile.
- Para detección en tiempo real con bounding boxes, completa el entrenamiento de `train_det.py` y exporta con `export_det.py`.

## 7. Próximos pasos sugeridos

1. Completar y balancear el dataset.
2. Entrenar el detector y evaluar mAP.
3. Integrar detección + clasificación en la app móvil.
4. Mejorar UI/UX y añadir mapa de detecciones.
5. Configurar despliegue del backend en la nube.


