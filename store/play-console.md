# Play Console: cosa compilare per Sordina

Risposte pronte per la sezione **Dashboard → Configura la tua app**, nell'ordine in cui le chiede Play.
Fatte leggendo il codice al 16/11/2026 (manifest, rete, librerie). Se cambiano permessi o chiamate di rete, vanno riviste.

---

## 0. Già sistemato nell'app

- **Contatto:** nell'informativa c'è il link alle segnalazioni del repo (`github.com/baf93/sordina-launcher/issues`), nessuna email. Funziona quando il repo diventa pubblico.
- **Informativa sull'accessibilità:** prima di aprire le impostazioni di Android, Sordina mostra un foglio "Usare l'accessibilità?" con la spiegazione completa e i tasti "Accetto, apri le impostazioni" / "Annulla". Senza "Accetto" non si va avanti. Vale in tutti e tre i punti da cui la si accende: Freno, Home Assistant, Permessi.

## 1. Norme sulla privacy

Serve un URL pubblico. La pagina è `store/privacy/index.html`: un file solo, senza dipendenze, in IT + EN.

Quando il repo `sordina-launcher` è pubblico: **Settings → Pages → Deploy from a branch → `main` / `/ (root)`**.
L'URL dell'informativa diventa `https://baf93.github.io/sordina-launcher/store/privacy/`.

## 2. Accesso all'app
"Parti della tua app sono soggette a limitazioni?" **No.**
- Non ci sono account, pagamenti né codici.
- Le app nascoste chiedono il blocco schermo del telefono, quello del revisore. Senza un blocco impostato si aprono subito, quindi non sono una restrizione.
- Home Assistant è facoltativo e usa il server dell'utente: non serve dare credenziali al revisore.

## 3. Annunci
**No, la mia app non contiene annunci.**

## 4. Classificazione dei contenuti
- Categoria: **Tutte le altre tipologie di app** (non è un gioco, né un social).
- Rispondi **No** a tutto: violenza, sessualità, linguaggio, sostanze, gioco d'azzardo.
- Interazione fra utenti / contenuti generati dagli utenti: **No**. Sordina passa i messaggi alle altre app, ma non ha una sua chat.
- Condivide la posizione con altri utenti: **No**.
- Acquisti digitali: **No**.
- Esito atteso: PEGI 3 / Everyone.

## 5. Pubblico di destinazione
- Fasce d'età: **18 anni e oltre**. Scegliere fasce sotto i 13 farebbe scattare le norme Famiglie, che qui non servono.
- "Potrebbe attrarre i bambini?" **No**.

## 6. Altre dichiarazioni
- App di notizie: **No**.
- App governativa: **No**.
- Funzionalità finanziarie: **La mia app non offre funzionalità finanziarie**.
- App per la salute: **nessuna**.
- Tracciamento contatti COVID: **No**.

## 7. Sicurezza dei dati (Data safety)

**La tua app raccoglie o condivide i tipi di dati utente richiesti?** **Sì**, per due motivi: la posizione approssimata mandata a Open-Meteo e i dati diagnostici inviati dalle librerie Google ML Kit.

- **Tutti i dati sono criptati in transito?** **Sì** (HTTPS).
- **Account:** l'app non permette di creare account, quindi niente URL per l'eliminazione.

| Tipo di dati | Raccolto | Condiviso | Elaborato temporaneamente | Obbligatorio / facoltativo | Finalità |
|---|---|---|---|---|---|
| Posizione → **Posizione approssimativa** | Sì | No | Sì | Facoltativo (il meteo si spegne) | Funzionalità dell'app |
| Informazioni e prestazioni app → **Diagnostica** | Sì | No | No | Obbligatorio | Analisi |

**Perché solo questi:**
- Notifiche, contatti, calendario, app usate, audio e indirizzi dei browser vengono elaborati **solo sul telefono**. Per Play non sono "raccolti", perché non lasciano il dispositivo.
- **Home Assistant:** i dati vanno al server dell'utente, scelto e gestito da lui, non allo sviluppatore.
- **Backup di Android:** la copia giornaliera va nel backup Google dell'utente. Di solito non si dichiara, perché lo gestisce il sistema e l'app non lo riceve. Se il revisore la pensa diversamente, si aggiunge "Impostazioni app".
- **ML Kit** (dalla [pagina di Google](https://developers.google.com/ml-kit/android-data-disclosure)): manda modello e produttore del dispositivo, versione di Android, lingue configurate e codici di errore, per diagnostica e statistiche. Sono dati criptati e non passati a terzi.

## 8. Categoria e contatti della scheda
- Tipo: **App**. Categoria: **Personalizzazione** (è quella dei launcher).
- Tag: launcher, schermata Home, benessere digitale, notifiche.
- Email: la stessa dell'informativa. Sito web: facoltativo.

## 9. Dichiarazione delle autorizzazioni (Contenuti dell'app → Dichiarazione autorizzazioni)

**AccessibilityService** (richiesto, perché `isAccessibilityTool` non è dichiarato):
- **Funzionalità principale:** frenare le app che distraggono, bloccarle appena arrivano in primo piano (anche il loro sito nel browser); bloccare lo schermo con un doppio tocco; scrivere un comando in Home Assistant Assist / Signal per conto dell'utente.
- **Testo suggerito:** "Sordina è un launcher per il benessere digitale. Il servizio di accessibilità è facoltativo e ascolta solo il cambio di finestra delle app che l'utente ha scelto di frenare. Quando una di queste app, o il suo sito nel browser (letto solo dalla barra dell'indirizzo), arriva in primo piano, Sordina la ferma. Serve anche a bloccare lo schermo con un doppio tocco e, su richiesta dell'utente, a scrivere una frase dove l'app non la accetta dall'esterno: un comando in Home Assistant Assist, o il testo di un messaggio nella casella di Signal (senza inviarlo: l'invio resta all'utente). In quei casi legge la finestra di quell'app solo per pochi secondi. Nessun dato lascia il dispositivo."
- **Video** (link YouTube non in elenco, 30–60 s):
  1. l'informativa e il tocco su "Accetto";
  2. l'accensione nelle impostazioni;
  3. un'app frenata che viene fermata;
  4. il doppio tocco che blocca lo schermo;
  5. "scrivi a … su Signal": il testo compare nella casella e l'invio lo tocca l'utente.

Non servono moduli per: accesso alle notifiche, accesso all'utilizzo, contatti, calendario, posizione (solo in primo piano), microfono.
Su `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` non c'è un modulo, ma Play può chiedere perché serve. Risposta: "un launcher che smista le notifiche deve restare attivo; senza l'esenzione Android ne ritarda l'ascolto".

## 10. Passare alla produzione

Gli account sviluppatore **personali** creati dopo novembre 2023 devono fare un **test chiuso con almeno 12 tester per 14 giorni di fila** prima di poter chiedere l'accesso alla produzione. Il test interno non conta. Se nella Dashboard di Sordina compare "Richiedi l'accesso alla produzione" con questo requisito, serve un canale **Test chiuso** con una lista di almeno 12 persone che restino iscritte per 14 giorni. Con Eland Athletic l'avevi già visto, se l'account è lo stesso.

## 11. Prima della produzione, controlla la scheda
- La descrizione dice **"Il codice è open source (GPL-3.0)"**: diventa vera quando il repo è pubblico. Rendilo pubblico prima di mandare la scheda in revisione.
- I testi sono in `store/listing/`, le immagini in `store/graphics/`.
