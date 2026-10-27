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
