# Dataset sintetico iniziale

Eseguire dalla radice del repository:

```bash
python experiments/anatomy-evaluator/generate_synthetic.py
```

Il generatore legge le **8 domande** in `app/src/main/assets/questions.json` e produce **48 esempi sintetici** (`synthetic_seed.jsonl`), con `question_id`, domanda, riferimento, risposta simulata, etichette e provenienza esplicita `synthetic_template_unreviewed`. L'output è riproducibile con il seed predefinito 42.

## Limiti importanti

- Sono combinazioni meccaniche di concetti, **non risposte di studenti reali** e **non validate da esperti**.
- Non includono errori fattuali credibili, negazioni, confusione fra strutture o risposte discorsive complesse.
- Le otto domande originali non consentono di valutare la generalizzazione a nuovi argomenti. **Non dividere casualmente le 48 righe** tra train e test: produrrebbe leakage tra varianti della stessa domanda.
- Il generatore non prova l'accuratezza dell'IA e non sostituisce la revisione anatomica. La soglia di almeno 100 esempi revisionati in `train_qlora.py` resta intenzionalmente in vigore.

## Passo successivo

Ampliare la banca di domande con fonti anatomiche attendibili e contenuti originali, revisionare concetti e risposte, aggiungere casi realistici con errori e negazioni, poi separare i dati **per domanda/argomento** prima dell'addestramento.
