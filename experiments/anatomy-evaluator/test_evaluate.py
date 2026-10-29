#!/usr/bin/env python3
"""Benchmark integrity tests; fixtures do not validate anatomical knowledge."""
import tempfile
import unittest
from pathlib import Path

from evaluate import evaluate, load


def case(case_id="case-1", outcome="corretta"):
    return {
        "case_id": case_id,
        "question": "Domanda dimostrativa?",
        "student_answer": "Risposta dimostrativa.",
        "expected": {"correct": ["A"], "missing": [], "incorrect": [], "outcome": outcome},
    }


class BenchmarkIntegrityTests(unittest.TestCase):
    def test_matching_predictions(self):
        g = case()
        p = {"case_id": "case-1", "predicted": g["expected"]}
        metrics = evaluate([g], [p])
        self.assertEqual(metrics["outcome_accuracy"], 1.0)
        self.assertEqual(metrics["exact_concept_sets"], 1.0)
        self.assertTrue(metrics["case_id_verified"])

    def test_rejects_swapped_predictions(self):
        gold = [case("one"), case("two")]
        predictions = [{"case_id": "two", "predicted": gold[1]["expected"]},
                       {"case_id": "one", "predicted": gold[0]["expected"]}]
        with self.assertRaisesRegex(ValueError, "case_id mismatch"):
            evaluate(gold, predictions)

    def test_rejects_missing_prediction_id(self):
        with self.assertRaisesRegex(ValueError, "missing prediction.case_id"):
            evaluate([case()], [{"predicted": case()["expected"]}])

    def test_rejects_duplicate_ids(self):
        with self.assertRaisesRegex(ValueError, "duplicate case_id"):
            evaluate([case(), case()], [case(), case()])

    def test_rejects_invalid_prediction_outcome(self):
        p = case()
        p["expected"]["outcome"] = "inventata"
        with self.assertRaisesRegex(ValueError, "invalid outcome"):
            evaluate([case()], [p])

    def test_rejects_missing_concept_field(self):
        p = case()
        del p["expected"]["incorrect"]
        with self.assertRaisesRegex(ValueError, "incorrect must be list"):
            evaluate([case()], [p])

    def test_rejects_empty_benchmark(self):
        with self.assertRaisesRegex(ValueError, "must contain examples"):
            evaluate([], [])

    def test_rejects_mismatched_echoed_question(self):
        g = case()
        p = case()
        p["question"] = "Altra domanda?"
        with self.assertRaisesRegex(ValueError, "question mismatch"):
            evaluate([g], [p])

    def test_legacy_rows_still_work(self):
        g = case()
        g.pop("case_id")
        p = {"predicted": g["expected"]}
        self.assertFalse(evaluate([g], [p])["case_id_verified"])

    def test_rejects_different_counts(self):
        with self.assertRaisesRegex(ValueError, "Different numbers"):
            evaluate([case(), case("two")], [case()])

    def test_load_rejects_empty_file(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "empty.jsonl"
            path.write_text("\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "no examples"):
                load(path)

    def test_load_rejects_invalid_json(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "bad.jsonl"
            path.write_text("{broken}\n", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "invalid JSON"):
                load(path)


if __name__ == "__main__":
    unittest.main()
