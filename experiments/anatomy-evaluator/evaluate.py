#!/usr/bin/env python3
"""Benchmark output JSONL from any model against reviewed reference labels."""
import argparse, json
from collections import Counter

def load(path):
    with open(path, encoding="utf-8") as f:
        return [json.loads(line) for line in f if line.strip()]

def main():
    p=argparse.ArgumentParser()
    p.add_argument("gold", help="JSONL with expected labels")
    p.add_argument("predictions", help="JSONL with predicted labels in same order")
    a=p.parse_args()
    gold=load(a.gold); pred=load(a.predictions)
    if len(gold)!=len(pred):
        raise ValueError("Different numbers of examples")
    total=len(gold); outcomes=0; exact=0; by_class=Counter()
    for g,prediction in zip(gold,pred):
        expected=g["expected"]; actual=prediction.get("predicted",prediction.get("expected",prediction))
        outcomes+=expected["outcome"]==actual.get("outcome")
        exact+=all(set(expected[k])==set(actual.get(k,[])) for k in ("correct","missing","incorrect"))
        by_class[(expected["outcome"],actual.get("outcome","?"))]+=1
    print(f"Examples: {total}")
    print(f"Outcome accuracy: {outcomes/total:.1%}")
    print(f"Exact concept sets: {exact/total:.1%}")
    print("Confusion counts:",dict(by_class))
if __name__=="__main__":
    main()
