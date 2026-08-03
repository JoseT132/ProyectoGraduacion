import cv2
import os
import glob

# Ruta a una carpeta con imágenes y etiquetas (.txt)
FOLDER = "raw_data/Agrotis_ipsilon"  # Cambia por la carpeta que quieras revisar

images = glob.glob(os.path.join(FOLDER, "*.jpg"))[:5]  # Muestra las primeras 5

for img_path in images:
    txt_path = img_path.replace(".jpg", ".txt")
    if not os.path.exists(txt_path):
        continue

    img = cv2.imread(img_path)
    h, w, _ = img.shape

    with open(txt_path, "r") as f:
        lines = f.readlines()

    for line in lines:
        parts = line.strip().split()
        class_id, x_c, y_c, bw, bh = map(float, parts)

        # Convertir de coordenadas normalizadas (0 a 1) a píxeles
        x1 = int((x_c - bw / 2) * w)
        y1 = int((y_c - bh / 2) * h)
        x2 = int((x_c + bw / 2) * w)
        y2 = int((y_c + bh / 2) * h)

        # Dibujar rectángulo verde
        cv2.rectangle(img, (x1, y1), (x2, y2), (0, 255, 0), 3)

    cv2.imshow("Verificacion de Etiqueta (Presiona cualquier tecla para la siguiente)", img)
    cv2.waitKey(0)

cv2.destroyAllWindows()