#!/usr/bin/env python3
"""Self-test the six illustrative labels and common speech variants."""
import importlib.util
import json
from pathlib import Path

ROOT=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location("baseline",ROOT/"baseline_rules.py")
baseline=importlib.util.module_from_spec(spec)
spec.loader.exec_module(baseline)

def main():
    rows=[json.loads(line) for line in (ROOT/"examples.jsonl").read_text(encoding="utf-8").splitlines() if line.strip()]
    for i,row in enumerate(rows,1):
        got=baseline.evaluate(row["question"],row["student_answer"])
        want=row["expected"]
        assert got["outcome"]==want["outcome"],(i,"outcome",got,want)
        for key in ("correct","missing","incorrect"):
            assert set(got[key])==set(want[key]),(i,key,got,want)
    tests=[
        ("Quali sono i rami viscerali pari dell'aorta addominale?","Le renali e le surrenali medie.", "parziale"),
        ("Quali sono i rami viscerali impari dell'aorta addominale?","Le arterie renali.", "errata"),
        ("Quali sono i rami viscerali pari dell'aorta addominale?","Le renali, le gonadiche e le surrenali medie.", "corretta"),
        ("Quali sono i rami viscerali pari dell'aorta addominale?","La mesenterica inferiore, la renale e la surrenale media.", "parziale_con_errore"),
    ]
    for q,a,outcome in tests:
        got=baseline.evaluate(q,a)
        assert got["outcome"]==outcome,(a,got,outcome)
    print(f"PASS: {len(rows)} illustrative cases + {len(tests)} speech variants")
    print("These are regression tests, not an unbiased evaluation of AI accuracy.")

if __name__=="__main__":
    main()
