#!/usr/bin/env python3
"""Split integrity tests; approved fixture metadata is not expert validation."""
import copy
import json
import tempfile
import unittest
from pathlib import Path

from prepare_dataset import prepare_splits
from test_dataset_guard import fixture, write_rows


def rows(questions=12, answers=10):
    return [fixture(q, a) for q in range(questions) for a in range(answers)]


def read_split(folder, name):
    return [json.loads(line) for line in (folder / f"{name}.jsonl").read_text(encoding="utf-8").splitlines()]


class SplitIntegrityTests(unittest.TestCase):
    def test_disjoint_question_splits_and_training_ready(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            data = rows()
            write_rows(folder / "input.jsonl", data)
            counts = prepare_splits(folder / "input.jsonl", folder / "splits")
            self.assertEqual(counts, {"test": (10, 1), "validation": (10, 1), "train": (100, 10)})
            splits = {name: read_split(folder / "splits", name) for name in counts}
            ids = [{r["question_id"] for r in split} for split in splits.values()]
            self.assertTrue(all(ids[i].isdisjoint(ids[j]) for i in range(3) for j in range(i + 1, 3)))
            self.assertEqual(sum(len(split) for split in splits.values()), len(data))

    def test_rejects_unreviewed_without_writing(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            data = rows()
            data[0]["provenance"] = "synthetic_template_unreviewed"
            write_rows(folder / "input.jsonl", data)
            with self.assertRaisesRegex(ValueError, "unreviewed"):
                prepare_splits(folder / "input.jsonl", folder / "splits")
            self.assertFalse((folder / "splits").exists())

    def test_rejects_undersized_training_partition_without_writing(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            write_rows(folder / "input.jsonl", rows(11, 10))
            with self.assertRaisesRegex(ValueError, "Training split needs"):
                prepare_splits(folder / "input.jsonl", folder / "splits")
            self.assertFalse((folder / "splits").exists())

    def test_rejects_duplicate_without_writing(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            data = rows()
            data[1] = copy.deepcopy(data[0])
            write_rows(folder / "input.jsonl", data)
            with self.assertRaisesRegex(ValueError, "duplicate"):
                prepare_splits(folder / "input.jsonl", folder / "splits")
            self.assertFalse((folder / "splits").exists())

    def test_group_assignment_independent_of_row_order(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp)
            data = rows()
            write_rows(folder / "first.jsonl", data)
            write_rows(folder / "reverse.jsonl", list(reversed(data)))
            prepare_splits(folder / "first.jsonl", folder / "first")
            prepare_splits(folder / "reverse.jsonl", folder / "reverse")
            for name in ("train", "validation", "test"):
                first_ids = {r["question_id"] for r in read_split(folder / "first", name)}
                reverse_ids = {r["question_id"] for r in read_split(folder / "reverse", name)}
                self.assertEqual(first_ids, reverse_ids)


if __name__ == "__main__":
    unittest.main()
