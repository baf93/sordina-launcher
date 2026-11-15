# Da fare

- **Ripensare bene la gestione della casa.** Oggi in Impostazioni › Home c'è la sezione "Casa" con "Riconosci casa" (Wi‑Fi / posizione) e Home Assistant: va ripensata da capo, cosa serve davvero e come si imposta in modo semplice.
- **Riconoscimento dei comandi nelle altre lingue.** Oggi la ricerca capisce solo italiano e inglese (date/ore, timer, sveglia, chiama, scrivi, naviga, musica, casa, eventi senza verbo). Nelle altre 24 lingue i testi sono tradotti ma i comandi finiscono in "Cerca sul web". Partire da spagnolo, francese, tedesco, portoghese: stesso schema dell'inglese in `DateParser.english()` (riscrivere nelle forme italiane) + verbi in `Intents`, `Phrase` e `HomeTalk`. Poi rimettere tradotte le frasi d'esempio `obt_q1..4` dell'onboarding.
- **Allineare le altre 25 lingue** ai testi italiani definitivi, quando la correzione di `testi-it.txt` è finita.
