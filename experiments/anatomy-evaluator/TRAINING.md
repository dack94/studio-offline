## Addestramento QLoRA (sperimentale)

1. Preparare `train.jsonl` con **almeno 100** casi revisionati; per un esperimento serio puntare a 1.000+ casi di qualità.
2. **Separare il test prima di creare varianti/parafrasi**, raggruppando per domanda e argomento.
3. Su Linux con GPU NVIDIA CUDA e ambiente Python dedicato:
   ```bash
   pip install -r experiments/anatomy-evaluator/requirements-train.txt
   python experiments/anatomy-evaluator/train_qlora.py --data experiments/anatomy-evaluator/train.jsonl
   ```
4. Il risultato è un **adapter LoRA**, non un modello Android pronto. Per distribuirlo servono merge, quantizzazione e conversione per il runtime scelto.
5. Eseguire il modello base e quello adattato su **identico test non visto**. Registrare per ogni caso la predizione in JSONL e usare `evaluate.py`.

### Nota
Questo script è una base riproducibile, **non ancora eseguita né validata su GPU**. Le API di TRL possono cambiare; bloccare versioni dopo il primo test. La qualità delle correzioni e la latenza su hardware da meno di 100 € restano da verificare.

### Blocco di sicurezza dei dati (prima di caricare GPU/modello)

`train_qlora.py` ora esegue `dataset_guard.load_training_rows` **prima di importare Torch o scaricare il modello**. Il controllo rifiuta:

- meno di 100 risposte o meno di 10 domande distinte;
- risposte duplicate per la stessa domanda, ID domanda incoerenti;
- etichette duplicate, sovrapposte o incompatibili con l'esito;
- dati marcati `synthetic_template_unreviewed` o privi di revisione approvata.

Ogni esempio utilizzabile per addestramento deve aggiungere i campi `question_id`, `source`, `provenance` e una dichiarazione di revisione, ad esempio:

```json
"review": {
  "status": "approved",
  "reviewer_id": "identificativo-revisore",
  "reviewed_at": "2026-10-28"
}
```

Questi valori sono **uno schema di esempio, non una revisione realmente effettuata**. Compilarli soltanto dopo un controllo umano documentato. Il metadato da solo non dimostra competenza del revisore né correttezza anatomica; occorre una verifica indipendente delle fonti e degli esempi. I 48 esempi sintetici iniziali restano esclusi finché non vengono revisionati individualmente e riclassificati.

I test automatici verificano soltanto la struttura del dataset e le regole di ammissione, **non la correttezza medica**.
