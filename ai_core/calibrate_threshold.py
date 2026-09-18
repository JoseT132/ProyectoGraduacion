"""
Calibra el umbral de confianza para marcar una predicción como "desconocida".

Corre el clasificador sobre el split indicado de dataset_cls_crops, mide la
confianza top-1 de cada imagen y si acertó la clase real. Luego barre umbrales
y reporta, para cada uno:

  - cobertura:  % de imágenes aceptadas (conf >= umbral)
  - precisión:  % de aceptadas que son correctas
  - rechazo:    % de las predicciones INCORRECTAS que quedan por debajo del
                umbral (lo que queremos que el flag "desconocido" capture)

Recomienda el umbral que maximiza precisión-de-aceptados sin sacrificar
demasiada cobertura.
"""
import argparse
from pathlib import Path

import torch
from ultralytics import YOLO


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--model",
        default="runs/classify/runs/classify/insect_yolov11s_cls_crops_v3/weights/best.pt",
    )
    parser.add_argument("--data", default="dataset_cls_crops")
    parser.add_argument("--split", default="test", choices=["val", "test"])
    parser.add_argument("--imgsz", type=int, default=224)
    return parser.parse_args()


def collect_confidences(model, split_dir: Path, imgsz: int, device):
    """Devuelve lista de (confianza_top1, es_correcta)."""
    rows = []
    class_dirs = sorted(d for d in split_dir.iterdir() if d.is_dir())
    for class_dir in class_dirs:
        true_name = class_dir.name
        images = [p for p in class_dir.iterdir() if p.suffix.lower() in (".jpg", ".jpeg", ".png")]
        for img_path in images:
            results = model.predict(str(img_path), imgsz=imgsz, device=device, verbose=False)[0]
            top_idx = results.probs.top1
            top_conf = float(results.probs.top1conf)
            pred_name = results.names[top_idx]
            rows.append((top_conf, pred_name == true_name))
    return rows


def main():
    args = parse_args()
    project_root = Path(__file__).parent.parent
    model_path = project_root / args.model
    split_dir = project_root / "ai_core" / args.data / args.split

    if not model_path.exists():
        print(f"[ERROR] No se encontró {model_path}")
        return
    if not split_dir.exists():
        print(f"[ERROR] No se encontró {split_dir}")
        return

    device = 0 if torch.cuda.is_available() else "cpu"
    print(f"[INFO] Modelo: {model_path.name} | split: {args.split} | device: {device}")

    model = YOLO(str(model_path))
    rows = collect_confidences(model, split_dir, args.imgsz, device)
    total = len(rows)
    correct = sum(1 for _, ok in rows if ok)
    incorrect = total - correct
    print(f"[INFO] {total} imágenes | correctas: {correct} | incorrectas: {incorrect}")
    print(f"[INFO] Accuracy base (sin umbral): {correct / total:.4f}")

    # Barrido de umbrales
    print("\n Umbral | Cobertura | Precisión acept. | Rechazo errores")
    print("--------+-----------+------------------+----------------")
    best = None
    for t in [x / 100 for x in range(5, 96, 5)]:
        accepted = [(c, ok) for c, ok in rows if c >= t]
        if not accepted:
            continue
        coverage = len(accepted) / total
        precision = sum(1 for _, ok in accepted if ok) / len(accepted)
        rejected_errors = sum(1 for c, ok in rows if not ok and c < t)
        reject_rate = rejected_errors / incorrect if incorrect else 0.0
        print(f" {t:5.2f}  |   {coverage:6.3f}   |      {precision:6.3f}      |      {reject_rate:6.3f}")

        # Candidato: precisión aceptados >= 0.85 con mayor cobertura posible
        if precision >= 0.85:
            if best is None or coverage > best[1]:
                best = (t, coverage, precision, reject_rate)

    print()
    if best:
        t, cov, prec, rej = best
        print(f"[OK] Umbral recomendado (precisión >= 85%): {t:.2f}")
        print(f"     Cobertura: {cov:.1%} | Precisión: {prec:.1%} | Errores rechazados: {rej:.1%}")
    else:
        # Fallback: el que maximiza precisión aceptada con cobertura >= 50%
        candidates = []
        for t in [x / 100 for x in range(5, 96, 5)]:
            accepted = [(c, ok) for c, ok in rows if c >= t]
            if len(accepted) / total < 0.5 or not accepted:
                continue
            precision = sum(1 for _, ok in accepted if ok) / len(accepted)
            candidates.append((t, len(accepted) / total, precision))
        if candidates:
            t, cov, prec = max(candidates, key=lambda x: x[2])
            print(f"[OK] Umbral recomendado (máx. precisión con cobertura >= 50%): {t:.2f}")
            print(f"     Cobertura: {cov:.1%} | Precisión: {prec:.1%}")
        else:
            print("[WARN] No se pudo recomendar un umbral.")

    # Estadísticas descriptivas
    confs_ok = sorted(c for c, ok in rows if ok)
    confs_bad = sorted(c for c, ok in rows if not ok)
    if confs_ok and confs_bad:
        med_ok = confs_ok[len(confs_ok) // 2]
        med_bad = confs_bad[len(confs_bad) // 2]
        print(f"\n[INFO] Mediana confianza correctas: {med_ok:.3f} | incorrectas: {med_bad:.3f}")


if __name__ == "__main__":
    main()
