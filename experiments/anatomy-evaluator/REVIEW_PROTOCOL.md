# Protocollo di revisione del dataset anatomico

Questo documento definisce una procedura di revisione umana, non certifica alcuna risposta come corretta. Non utilizzare esempi sintetici o fixture di test come dati scientificamente revisionati.

## Unità di revisione

Per ogni caso registrare domanda, risposta dello studente, riferimento anatomico, fonte verificabile (opera, edizione, capitolo/pagina oppure URL e data di consultazione), identificativo del revisore e data della revisione. Conservare il `case_id` quando presente; non rigenerarlo modificando l'ordine delle righe.

## Controlli prima dell'approvazione

1. Verificare che la fonte sostenga esplicitamente il riferimento, senza inferire dettagli anatomici assenti.
2. Distinguere errori fattuali, omissioni, formulazioni ambigue e varianti terminologiche accettabili.
3. Controllare negazioni, lateralità, rapporti spaziali (anteriore/posteriore, mediale/laterale), origine e decorso dei vasi.
4. Se due fonti autorevoli divergono, documentare la divergenza e sospendere l'approvazione finché non è risolta.
5. Non segnare come revisionata una risposta controllata soltanto da un modello linguistico.
6. Rimuovere dati personali e identificativi degli studenti prima della condivisione; gli hash dei contenuti non garantiscono anonimato.

## Separazione dei dati

La divisione per domanda del preparatore riduce la contaminazione diretta, ma non esclude domande parafrasate o fatti condivisi tra split. Prima di usare il test come benchmark, un revisore deve controllare anche la sovrapposizione semantica tra train, validation e test. Congelare e versionare lo split di test prima di ottimizzare prompt, soglie o modello.

## Decisione finale

Usare tre stati di lavoro: **da revisionare**, **approvato**, **sospeso**. Solo i casi approvati da una persona competente e conformi ai controlli automatici possono essere candidati all'addestramento. Questa procedura non modifica né aggira i requisiti tecnici imposti da `dataset_guard.py`.
