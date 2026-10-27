#!/usr/bin/env python3
"""Transparent rule-based baseline for the six illustrative abdominal-aorta examples.
Not an LLM, and not an estimate of generalization to unseen anatomy questions.
"""
import json, re, sys
from pathlib import Path

PAI=("surrenali medie","renali","gonadiche")
IMPARI=("tronco celiaco","mesenterica superiore","mesenterica inferiore")
ALIASES={
 "surrenali medie":["surrenali medie","surrenale media","surrenalica media"],
 "renali":["renali","renale","arteria renale","arterie renali"],
 "gonadiche":["gonadiche","gonadica","testicolari","testicolare","ovariche","ovarica"],
 "tronco celiaco":["tronco celiaco","celiaco"],
 "mesenterica superiore":["mesenterica superiore"],
 "mesenterica inferiore":["mesenterica inferiore"]
}
def norm(s):
 return " ".join(re.findall(r"[a-z0-9]+",s.casefold()))
def mentions(s,term):
 text=" "+norm(s)+" "
 return any(" "+norm(variant)+" " in text for variant in ALIASES[term])
def evaluate(question,answer):
 if "viscerali pari" in norm(question):
  target,other=PAI,IMPARI
 elif "viscerali impari" in norm(question):
  target,other=IMPARI,PAI
 else:
  raise ValueError("Baseline currently covers only paired/unpaired abdominal aorta questions")
 correct=[x for x in target if mentions(answer,x)]
 missing=[x for x in target if x not in correct]
 incorrect=[x+(" (impari)" if x in IMPARI else " (pari)") for x in other if mentions(answer,x)]
 outcome=("parziale_con_errore" if correct else "errata") if incorrect else ("corretta" if not missing else ("parziale" if correct else "errata"))
 return {"correct":correct,"missing":missing,"incorrect":incorrect,"outcome":outcome}
def main():
 path=Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).with_name("examples.jsonl")
 rows=[json.loads(x) for x in path.read_text(encoding="utf-8").splitlines() if x.strip()]
 count=0
 for n,row in enumerate(rows,1):
  pred=evaluate(row["question"],row["student_answer"])
  expected=row["expected"]
  matches=pred["outcome"]==expected["outcome"] and all(set(pred[k])==set(expected[k]) for k in ("correct","missing","incorrect"))
  count+=matches
  print(json.dumps({"case":n,"match":matches,"predicted":pred},ensure_ascii=False))
 print(f"Exact match on illustrative examples: {count}/{len(rows)}; NOT a generalization benchmark.")
if __name__=="__main__":
 main()
