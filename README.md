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

- **Clasificación**: entrenado con 40 épocas, resolución 224×224.
  - Exactitud top-1 en validación: ~78.7 %
  - Exactitud top-5 en validación: ~94.3 %
  - Exactitud en test: ~75.3 %
- **Detección multi-clase** (`yolo11n`, 50 épocas):
  - mAP@0.5 en test: 0.3392
- **Detección binaria** (`yolo11s`, 24 épocas, clase única "insecto"):
  - mAP@0.5 en test: 0.5638
  - Precision: 0.5063, Recall: 0.6833

> **Nota importante**: Llegar a mAP@0.5 ≥ 0.9 con el dataset actual (~3.500 imágenes y anotaciones automáticas de una sola caja) no es realista. Para acercarse a ese objetivo se requiere un dataset más grande, anotaciones más precisas y/o modelos más grandes (YOLO11m/x) con más tiempo de entrenamiento.

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
```

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

## Autor

Proyecto de graduación - UMG, Décimo Semestre, PGII.
