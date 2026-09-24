import argparse
import json
from collections import Counter, defaultdict
from pathlib import Path

from ultralytics import YOLO


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", required=True, help="Ruta al best.pt del clasificador")
    parser.add_argument("--data", default="dataset_cls_crops", help="Dataset de clasificación")
    parser.add_argument("--split", default="test", choices=["train", "val", "test"])
    parser.add_argument("--out", default="confusion_report.json")
    return parser.parse_args()


def main():
    args = parse_args()
    ai_core = Path(__file__).parent.resolve()
    data_dir = ai_core / args.data / args.split
    if not data_dir.exists():
        print(f"[ERROR] No existe {data_dir}")
        return

    model = YOLO(args.model)
    class_names = [d.name for d in sorted(data_dir.iterdir()) if d.is_dir()]

    correct = Counter()
    total = Counter()
    confusions = defaultdict(Counter)  # true -> predicted -> count

    for cls in class_names:
        for img_path in sorted((data_dir / cls).glob("*.jpg")):
            results = model.predict(str(img_path), imgsz=224, verbose=False)[0]
            pred_idx = results.probs.top1
            pred_name = results.names[pred_idx]
            total[cls] += 1
            if pred_name == cls:
                correct[cls] += 1
            else:
                confusions[cls][pred_name] += 1

    print("\n=== ACCURACY POR CLASE ===")
    per_class = {}
    for cls in class_names:
        acc = correct[cls] / total[cls] if total[cls] else 0.0
        per_class[cls] = {"correct": correct[cls], "total": total[cls], "acc": round(acc, 4)}
        flag = " <-- DEBIL" if acc < 0.85 else ""
        print(f"{cls:32s} {correct[cls]:3d}/{total[cls]:3d}  {acc:.2%}{flag}")

    print("\n=== PARES MAS CONFUNDIDOS ===")
    pairs = []
    for true_cls, preds in confusions.items():
        for pred_cls, n in preds.items():
            pairs.append((n, true_cls, pred_cls))
    pairs.sort(reverse=True)
    for n, true_cls, pred_cls in pairs[:15]:
        print(f"{true_cls:32s} -> {pred_cls:32s}  {n} errores")

    overall = sum(correct.values()) / sum(total.values()) if sum(total.values()) else 0
    print(f"\nTop-1 global en {args.split}: {overall:.2%} ({sum(correct.values())}/{sum(total.values())})")

    report = {
        "model": args.model,
        "split": args.split,
        "overall_top1": round(overall, 4),
        "per_class": per_class,
        "confusions": {t: dict(p) for t, p in confusions.items()},
    }
    out_path = ai_core / args.out
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)
    print(f"[OK] Reporte guardado en {out_path}")


if __name__ == "__main__":
    main()
