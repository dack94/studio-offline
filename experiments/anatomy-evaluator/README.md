# Esperimento: correttore orale Qwen 2.5 1.5B

Obiettivo: confrontare il modello originale e uno specializzato LoRA sul compito di valutare risposte orali di anatomia. **Nessun addestramento è stato ancora eseguito**.

## Protocollo
1. Preparare un dataset di almeno 1.000 casi revisionati da un esperto, con domande, riferimenti, risposte dello studente e annotazioni.
2. Dividere per **argomento/domanda** (non casualmente per risposta): train 80%, validation 10%, test 10%. Evitare che parafrasi della stessa domanda compaiano in train e test.
3. Misurare il modello base sul test prima del fine-tuning.
4. Addestrare LoRA su GPU esterna; valutare sullo stesso test e confrontare con un sistema deterministico.
5. Misurare falsi positivi (errori dichiarati corretti), falsi negativi, accuratezza per concetto, precision/recall degli errori, latenza e memoria sul dispositivo finale.
6. Solo se il modello supera i criteri di sicurezza e latenza, quantizzarlo e provarlo su hardware economico.

## Formato
Ogni riga JSONL contiene `question`, `reference`, `student_answer`, `expected` con `correct`, `missing`, `incorrect` ed `outcome`.
Il riferimento è parte dell'input: non si vuole insegnare al modello a inventare fatti anatomici.
I dati in `examples.jsonl` sono **esempi iniziali illustrativi**, non un dataset sufficiente per addestramento né un benchmark rappresentativo.

## Limiti
Un 1.5B specializzato potrebbe migliorare, ma non è garantito. La qualità va misurata su domande mai viste. La valutazione automatica non sostituisce la verifica anatomica umana.

## Integrità del benchmark

`evaluate.py` confronta i file JSONL nell'ordine delle righe. Per evitare di
associare predizioni a casi sbagliati, assegnare a **ogni risposta** un
`case_id` univoco e stabile nel gold e riportare lo stesso `case_id` nelle
predizioni (non usare `question_id`, perché una domanda può avere più risposte).

Esempio di riga delle predizioni:
```json
{"case_id":"caso-0001","predicted":{"correct":["concetto A"],"missing":[],"incorrect":[],"outcome":"corretta"}}
```

Il benchmark rifiuta ID mancanti, duplicati o fuori ordine, JSON non valido,
etichette incomplete e dataset vuoti.
Inoltre il benchmark rifiuta etichette **contraddittorie** sia nel gold sia
nelle predizioni: ad esempio `corretta` con concetti mancanti, `errata` con
concetti corretti, o lo stesso concetto in più liste (ignorando maiuscole e
punteggiatura). Questi controlli seguono le regole strutturali di
`dataset_guard.py`; non certificano la verità anatomica delle etichette. I vecchi file senza `case_id` rimangono
compatibili, ma l'allineamento è soltanto posizionale e viene segnalato con
un avviso: **non usarli per dichiarare risultati scientifici affidabili**.
Questi test verificano il software, non l'accuratezza anatomica del modello.
