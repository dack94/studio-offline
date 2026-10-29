#!/usr/bin/env python3
"""Compare JSONL predictions with reference labels; reject malformed/misaligned cases.

An optional unique case_id on *both* files prevents silent row-order mismatches.
Legacy files without case_id are supported, but only compared positionally.
"""
import argparse
import json
from collections import Counter

OUTCOMES = {"corretta", "parziale", "parziale_con_errore", "errata"}
CONCEPT_FIELDS = ("correct", "missing", "incorrect")


def load(path):
    rows = []
    with open(path, encoding="utf-8") as stream:
        for line_number, line in enumerate(stream, 1):
            if not line.strip():
                continue
            try:
                row = json.loads(line)
            except json.JSONDecodeError as exc:
                raise ValueError(f"{path}:{line_number}: invalid JSON") from exc
            if not isinstance(row, dict):
                raise ValueError(f"{path}:{line_number}: expected JSON object")
            rows.append(row)
    if not rows:
        raise ValueError(f"{path}: no examples to evaluate")
    return rows


def _labels(value, label):
    if not isinstance(value, dict) or value.get("outcome") not in OUTCOMES:
        raise ValueError(f"{label}: invalid outcome")
    for field in CONCEPT_FIELDS:
        concepts = value.get(field)
        if not isinstance(concepts, list) or any(
            not isinstance(item, str) or not item.strip() for item in concepts
        ):
            raise ValueError(f"{label}: {field} must be list of nonempty strings")
        if len(concepts) != len(set(concepts)):
            raise ValueError(f"{label}: duplicate {field} concepts")
    return value


def _prediction(row):
    return row.get("predicted", row.get("expected", row))


def evaluate(gold, predictions):
    """Return metrics and alignment status; no claim of scientific accuracy."""
    if not gold or not predictions:
        raise ValueError("Both gold and predictions must contain examples")
    if len(gold) != len(predictions):
        raise ValueError("Different numbers of examples")

    # A question_id is not a case identifier: many answers share one question.
    has_ids = any("case_id" in row for row in gold + predictions)
    if has_ids:
        seen = set()
        for index, (g, p) in enumerate(zip(gold, predictions), 1):
            for role, row in (("gold", g), ("prediction", p)):
                if not isinstance(row.get("case_id"), str) or not row["case_id"].strip():
                    raise ValueError(f"Row {index}: missing {role}.case_id")
            if g["case_id"] != p["case_id"]:
                raise ValueError(f"Row {index}: case_id mismatch (possible shuffled predictions)")
            if g["case_id"] in seen:
                raise ValueError(f"Row {index}: duplicate case_id")
            seen.add(g["case_id"])

    outcomes = 0
    exact = 0
    by_class = Counter()
    for index, (g, p) in enumerate(zip(gold, predictions), 1):
        if not isinstance(g, dict) or not isinstance(p, dict):
            raise ValueError(f"Row {index}: expected JSON objects")
        # Check echoed input when available, even for legacy files without case_id.
        for field in ("question", "student_answer"):
            if field in g and field in p and g[field] != p[field]:
                raise ValueError(f"Row {index}: {field} mismatch (possible shuffled predictions)")
        expected = _labels(g.get("expected"), f"Gold row {index}")
        actual = _labels(_prediction(p), f"Prediction row {index}")
        outcomes += expected["outcome"] == actual["outcome"]
        exact += all(set(expected[k]) == set(actual[k]) for k in CONCEPT_FIELDS)
        by_class[(expected["outcome"], actual["outcome"])] += 1
    return {
        "total": len(gold),
        "outcome_accuracy": outcomes / len(gold),
        "exact_concept_sets": exact / len(gold),
        "confusion": dict(by_class),
        "case_id_verified": has_ids,
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("gold", help="JSONL with expected labels")
    parser.add_argument("predictions", help="JSONL with predicted labels in same order")
    args = parser.parse_args()
    metrics = evaluate(load(args.gold), load(args.predictions))
    if not metrics["case_id_verified"]:
        print("WARNING: no case_id supplied; alignment checked only by row order/echoed fields")
    print(f"Examples: {metrics['total']}")
    print(f"Outcome accuracy: {metrics['outcome_accuracy']:.1%}")
    print(f"Exact concept sets: {metrics['exact_concept_sets']:.1%}")
    print("Confusion counts:", metrics["confusion"])


if __name__ == "__main__":
    main()
