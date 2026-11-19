# Sordina: concetto

Aggiornato al 19 novembre 2026, versione 0.2.7 (17). Il perché e le regole di fondo; il come, schermata per schermata, è in [UX.md](UX.md). La prima bozza dell'11 novembre è nella cronologia git.

## Visione

Un modo nuovo di usare il telefono: lo prendi perché ti serve, fai la cosa, e lui torna in silenzio. Niente ti invita a restare, niente di importante ti sfugge.

Non è un launcher con meno icone. È un telefono che parte da quello che vuoi fare, non dalle app.

## Come nasce

- **Le impostazioni di partenza sono quelle dell'autore.** L'app ha un'opinione precisa su cosa significa "calmo" e funziona così dal primo minuto.
- **Ogni utente le cambia come vuole.** Tutto è un valore predefinito, non una regola fissa. Personalizzare è possibile, mai obbligatorio.
- **Funziona da sola.** Home Assistant la rende più brava, non è un requisito.

## Principi

1. **Casa quasi vuota.** Data, meteo, i prossimi impegni, una barra e la sordina. Nessuna griglia, niente da scorrere.
2. **Prima l'intenzione, poi l'app.** Scrivi o dici cosa vuoi fare, e Sordina fa la cosa giusta.
3. **Propone, non fa da sola.** Una cosa alla volta, imparata da quello che fai di solito a quell'ora. A toccare sei sempre tu.
4. **L'importante passa, il resto aspetta.** Regole chiare, decise da te. Dove Sordina riconosce qualcosa dal testo (codici, urgenze), si vede perché, e si spegne.
5. **Un freno solo per il "tanto per".** Le app usate per riflesso chiedono un momento prima di aprirsi. Quelle usate per uno scopo si aprono subito.
6. **Fatto, e torna calmo.** Finita l'azione, nessun percorso che porta altrove.
7. **Un gioco leggero, mai una pressione.** Imprese, serie e adesivi della mascotte festeggiano le scelte fatte, non chiedono di tornare: nessuna notifica per non perdere la serie, quello che vinci resta tuo.
8. **Tutto resta sul telefono.** Niente account, niente server, niente tracciamento.

## L'esperienza

### Casa e il fumetto della sordina
Data, meteo, i prossimi impegni e la barra. Sotto, la sordina: tace quando non c'è niente, e quando serve parla in un fumetto che dice **una cosa sola**, la più importante in corso: il codice da copiare, la riunione che sta per iniziare, la chiamata persa, la musica, la domanda di casa. Se non c'è niente in corso, propone una cosa che di solito fai a quell'ora.

### La barra
Si scrive o si parla, in tutte le 27 lingue dell'app: "chiama Anna", "scrivi a Paolo che arrivo", "sveglia domani alle 7", "calcetto domani alle 17", "spegni luci sala".
- Fa l'azione quando può (sveglia, timer, impegno con Annulla, comando di casa); verso altre persone serve sempre il tuo tocco.
- Voce riconosciuta solo sul telefono.
- Impara dalle correzioni, dai soprannomi ("mamma"), dall'app che usi con ciascuno. Tutto in locale.
- Sotto la barra, sei app proposte per quell'ora; più su, tutte le app.

### La Casa cambia con te
Tre momenti, ognuno si accende da solo e si spegne quando vuoi:
- **Sera** (da un'ora scelta): la giornata si chiude, con un riepilogo di una riga (tempo al telefono, sblocchi), senza grafici.
- **Notte** (da un'ora scelta): solo l'essenziale, schermo scuro.
- **Auto** (Android Auto): orologio grande, navigazione, musica, le persone scelte e un microfono che si trova senza guardare.

### Sullo schermo dell'auto
Sordina è anche un'app di Android Auto, a schermo intero, accanto a Maps e alla musica. Fa solo quello che l'assistente di Google non sa fare: la casa, quello che aspetta in sordina, l'agenda. In alto la mascotte e fino a quattro schede: **Casa** (fino a sei routine di Home Assistant con "Anche in auto"), **In sordina** (chi aspetta, da ascoltare o a cui rispondere a voce, e "fai passare tutti per un'ora"), **Agenda** (gli impegni di oggi, con "Vai") e **Parla** (un tocco: Sordina ascolta, risponde a voce e torna dov'eri). Disattivare un allarme si fa solo da fermi, col codice o con l'impronta sul telefono. Le schede si accendono, spengono e ordinano in Impostazioni › Home › Auto.

### La pagina Sordina
Quello che non passa aspetta qui, raggruppato per persona (o per app, se non c'è una persona), finché non lo apri o lo segni letto. Niente riepiloghi a orario: ci vai tu quando vuoi. Nulla viene cancellato. Si legge tutto qui, e un tocco apre l'app nel punto giusto, come la notifica originale.

### Il freno
Per le app che apri per abitudine (e i loro siti nel browser): prima di aprirsi chiedono un momento. "Lascia stare" fa guadagnare minuti, livelli e giorni di serie; "Apri comunque" apre. Ogni app frenata può avere un budget giornaliero; il lunedì Sordina propone di aggiustarlo su com'è andata la settimana. Non è un blocco, è un dosso.

### Imprese e adesivi
Cento adesivi della mascotte, tutti sul distacco: le scelte del freno, le giornate senza tentazioni, il tempo ripreso, le serie, la guida, le notifiche.
- **Festeggiano, non chiedono.** Nessuna notifica, nessun promemoria per "non perdere la serie".
- **Una tentazione vale una volta.** Dopo un "Lascia stare" premiato, per 20 minuti gli altri non danno minuti né imprese.
- **Quello che vinci resta.** Una serie che si interrompe non toglie le imprese già vinte.
- **Un attimo e via.** Un'impresa nuova si festeggia una volta in Casa; il resto sta nella pagina dei progressi.
- Gli adesivi si condividono (WhatsApp, Telegram) solo se scegli di farlo.

### Aspetto
Un tema sobrio, chiaro, scuro o automatico, con un colore principale. Sfondo a tinta unita, con disegni, o lo sfondo del telefono: il testo resta leggibile su qualsiasi immagine. Una mascotte, tante facce. Su tablet una colonna centrale che ruota con lo schermo.

## Cosa passa e cosa aspetta

Tutto aspetta in sordina, tranne quello che scegli in Impostazioni → Notifiche, dal generico allo specifico:

| Tranne | Cosa contiene | Si spegne? |
|---|---|---|
| **Importanti** | Codici e sicurezza (codici di accesso, avvisi di frode, pagamenti da confermare), chiamate perse, promemoria e impegni, messaggi urgenti dai contatti, messaggi di risposta (per 15 minuti dopo che scrivi o chiami qualcuno), notifiche di sistema | Uno per uno. Codici e sicurezza no: si spengono solo per un'app che sbaglia ("non indovinare per questa app") |
| **App** | Le app che fai entrare: entra tutto, tranne i tipi di notifica o le chat che spegni nella loro pagina | Togliendo l'app, o il singolo tipo |
| **Persone** | Ovunque, o solo da un'app ("su WhatsApp"); nei gruppi solo se scelto | Togliendo la persona |

E nella direzione opposta:

| **Dalla tendina** | Un tipo di notifica che passerebbe comunque (meteo, calendario, sistema) e che vuoi in sordina. Si sceglie da quello che c'è adesso nella tendina; va in sordina solo quel tipo, mai l'app intera |
|---|---|

Più specifica è la regola, più vince. Il resto aspetta nella pagina Sordina.

In cima alla pagina Notifiche c'è **Oggi**: le notifiche arrivate da mezzanotte, passate o in sordina, ciascuna con il motivo. Si guarda e basta: per cambiare una regola si torna in Notifiche, per leggere c'è la pagina Sordina.

La catena delle regole è fissa e deterministica (stessa notifica, stesse regole, stesso verdetto), in quest'ordine:
tipo spento (salvo codici e sicurezza) → sistema, chiamate, sveglie, musica, notifiche in corso → "lascia passare per qualche ora" →
codici e avvisi di pagamento (non per le app con "non indovinare"; nei gruppi mai) → app frenata →
chat spenta o persona che entra da un'app → app nascosta (se non è fra le App) → conversazione in corso → persona importante →
App → urgenze e insistenza (solo in una chat a due, solo da chi è davvero in rubrica) → chiamate perse, promemoria →
il resto aspetta.

Un nome visto in un'app (Telegram, Signal…) vale come persona importante solo dove il nome viene dalla rubrica, o se l'hai collegato tu a quella conversazione: il collegamento è per conversazione, non per nome.

Con Non disturbare o una modalità di Android accesa aspettano anche le persone che entrano da un'app sola, le chiamate perse e i promemoria; codici e sicurezza, persone di ovunque, App e urgenze restano visibili.

Garanzie:
- **Nessuna perdita.** Sordina posticipa le notifiche con la funzione di Android, non le copia: se si fermasse, entro un giorno tutto torna da solo nella tendina.
- **Le tue scelte vincono.** Quello che metti in sordina tu (app, persona, tipo, frenata, nascosta) non torna davanti come fumetto o proposta.
- **Il freno vince.** Un'app frenata resta in sordina: passano solo codici, chiamate e il "lascia passare" chiesto per qualche ora.
- **Letto è letto.** Una notifica letta nell'app di origine esce anche dalla sordina.

## Home Assistant

Facoltativo, con token (risposte nel fumetto) o solo con l'app di Home Assistant installata.
- **Comandi dalla barra**, nella tua lingua, passati ad Assist: luci, tapparelle, scene, domande.
- **La casa chiede**: le notifiche con risposte di Home Assistant compaiono nel fumetto, con fino a tre risposte.
- **Routine** come "Esco" o "Rientro" in un tocco, con o senza impronta, anche legate a un allarme (inserisci dopo, disattiva prima).
- **Allarmi**: "disattiva allarme", "inserisci allarme notte", con il codice cifrato sul telefono e aperto solo dall'impronta.
- **Sicurezza prima**: aprire porte, cancelli, garage e serrature, o disattivare un allarme, chiede impronta o PIN, da voce, tastiera o proposta. Chiudere, bloccare e inserire restano immediati.

Indirizzo e token restano cifrati sul telefono. Nessun server intermedio.

### Il calendario di Sordina
Facoltativo (Impostazioni › Home, sotto Impegni). I calendari di casa e quelli del telefono in un posto solo, nello stile di Sordina, senza aprire l'app di Home Assistant: elenco con la settimana sopra, mese, cerca, "+" per un impegno nuovo; tocchi un impegno e lo vedi, lo modifichi o lo elimini. Dei calendari di casa Sordina tiene una copia da un anno prima a un anno dopo il mese corrente: si apre anche senza casa, e quello che cambi parte appena Home Assistant risponde (se lo rifiuta, Sordina lo dice). Cosa si può fare lo decide ogni calendario. I promemoria, 15 minuti prima, li può fare anche Sordina, solo per i calendari di casa e spenti all'inizio (spesso li fa già Home Assistant; quelli del telefono hanno i loro).

## Privacy e permessi

Escono dal telefono solo:
- la posizione arrotondata a ~10 km verso Open-Meteo, per il meteo, al massimo una volta l'ora;
- i comandi verso il **tuo** Home Assistant, se lo colleghi;
- una copia giornaliera delle impostazioni e di ciò che Sordina ha imparato (senza notifiche né token) nel backup Google, se il backup è acceso;
- i dati di diagnostica di ML Kit (modello, versione di Android, lingue, codici di errore), per le librerie di testo e QR sul telefono.

| Permesso | Perché serve |
|---|---|
| Accesso alle notifiche (l'unico necessario) | Decidere cosa passa e cosa aspetta; codici, riunioni e immagini nel fumetto |
| Home predefinita | È la Home |
| Dati di utilizzo | Ordinare le app nella ricerca, freno, riepilogo della sera |
| Accesso a Non disturbare | Silenzio a tempo ("silenzio per 45 minuti") e auto |
| Notifiche | La scheda di stato e gli avvisi di Sordina |
| Contatti | "Chiama Anna", persone importanti, persone in auto |
| Calendario | Prossimi impegni, impegni scritti nella barra |
| Posizione approssimativa | Solo meteo |
| Microfono | Voce, riconosciuta sul telefono |
| Telefono | Fare la chiamata dopo il tuo tocco |
| Servizio di accessibilità | Freno istantaneo (anche sul sito dell'app frenata, leggendo solo la barra degli indirizzi), doppio tocco per spegnere lo schermo, scrivere una frase in Assist o in Signal. Spiegato prima, con consenso esplicito |
| Esenzione dall'ottimizzazione della batteria | Perché Android non addormenti lo smistamento delle notifiche |

## Limiti di Android

- La schermata di blocco resta quella del sistema. Sordina ci porta una scheda di stato silenziosa.
- I gesti indietro e app recenti restano del sistema.
- Dopo alcuni aggiornamenti il sistema può rimettere la sua Home come predefinita.
- L'apertura nel punto giusto dipende dall'app di origine: se ritira la notifica, Sordina apre l'app e basta.
- Un launcher non può leggere l'elenco dei tipi di notifica di un'app: Sordina li impara man mano che arrivano.
- Android Auto annuncia una notifica appena arriva, prima che Sordina la metta in sordina: in auto è Non disturbare a doverla fermare.
- Sullo schermo dell'auto Sordina non può avere la scheda piccola dello schermo diviso (è solo di navigazione e musica) né widget: per ora è solo a schermo intero.

## Come capire se funziona

- Il telefono si prende meno volte al giorno, e più spesso per un motivo.
- Nessuna notifica importante persa in una settimana (la pagina Oggi serve anche a verificarlo).
- Non viene voglia di tornare alla Home del telefono.

## Cosa esiste già (ricerca dell'11 novembre 2026)

| App | Cosa ha in comune | Cosa manca |
|---|---|---|
| Niagara Launcher | Riepilogo delle notifiche ogni ~6 ore (beta) | Per app, non per persona; niente intenzioni; non open source |
| Before Launcher | Filtro notifiche, home a lista di testo | Niente raggruppamento per persona, niente contesto |
| Focus Launcher (ex Siempo) | Intenzioni e notifiche a blocchi, open source | Testo bianco e nero, timer, blocco app |
| Kvaesitso | Ricerca globale, calcolatrice, unità; open source GPL-3.0 | Nessuna gestione delle notifiche |
| MinkLauncher | Azioni scritte con un prefisso | Niente linguaggio naturale, niente notifiche |
| Olauncher | Minimale, open source | Solo lista di app |
| Pixel Launcher | Proposte in base all'uso | Solo Pixel, nessuna calma |

Cosa rende Sordina diversa: notifiche raggruppate **per persona** con lettura del messaggio, comandi che **fanno l'azione** in 27 lingue, proposte apprese **tutte in locale**, Home Assistant integrato con sicurezza a impronta, freno con budget. Il rischio da tenere d'occhio è Niagara, il più diffuso.

## Decisioni

- Nome **Sordina**, sul Play Store "Sordina Launcher".
- **Open source e gratuita**, licenza GPL-3.0. Nessuna pubblicità, nessun acquisto in-app.
- Kotlin e Jetpack Compose, solo Android 8+, package `app.sordina`.
- **Veloce e fluida anche sui telefoni economici e vecchi.**
- Niente riepiloghi a orario: in sordina vuol dire "finché non la apri".
- Imprese e serie restano, con le regole del gioco leggero (principio 7).
- L'accessibilità si usa, solo per le funzioni elencate sopra e con consenso esplicito.

## Domande aperte

- Pubblicazione anche su F-Droid.
- Revisione madrelingua delle traduzioni nuove e dei comandi delle 20 lingue in `LingueAltre.kt`.
