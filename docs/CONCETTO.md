# Sordina: concetto

Nome: Sordina (deciso l'11 novembre 2026; sul Play Store "Sordina Launcher"). Bozza 1, 11 novembre 2026, da rivedere insieme.

## Visione

Un modo nuovo di usare il telefono: lo prendi perché ti serve, fai la cosa, e lui torna in silenzio. Niente ti invita a restare, niente di importante ti sfugge.

Non è un launcher con meno icone. È un telefono che parte dall'intenzione e dal momento, non dalle app.

## Come nasce

- **Le impostazioni di partenza sono quelle dell'autore.** L'app nasce con un'opinione precisa su cosa significa "calmo" e funziona così dal primo minuto, senza configurare nulla.
- **Ogni utente le cambia come vuole.** Tutto quello che segue è un valore predefinito, non una regola fissa. Personalizzare è possibile, mai obbligatorio.
- **Funziona da sola.** Home Assistant è un'estensione che la rende più brava, non un requisito.

## Principi

1. **Parte vuoto.** Allo sblocco: schermo nero, ora, e al massimo un segnale discreto. Nessuna griglia, niente da scorrere.
2. **Prima l'intenzione, poi l'app.** Scrivi o dici cosa vuoi fare, e parte l'azione giusta.
3. **Il momento propone, non pretende.** Al massimo sei proposte, imparate da cosa fai di solito a quell'ora e in quel posto. Mai un elenco.
4. **L'importante passa sempre, il resto aspetta.** Regole chiare, decise dall'utente, non un algoritmo che indovina.
5. **Attrito solo per il "tanto per".** Le app usate per riflesso chiedono un momento prima di aprirsi. Quelle usate per uno scopo si aprono subito.
6. **Fatto, e torna nero.** Finita l'azione, nessun percorso che porta altrove.
7. **Un gioco leggero, mai una pressione.** Una riga a fine giornata. Imprese, serie e adesivi della mascotte festeggiano le scelte fatte, non chiedono di tornare: nessuna notifica per non perdere la serie, un'impresa vinta resta tua. Quando ne vinci una la festa dura un attimo in Casa, poi resta nella pagina dei progressi.
8. **Tutto resta sul telefono.** Niente account, niente server, niente tracciamento.

## L'esperienza

### Schermo vuoto
Ora e data. Sotto, solo se serve, una riga di stato: "Niente di urgente fino alle 13". Tocco o gesto verso l'alto per agire.

### Barra delle intenzioni
Il centro dell'app. Si scrive o si parla: "paga parcheggio", "chiama Anna", "spegni luci sala", "musica in auto".
- Riconosce app, contatti, azioni delle app (scorciatoie Android), comandi di casa se Home Assistant è collegato.
- Impara dall'uso: le intenzioni frequenti salgono in cima, sul telefono, senza inviare dati.
- Se un'intenzione apre un'app da frenare, scatta l'attrito.

### Le sei proposte
Sotto la barra, sei cose: quelle che di solito fai a quell'ora, in quel posto. Non categorie fisse, ma ciò che l'app impara usandola, tutto sul telefono.
- Possono essere app, persone o azioni ("Chiama Anna", "Verso casa", "Spegni luci sala").
- Le app da frenare non vengono mai proposte: Sordina non suggerisce le abitudini che vuoi perdere.
- Si possono fissare una o due proposte che restano sempre.
- Dettagli in [UX.md](UX.md).

### Il momento
Il telefono riconosce in che momento sei e cambia ciò che propone. Momenti predefiniti:

| Momento | Come lo riconosce | Cosa propone | Interruzioni |
|---|---|---|---|
| Mattina | Prima ora dopo la sveglia | Meteo, primo impegno, stato casa | Normali |
| Lavoro | Calendario o orario impostato | Prossima riunione, Teams, Outlook | Lavoro e persone importanti |
| In auto | Android Auto o Bluetooth dell'auto | Maps, musica, chiama | Solo chiamate |
| Sera a casa | Wi-Fi di casa dopo le 19 | Casa, musica | Persone importanti |
| Notte | Orario o Buonanotte di Home Assistant | Niente | Solo chiamate importanti e allarmi |

L'utente modifica, aggiunge o spegne i momenti.

### Riepiloghi
Ciò che non è urgente arriva in blocchi: predefiniti alle 8, alle 13 e alle 19. Ogni riepilogo raggruppa per persona, non per app. Nulla viene cancellato: se un riepilogo non lo apri, il contenuto resta al successivo.

### Messaggi: leggi qui, rispondi nell'app
Niente risposte rapide. Ogni messaggio o avviso si legge per intero dentro Sordina (mittente, testo, anteprima). Un tocco porta nell'app, nel punto giusto, esattamente come toccare la notifica originale.
- Sordina conserva l'azione di apertura della notifica anche quando la notifica di sistema viene nascosta.
- Se l'app di origine l'ha ritirata, si apre l'app nella sua schermata principale.

### Aspetto e temi
Tranquilla, non spoglia: deve essere piacevole da guardare e da usare.

La struttura delle schermate è sempre la stessa; cambia il **tema**. Un tema decide colori, caratteri, stile dell'orologio, forme e texture. L'app ne include diversi, e se ne possono aggiungere (anche dalla comunità, essendo open source).

Direzioni di partenza, da rifinire: **Carta** (chiaro, serif, caldo), **Strumento** (monocromo, puntini, monospace, un solo rosso), **Morbido** (pastello, forme tonde), più gli incroci tra loro.

Dentro ogni tema, un minimo di personalizzazione da una sola schermata:
- **Colore d'accento**: alcune tinte proposte dal tema, o dal sistema (Material You).
- **Chiaro, scuro o automatico** dove il tema lo prevede.
- **Movimento ridotto**.
- **Foto di sfondo**, a schermo intero o come cartolina appoggiata sulla carta.

Testo adattivo: con una foto di sfondo, ogni zona di testo (data, ora, frase, linea della giornata, righe in basso) diventa chiara o scura in base alla luce della foto sotto di lei. Un alone leggero del colore opposto aiuta dove la foto è mista. Il calcolo si fa una volta sola, quando si sceglie la foto. Si può anche forzare sempre chiaro o sempre scuro.

Le app si riconoscono dalla loro icona originale, piccola e ammorbidita, mai in griglia. Le persone hanno un'iniziale colorata o la foto del contatto.

### Specchio serale
Una riga: "Oggi l'hai preso 34 volte, 27 per un motivo". Facoltativo, spegnibile.

### Imprese e serie
Cento adesivi della mascotte, tutti sul detox: le scelte del freno, le giornate senza tentazioni, il tempo ripreso, le serie di giorni, la guida, le notifiche. Le regole del gioco:
- **Festeggiano, non chiedono.** Nessuna notifica, nessun promemoria per "non perdere la serie".
- **Una tentazione vale una volta.** Dopo un "Lascia stare" premiato, per 20 minuti gli altri (anche su altre app frenate) non danno minuti né imprese: aprire e lasciare stare di continuo non fa punti. "Apri comunque" invece costa sempre.
- **Quello che vinci resta.** Una serie che si interrompe non toglie le imprese già vinte.
- **Un attimo e via.** Un'impresa nuova si festeggia una volta in Casa; il resto sta nella pagina dei progressi, dove vai tu.
- **Tutto sul telefono.** Gli adesivi si condividono solo se scegli di farlo.

## Cosa passa e cosa aspetta

Tre livelli, decisi dall'utente. I predefiniti:

| Livello | Cosa contiene (predefinito) | Come arriva |
|---|---|---|
| Sempre | App scelte da te come "Sempre" (es. allarmi di casa) | Subito, anche in modalità e di notte |
| Passa | Persone importanti, codici, avvisi di pagamento sospetti, chiamate perse, promemoria, app scelte da te | Nella tendina, come la manda l'app |
| In sordina | Tutto il resto | Nella pagina Sordina, finché non la apri |

La catena delle regole è fissa e deterministica (stessa notifica, stesse regole → stesso verdetto), in quest'ordine:
regola per tipo di notifica (canale) → di sistema, chiamate, sveglie, media → "lascia passare per un po'" →
regola per persona (per app) → app frenata → codici e avvisi di pagamento → app nascosta → conversazione in corso →
app "Sempre" → persona importante → parole urgenti e insistenza (solo dai contatti, mai da numeri sconosciuti) →
regola per app → pagamenti, chiamate perse, promemoria → predefinito (in sordina).

Garanzie:
- **Il segnale di calma è sempre visibile**, così si sa che non c'è niente invece di doverlo controllare.
- **Nessuna perdita.** Ogni notifica in sordina resta nella pagina Sordina finché non la apri o la segni letta.
- **Le tue scelte vincono.** Quello che metti in sordina tu (app, persona, tipo, frenata, nascosta) non torna davanti come scheda o proposta.
- **Prova delle regole.** Una schermata mostra cosa è passato e cosa è stato trattenuto oggi, per correggere le regole.

## Fonti di contesto

| Fonte | Cosa fornisce | Disponibile per |
|---|---|---|
| Telefono | Ora, luogo (casa, lavoro), Wi-Fi, auto, ricarica, movimento | Tutti |
| Calendario | Impegni, riunioni, orari di lavoro | Tutti |
| Home Assistant | Presenza, allarmi, routine, sonno, comandi | Chi lo collega |

## Estensione Home Assistant

Cosa aggiunge:
- **Allarmi sopra il silenzioso**: un'entità o un evento scelto dall'utente diventa livello "Sempre".
- **Momenti guidati dalla casa**: Buonanotte attiva Notte, Esci e rientro cambiano il momento.
- **Comandi nella barra delle intenzioni**: script e scene di Home Assistant come intenzioni.
- **Stato in una riga** sullo schermo vuoto, se c'è qualcosa da sapere.

Come si collega: indirizzo e token creati dall'utente in Home Assistant, conservati cifrati sul telefono, usati in rete locale o tramite l'accesso remoto dell'utente. Nessun server intermedio.


## Privacy e permessi

| Permesso | Perché serve | Senza |
|---|---|---|
| App predefinita per la Home | È la Home | L'app non ha senso |
| Accesso alle notifiche | Livelli, riepiloghi, segnale di calma | Niente riepiloghi |
| Accesso a Non disturbare | Applicare i livelli di interruzione | Solo riepiloghi, senza silenziare |
| Dati di utilizzo | Attrito e specchio serale | Niente attrito né specchio |
| Posizione (facoltativa) | Riconoscere casa e lavoro | Momenti solo da orario, Wi-Fi e calendario |
| Calendario (facoltativo) | Momento Lavoro e prossimo impegno | Lavoro solo da orario |
| Contatti (facoltativo) | Persone importanti, "chiama Anna" | Persone scelte a mano |
| Microfono (facoltativo) | Ricerca a voce, riconosciuta solo sul telefono | Solo ricerca scritta |

Da evitare: servizio di accessibilità, connessione a internet per qualsiasi cosa che non sia Home Assistant.

## Limiti di Android

- La schermata di blocco resta quella del sistema. Sordina ci porta il riepilogo con una propria scheda di notifica e, facoltativamente, uno sfondo vivo del tema (vedi UX.md, 3.9).
- I gesti indietro e app recenti restano del sistema.
- Dopo alcuni aggiornamenti il sistema può rimettere la sua Home come predefinita.
- L'apertura nel punto giusto dipende dall'app di origine: se ritira la notifica, Sordina apre l'app e basta.

## Prima versione da provare

Obiettivo: una settimana sul telefono dell'autore, poi decidere.

Dentro:
- Schermo vuoto con segnale di calma
- Barra delle intenzioni con app, contatti e scorciatoie
- Sei proposte apprese per ora e luogo
- Tre livelli di notifiche e riepiloghi
- Momenti Notte e In auto
- Estensione Home Assistant minima: allarmi sopra il silenzioso e Buonanotte

Fuori, per dopo:
- Voce
- Momenti Mattina, Lavoro, Sera
- Attrito e specchio serale
- Comandi di casa nella barra

## Come capire se funziona

- Il telefono si prende meno volte al giorno, e più spesso per un motivo.
- Nessuna notifica importante persa in una settimana.
- Non viene voglia di tornare alla Home di One UI.

## Cosa esiste già (ricerca dell'11 novembre 2026)

Nessuna app trovata combina tutto, ma quasi ogni pezzo esiste da qualche parte:

| App | Cosa ha in comune | Cosa manca |
|---|---|---|
| Niagara Launcher | Riepilogo delle notifiche ogni ~6 ore (beta) | Per app, non per persona; niente intenzioni né momenti; non open source |
| Before Launcher | Filtro notifiche, home a lista di testo | Niente riepiloghi per persona, niente contesto |
| Siempo → Focus Launcher | Intenzioni e notifiche a blocchi, open source | Siempo chiusa nel 2020, ripubblicata come Focus Launcher: testo bianco e nero, timer, blocco app |
| Kvaesitso | Ricerca globale (app, contatti, calendario), calcolatrice, unità; open source GPL-3.0 | Nessuna gestione delle notifiche, nessun contesto |
| MinkLauncher | Azioni scritte con un prefisso (messaggio, evento, nota) | Niente linguaggio naturale, niente notifiche |
| Olauncher | Minimale, open source | Solo lista di app |
| Pixel Launcher | Proposte in base a uso e abitudini | Solo Pixel, nessuna calma |
| EverythingMe, Aviate | Proposte per ora e luogo, momenti | Chiusi da anni |

Cosa rende Sordina diversa: riepiloghi **per persona** con lettura del messaggio, sei proposte apprese **tutte in locale**, intenzioni che **fanno l'azione**, momenti, Home Assistant integrato, temi curati. Il rischio da tenere d'occhio è Niagara, il più diffuso.

## Decisioni

- **Open source e gratuita**, pubblicata sul Play Store con l'account sviluppatore dell'autore. Nessuna pubblicità, nessun acquisto in-app.

- **Veloce e fluida anche sui telefoni economici e vecchi**: requisito di progetto (dettagli in UX.md, Qualità).

## Domande aperte

- Nome: **Sordina** (sul Play Store "Sordina Launcher"). Deciso l'11 novembre 2026.
- Licenza: GPL-3.0, decisa.
- Anche su F-Droid, oltre al Play Store.
- Tecnologia: Kotlin e Jetpack Compose, solo Android, package app.sordina (decisi).
- Requisiti Play Store aggiornati per accesso alle notifiche, Non disturbare e dati di utilizzo.
