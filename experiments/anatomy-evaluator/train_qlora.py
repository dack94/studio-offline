#!/usr/bin/env python3
"""QLoRA supervised fine-tuning. Requires a CUDA GPU; not intended for the Android device."""
import argparse, json, random
from dataset_guard import load_training_rows
from pathlib import Path

def main():
    p=argparse.ArgumentParser()
    p.add_argument("--data",default="experiments/anatomy-evaluator/train.jsonl")
    p.add_argument("--model",default="Qwen/Qwen2.5-1.5B-Instruct")
    p.add_argument("--output",default="anatomy-lora")
    p.add_argument("--epochs",type=float,default=2)
    a=p.parse_args()
    rows=load_training_rows(a.data)
    random.Random(42).shuffle(rows)
    import torch
    from datasets import Dataset
    from transformers import AutoTokenizer,AutoModelForCausalLM,BitsAndBytesConfig
    from peft import LoraConfig
    from trl import SFTTrainer, SFTConfig
    if not torch.cuda.is_available():
        raise RuntimeError("CUDA GPU required for this QLoRA training script")
    tokenizer=AutoTokenizer.from_pretrained(a.model)
    def format_row(row):
        prompt=("Valuta la risposta dello studente esclusivamente rispetto al riferimento. "
                "Restituisci solo JSON con correct, missing, incorrect, outcome. "
                "Non inventare fatti non presenti nel riferimento.\n"
                f"DOMANDA: {row['question']}\nRIFERIMENTO: {row['reference']}\n"
                f"RISPOSTA: {row['student_answer']}")
        messages=[{"role":"system","content":"Sei un valutatore rigoroso di risposte orali di anatomia."},
                  {"role":"user","content":prompt},
                  {"role":"assistant","content":json.dumps(row["expected"],ensure_ascii=False)}]
        return {"text":tokenizer.apply_chat_template(messages,tokenize=False)}
    ds=Dataset.from_list([format_row(x) for x in rows])
    quant=BitsAndBytesConfig(load_in_4bit=True,bnb_4bit_quant_type="nf4",
                             bnb_4bit_compute_dtype=torch.float16,bnb_4bit_use_double_quant=True)
    model=AutoModelForCausalLM.from_pretrained(a.model,device_map="auto",quantization_config=quant)
    model.config.use_cache=False
    lora=LoraConfig(r=16,lora_alpha=32,lora_dropout=0.05,bias="none",task_type="CAUSAL_LM",
                    target_modules=["q_proj","k_proj","v_proj","o_proj","gate_proj","up_proj","down_proj"])
    args=SFTConfig(output_dir=a.output,num_train_epochs=a.epochs,
        per_device_train_batch_size=1,gradient_accumulation_steps=8,learning_rate=1e-4,
        logging_steps=10,save_strategy="epoch",report_to="none",fp16=True,
        gradient_checkpointing=True,seed=42,max_length=1024,dataset_text_field="text")
    trainer=SFTTrainer(model=model,args=args,train_dataset=ds,peft_config=lora,
                       processing_class=tokenizer)
    trainer.train()
    trainer.save_model(a.output)
    tokenizer.save_pretrained(a.output)
if __name__=="__main__":
    main()
