#!/usr/bin/env python3
"""Fail-closed, dependency-free checks before anatomy QLoRA training.

Review metadata is a declaration, not independent proof of expert validation.
"""
import json
import re
from datetime import date
from pathlib import Path

OUTCOMES = {"corretta", "parziale", "parziale_con_errore", "errata"}
FIELDS = ("correct", "missing", "incorrect")
UNREVIEWED_PROVENANCE = {"synthetic_template_unreviewed", "unreviewed", "illustrative"}


def _nonempty(value):
    return isinstance(value, str) and bool(value.strip())


def _normalized(value):
    return " ".join(re.findall(r"\w+", value.casefold()))


def validate_row(row, index):
    if not isinstance(row, dict):
        raise ValueError(f"Row {index}: expected JSON object")
    for field in ("question_id", "question", "reference", "student_answer", "provenance", "source"):
        if not _nonempty(row.get(field)):
            raise ValueError(f"Row {index}: missing nonempty {field}")
    if row["provenance"].casefold() in UNREVIEWED_PROVENANCE or "unreviewed" in row["provenance"].casefold():
        raise ValueError(f"Row {index}: unreviewed provenance is not training-ready")
    review = row.get("review")
    if not isinstance(review, dict) or review.get("status") != "approved":
        raise ValueError(f"Row {index}: review.status must be approved")
    if not _nonempty(review.get("reviewer_id")):
        raise ValueError(f"Row {index}: missing reviewer_id")
    try:
        reviewed = date.fromisoformat(review["reviewed_at"])
    except (KeyError, TypeError, ValueError) as exc:
        raise ValueError(f"Row {index}: invalid review.reviewed_at (YYYY-MM-DD)") from exc
    if reviewed > date.today():
        raise ValueError(f"Row {index}: review date is in the future")
    expected = row.get("expected")
    if not isinstance(expected, dict) or expected.get("outcome") not in OUTCOMES:
        raise ValueError(f"Row {index}: invalid expected.outcome")
    concepts = []
    for field in FIELDS:
        values = expected.get(field)
        if not isinstance(values, list) or any(not _nonempty(v) for v in values):
            raise ValueError(f"Row {index}: expected.{field} must be nonempty strings in a list")
        concepts.extend(_normalized(v) for v in values)
    if len(concepts) != len(set(concepts)):
        raise ValueError(f"Row {index}: duplicate/overlapping concept labels")
    correct, missing, incorrect = (expected[k] for k in FIELDS)
    outcome = expected["outcome"]
    valid = {
        "corretta": bool(correct) and not missing and not incorrect,
        "parziale": bool(correct) and bool(missing) and not incorrect,
        "parziale_con_errore": bool(correct) and bool(incorrect),
        "errata": not correct and bool(missing or incorrect),
    }[outcome]
    if not valid:
        raise ValueError(f"Row {index}: concept lists inconsistent with {outcome}")
    return row


def load_training_rows(path, min_rows=100, min_questions=10):
    path = Path(path)
    rows = []
    seen = set()
    question_ids = {}
    for index, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        try:
            row = validate_row(json.loads(line), index)
        except json.JSONDecodeError as exc:
            raise ValueError(f"Row {index}: invalid JSON") from exc
        qid = row["question_id"]
        question = _normalized(row["question"])
        if qid in question_ids and question_ids[qid] != question:
            raise ValueError(f"Row {index}: question_id maps to different questions")
        question_ids[qid] = question
        key = (question, _normalized(row["student_answer"]))
        if key in seen:
            raise ValueError(f"Row {index}: duplicate question/answer pair")
        seen.add(key)
        rows.append(row)
    if len(rows) < min_rows:
        raise ValueError(f"Need >= {min_rows} reviewed cases; found {len(rows)}")
    if len(set(question_ids.values())) < min_questions:
        raise ValueError(f"Need >= {min_questions} distinct questions; found {len(set(question_ids.values()))}")
    return rows
