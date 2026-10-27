#!/usr/bin/env python3
"""Generate reproducible SYNTHETIC anatomy examples from the app's concept cards.

These are NOT expert-reviewed labels or independent student answers.
Do not use the generated data to claim clinical/anatomical evaluation accuracy.
"""
import argparse
import itertools
import json
import random
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/src/main/assets/questions.json"

def make_rows(cards, seed=42):
    rng = random.Random(seed)
    rows = []
    for qid, card in enumerate(cards):
        concepts = card["concepts"]
        labels = [c["label"] for c in concepts]
        if len(set(labels)) != len(labels) or not labels:
            raise ValueError(f"Invalid concepts in question {qid}")
        variants = []
        # Every non-empty subset, plus an empty answer, for each question.
        for n in range(len(labels) + 1):
            for selected in itertools.combinations(range(len(labels)), n):
                variants.append(selected)
        for selected in variants:
            correct = [labels[i] for i in selected]
            missing = [label for i, label in enumerate(labels) if i not in selected]
            if not correct:
                answer = rng.choice(["Non lo so.", "Non ricordo.", "Non saprei rispondere."])
            else:
                terms = [rng.choice(concepts[i].get("alternatives") or [labels[i]]) for i in selected]
                rng.shuffle(terms)
                answer = rng.choice([
                    ", ".join(terms) + ".",
                    "Direi " + ", ".join(terms) + ".",
                    "Mi vengono in mente " + ", ".join(terms) + ".",
                ])
            rows.append({
                "question_id": f"app-{qid + 1:03d}",
                "question": card["question"],
                "reference": "; ".join(labels),
                "student_answer": answer,
                "expected": {
                    "correct": correct, "missing": missing, "incorrect": [],
                    "outcome": "corretta" if not missing else ("parziale" if correct else "errata")
                },
                "provenance": "synthetic_template_unreviewed",
                "source": "app/src/main/assets/questions.json",
            })
    return rows

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", default="experiments/anatomy-evaluator/synthetic_seed.jsonl")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    cards = json.loads(SOURCE.read_text(encoding="utf-8"))
    rows = make_rows(cards, args.seed)
    path = Path(args.output)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")
    print(f"Generated {len(rows)} unreviewed synthetic examples across {len(cards)} questions: {path}")

if __name__ == "__main__":
    main()
