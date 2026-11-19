# Da fare

## Codice

Niente di aperto.

## Da provare sul telefono

- Notifiche → Oggi con notifiche vere (WhatsApp, banca, meteo): motivi giusti, a mezzanotte si svuota.
- Tieni premuta un'app nella lista: vibrazione, riga illuminata e un po' più piccola.
- Sveglia, timer, evento scritti nella ricerca: si torna a Casa e risponde il fumetto della sordina (non la barra scura). Routine e allarmi di casa: idem.
- Voce senza la lingua scaricata (Android 13+): "Scarico la lingua…" e poi funziona.
- Tempo al telefono con un'app aperta da prima di mezzanotte.
- Pulsante "Tutte le app" su uno schermo stretto (non più schiacciato).
- Disinstalla dal menu di un'app (ora c'è `REQUEST_DELETE_PACKAGES`).
- Riepilogo della sera: icone e numeri centrati, e i minuti uguali a Benessere digitale.
- "Non ce l'ho" nel primo avvio: Home Assistant spento, e si riaccende da Impostazioni.
- "Letto" su una notifica posticipata: deve sparire anche dalla tendina (T7 dell'audit).
- Avviso del budget fuori dalla fascia del freno.
- Pagina Codici e sicurezza: elenco delle app e "Non indovinare".
- Troppi tentativi d'impronta su "disattiva allarme": deve comparire il codice a mano.
- In auto: Android Auto rispetta Non disturbare e annuncia solo i preferiti.
- "Prova collegamento" di Home Assistant da casa, se l'indirizzo locale è http.

## Da provare in auto (Android Auto, anche con la Desktop Head Unit)

- Sordina compare nel launcher di Android Auto (in debug: "Sorgenti sconosciute" nelle impostazioni sviluppatore di Android Auto).
- Schede: ordine e accensione da Impostazioni › Home › Auto; una sola scheda senza voce diventa schermata semplice.
- Parla come scheda: dopo la risposta si torna alla scheda di prima, senza scatti.
- Microfono dell'auto (Android 13+, Car API 5): Sordina sente dall'auto, non dal telefono in tasca.
- Velocità: in movimento "Fermati per disattivare l'allarme"; se l'auto non dà la velocità, decidono i pulsanti "solo da fermi".
- "Impronta sul telefono": l'attività sul telefono si apre davvero mentre Android Auto è collegato.
- Codice sbagliato: si resta sul tastierino con il motivo.
- "Rispondi a voce": la risposta parte dall'azione della notifica (WhatsApp, Telegram, SMS).
- "Tutti per un'ora": passa tutto e dopo un'ora torna da solo a solo importanti.


## Da provare: il calendario di Sordina

- Impostazioni › Home › Calendario di Sordina: in Casa la data apre il calendario, un impegno apre il suo foglio; i nuovi si fanno dal + del calendario.
- Settimana, mese, cerca; "calendario" nella ricerca apre questo; colori dei calendari; impegni di più giorni in ogni giorno.
- Crea, modifica, elimina su un calendario di casa (Local Calendar) e su uno del telefono (Google).
- Impegno che si ripete: "solo questa volta" e "questa e le prossime" (casa: recurrence_range THISANDFUTURE; telefono: la serie si chiude e ne parte una nuova).
- Senza casa (Wi-Fi e dati spenti): la copia si apre, una modifica resta "in attesa" e parte da sola alla riapertura con casa raggiungibile.
- Un calendario di casa in sola lettura (es. festività): niente Modifica ed Elimina.
- Promemoria di Sordina: arriva 15 minuti prima, una volta sola, tocco apre l'impegno.

## Prima della produzione

- Revisione madrelingua delle traduzioni nuove (onboarding, regole, allarmi, schermo dell'auto) e dei comandi delle 20 lingue in `LingueAltre.kt` e delle frasi di Parla in `car/CarPhrases.kt`.
