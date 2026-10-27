#!/usr/bin/env python3
"""Validate annotated anatomy cases and split by question to avoid paraphrase leakage."""
import argparse
import json
import random
import re
from collections import defaultdict
from pathlib import Path

LABELS={"corretta","parziale","parziale_con_errore","errata"}
FIELDS=("correct","missing","incorrect")

def normalize(s):
    return " ".join(re.findall(r"\w+",s.casefold()))

def validate(row,n):
    for key in ("question","reference","student_answer"):
        if not isinstance(row.get(key),str) or not row[key].strip():
            raise ValueError(f"Row {n}: missing nonempty {key}")
    expected=row.get("expected")
    if not isinstance(expected,dict) or expected.get("outcome") not in LABELS:
        raise ValueError(f"Row {n}: invalid expected.outcome")
    for key in FIELDS:
        if not isinstance(expected.get(key),list) or any(not isinstance(v,str) for v in expected[key]):
            raise ValueError(f"Row {n}: expected.{key} must be list[str]")
    if expected["outcome"]=="corretta" and (expected["missing"] or expected["incorrect"]):
        raise ValueError(f"Row {n}: inconsistent correct outcome")
    if expected["outcome"]=="parziale_con_errore" and not expected["incorrect"]:
        raise ValueError(f"Row {n}: missing incorrect concepts")
    if expected["outcome"]=="parziale" and expected["incorrect"]:
        raise ValueError(f"Row {n}: incorrect concepts require error label")
    if expected["outcome"]=="errata" and expected["correct"]:
        raise ValueError(f"Row {n}: errata with correct concepts; use parziale_con_errore")
    return row

def main():
    p=argparse.ArgumentParser()
    p.add_argument("input")
    p.add_argument("--out",default="splits")
    p.add_argument("--seed",type=int,default=42)
    a=p.parse_args()
    groups=defaultdict(list)
    seen=set()
    for n,line in enumerate(Path(a.input).read_text(encoding="utf-8").splitlines(),1):
        if not line.strip():
            continue
        row=validate(json.loads(line),n)
        key=(normalize(row["question"]),normalize(row["student_answer"]))
        if key in seen:
            raise ValueError(f"Row {n}: duplicate question/answer")
        seen.add(key)
        groups[normalize(row["question"])].append(row)
    keys=list(groups)
    if len(keys)<10:
        raise ValueError("Need >=10 distinct questions for meaningful question-level train/val/test split")
    random.Random(a.seed).shuffle(keys)
    test_count=max(1,round(len(keys)*.1))
    val_count=max(1,round(len(keys)*.1))
    partitions={"test":keys[:test_count],"validation":keys[test_count:test_count+val_count],
                "train":keys[test_count+val_count:]}
    out=Path(a.out);out.mkdir(parents=True,exist_ok=True)
    for name,subset in partitions.items():
        rows=[row for key in subset for row in groups[key]]
        (out/f"{name}.jsonl").write_text("".join(json.dumps(row,ensure_ascii=False)+"\n" for row in rows),encoding="utf-8")
        print(f"{name}: {len(rows)} cases, {len(subset)} questions")
    print("Warning: additionally review topic-level leakage; similar questions can share facts.")
if __name__=="__main__":
    main()
