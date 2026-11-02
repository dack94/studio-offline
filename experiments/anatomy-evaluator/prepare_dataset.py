#!/usr/bin/env python3
"""Fail-closed, question-level splits of human-reviewed anatomy training data."""
import argparse
import hashlib
import json
import random
from collections import defaultdict
from pathlib import Path

from dataset_guard import _normalized, load_training_rows


def assign_case_ids(rows):
    """Preserve explicit IDs; assign stable content-derived IDs to legacy cases.

    Hashes are identifiers, NOT anonymization of the underlying student answers.
    """
    assigned = []
    seen = set()
    for row in rows:
        case_id = row.get("case_id")
        if case_id is None:
            key = json.dumps(
                [_normalized(row["question"]), _normalized(row["student_answer"])],
                ensure_ascii=False, separators=(",", ":"),
            )
            case_id = "auto-" + hashlib.sha256(key.encode("utf-8")).hexdigest()
        if case_id in seen:
            raise ValueError("duplicate case_id after automatic assignment")
        seen.add(case_id)
        assigned.append({**row, "case_id": case_id})
    return assigned


def prepare_splits(input_path, output_dir, seed=42):
    """Validate before writing anything; keep all answers to a question together.

    The training partition must itself satisfy train_qlora.py admission rules.
    This is not a substitute for manual topic-level leakage checks.
    """
    rows = assign_case_ids(load_training_rows(input_path))
    groups = defaultdict(list)
    for row in rows:
        groups[_normalized(row["question"])].append(row)

    # Sorting makes group assignment stable even if input JSONL rows are reordered.
    keys = sorted(groups)
    random.Random(seed).shuffle(keys)
    test_count = max(1, round(len(keys) * 0.1))
    val_count = max(1, round(len(keys) * 0.1))
    partitions = {
        "test": keys[:test_count],
        "validation": keys[test_count:test_count + val_count],
        "train": keys[test_count + val_count:],
    }
    train_keys = partitions["train"]
    train_cases = sum(len(groups[key]) for key in train_keys)
    if len(train_keys) < 10 or train_cases < 100:
        raise ValueError(
            "Training split needs >=100 reviewed cases and >=10 distinct questions "
            f"after holdout; found {train_cases} cases and {len(train_keys)} questions"
        )

    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=True)
    counts = {}
    for name, subset in partitions.items():
        split_rows = [row for key in subset for row in groups[key]]
        (out / f"{name}.jsonl").write_text(
            "".join(json.dumps(row, ensure_ascii=False) + "\n" for row in split_rows),
            encoding="utf-8",
        )
        counts[name] = (len(split_rows), len(subset))
    return counts


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("input")
    parser.add_argument("--out", default="splits")
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    counts = prepare_splits(args.input, args.out, args.seed)
    for name, (cases, questions) in counts.items():
        print(f"{name}: {cases} cases, {questions} questions")
    print("Warning: additionally review topic-level leakage; similar questions can share facts.")


if __name__ == "__main__":
    main()
