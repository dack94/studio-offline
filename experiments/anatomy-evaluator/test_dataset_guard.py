#!/usr/bin/env python3
"""Unit tests for data admission; fixture approvals are NOT real expert reviews."""
import copy
import json
import tempfile
import unittest
from pathlib import Path

from dataset_guard import load_training_rows, validate_row


def fixture(question=0, answer=0):
    return {
        "question_id": f"fixture-{question}",
        "question": f"Domanda dimostrativa {question}?",
        "reference": "concetto A; concetto B",
        "student_answer": f"Concetto A, variante test {answer}.",
        "expected": {
            "correct": ["concetto A"],
            "missing": ["concetto B"],
            "incorrect": [],
            "outcome": "parziale",
        },
        "provenance": "unit_test_fixture_only",
        "source": "unit_test",
        "review": {"status": "approved", "reviewer_id": "fixture-not-a-real-reviewer", "reviewed_at": "2020-01-01"},
    }


def write_rows(path, rows):
    path.write_text("".join(json.dumps(r, ensure_ascii=False) + "\n" for r in rows), encoding="utf-8")


class DatasetGuardTests(unittest.TestCase):
    def test_accepts_structurally_valid_fixture(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "train.jsonl"
            write_rows(path, [fixture(q, a) for q in range(10) for a in range(10)])
            rows = load_training_rows(path)
            self.assertEqual(len(rows), 100)

    def test_rejects_blank_case_id(self):
        row = fixture()
        row["case_id"] = "  "
        with self.assertRaisesRegex(ValueError, "case_id must be a nonempty string"):
            validate_row(row, 1)

    def test_rejects_duplicate_explicit_case_ids(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "train.jsonl"
            data = [fixture(q, a) for q in range(10) for a in range(10)]
            data[0]["case_id"] = "same-case"
            data[1]["case_id"] = "same-case"
            write_rows(path, data)
            with self.assertRaisesRegex(ValueError, "duplicate case_id"):
                load_training_rows(path)

    def test_rejects_unreviewed_synthetic(self):
        row = fixture()
        row["provenance"] = "synthetic_template_unreviewed"
        with self.assertRaisesRegex(ValueError, "unreviewed"):
            validate_row(row, 1)

    def test_rejects_missing_review(self):
        row = fixture()
        del row["review"]
        with self.assertRaisesRegex(ValueError, "review.status"):
            validate_row(row, 1)

    def test_rejects_bad_outcome(self):
        row = fixture()
        row["expected"]["outcome"] = "corretta"
        with self.assertRaisesRegex(ValueError, "inconsistent"):
            validate_row(row, 1)

    def test_rejects_overlap(self):
        row = fixture()
        row["expected"]["missing"] = ["concetto A"]
        with self.assertRaisesRegex(ValueError, "overlapping"):
            validate_row(row, 1)

    def test_rejects_duplicate_answer(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "train.jsonl"
            rows = [fixture(q, a) for q in range(10) for a in range(10)]
            rows[1] = copy.deepcopy(rows[0])
            write_rows(path, rows)
            with self.assertRaisesRegex(ValueError, "duplicate"):
                load_training_rows(path)

    def test_rejects_too_few_distinct_questions(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "train.jsonl"
            write_rows(path, [fixture(q, a) for q in range(5) for a in range(20)])
            with self.assertRaisesRegex(ValueError, "distinct questions"):
                load_training_rows(path)

    def test_rejects_future_review(self):
        row = fixture()
        row["review"]["reviewed_at"] = "2999-01-01"
        with self.assertRaisesRegex(ValueError, "future"):
            validate_row(row, 1)

    def test_rejects_conflicting_reference_for_same_question(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "train.jsonl"
            data = [fixture(q, a) for q in range(10) for a in range(10)]
            data[1]["reference"] = "concetto A; concetto C"
            write_rows(path, data)
            with self.assertRaisesRegex(ValueError, "conflicting references"):
                load_training_rows(path)

    def test_rejects_multiple_ids_for_same_question(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "train.jsonl"
            data = [fixture(q, a) for q in range(10) for a in range(10)]
            data[1]["question_id"] = "alias-for-fixture-0"
            write_rows(path, data)
            with self.assertRaisesRegex(ValueError, "multiple question_ids"):
                load_training_rows(path)

    def test_reference_normalization_allows_formatting_variants(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "train.jsonl"
            data = [fixture(q, a) for q in range(10) for a in range(10)]
            data[1]["reference"] = "CONCETTO A, concetto B!"
            write_rows(path, data)
            self.assertEqual(len(load_training_rows(path)), 100)

    def test_rejects_question_id_collision(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "train.jsonl"
            rows = [fixture(q, a) for q in range(10) for a in range(10)]
            rows[10]["question_id"] = rows[0]["question_id"]
            write_rows(path, rows)
            with self.assertRaisesRegex(ValueError, "different questions"):
                load_training_rows(path)


if __name__ == "__main__":
    unittest.main()
