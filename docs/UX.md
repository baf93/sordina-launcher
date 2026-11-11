# Sordina: funzionalità ed esperienza d'uso

Bozza 1, 11 novembre 2026. Completa [CONCETTO.md](CONCETTO.md): là il perché, qui il come. I punti segnati **[da decidere]** sono da rivedere insieme.

## 1. Mappa e gesti

Una sola schermata di partenza, tutto il resto è a un gesto di distanza e torna sempre lì.

| Da Casa | Porta a |
|---|---|
| Scorri in su | **Intenzione**: barra e, sotto, le sei proposte del momento |
| Scorri in su due volte (o "Tutte le app" sotto le proposte) | **Tutte le app**: elenco alfabetico completo |
| Scorri in giù | Tendina notifiche di sistema (modificabile: In attesa) |
| Tocco su "in attesa" o sulla linea della giornata | **In attesa** (il riepilogo corrente) |
| Tocco sulla scheda Casa | Pannello Home Assistant rapido (se collegato) |
| Pressione lunga sul vuoto | **Impostazioni**, aperte su Aspetto |
| Pressione lunga sul momento | Cambia momento a mano, o "nessun momento" |
| Tasto Home o Indietro, da ovunque | Casa |

Regole:
- Nessuno scorrimento infinito, in nessuna schermata (Tutte le app è un elenco finito, alfabetico).
- Dopo aver aperto un'app, al ritorno si trova sempre Casa, mai l'ultima schermata di Sordina.
- Animazioni brevi (200–300 ms), dissolvenze, niente rimbalzi.

## 2. Primo avvio

Dieci passi al massimo, tutti saltabili tranne il primo. L'app funziona anche se l'utente salta tutto.

1. **Benvenuto**: una frase sul perché, nessun carosello.
2. **Imposta come Home**.
3. **Tema**: Carta, Quaderno, Strumento, Morbido…, con anteprima dal vivo. Chiaro, scuro o automatico.
4. **Sfondo**: nessuno, foto (velo o cartolina).
5. **Persone importanti**: proposte dai contatti preferiti e dai più chiamati; si conferma con un tocco.
6. **Notifiche**: permesso di accesso, spiegato con l'esempio "Anna passa subito, il gruppo calcetto aspetta le 13".
7. **Non disturbare**: permesso, spiegato con "di notte suonano solo le persone importanti e gli allarmi".
8. **Dati di utilizzo**: permesso per imparare le abitudini. Sordina legge le ultime settimane di utilizzo, così le sei proposte sono già sensate dal primo giorno.
9. **Home Assistant** (facoltativo): indirizzo e token, scelta degli allarmi che passano sempre.
10. **Fatto**: si arriva su Casa, già silenziosa.

Ogni permesso negato ha un percorso: l'app spiega cosa non potrà fare e lo ricorda una sola volta, dopo una settimana.

## 3. Le schermate

### 3.1 Casa
- Data, meteo (facoltativo), ora, frase di stato.
- Linea della giornata: dalle 6 a mezzanotte (orari modificabili), pallini per i riepiloghi, segno per l'ora attuale.
- Fino a due righe sotto: Casa (se c'è Home Assistant), In attesa (se c'è qualcosa). Se non c'è niente, non c'è la riga.
- Durante un momento: la frase lo dice ("In auto · solo chiamate") e le tre proposte del momento compaiono sopra la linea della giornata.

### 3.2 Intenzione
- Si apre **senza tastiera**: barra in alto, sei proposte ben visibili sotto. Tocco sulla barra per scrivere, microfono per parlare. Opzione per chi preferisce la tastiera subito aperta.
- Scorrendo ancora in su: Tutte le app.
- Mentre scrivi, al massimo cinque risultati, in quest'ordine di tipo ma ordinati per uso:
  1. Azioni (scorciatoie delle app, comandi Home Assistant, "chiama X")
  2. Persone
  3. App
  4. Impostazioni di sistema (Wi-Fi, Bluetooth, torcia…)
  5. In fondo, sempre: "Cerca sul web" con il browser predefinito
- Riconosce anche i calcoli semplici e le unità ("12*3", "5 km in miglia") e risponde lì.
- Invio apre il primo risultato (con le regole di sicurezza del motore delle intenzioni, qui sotto).

**Voce, senza cloud**
- Microfono a destra della barra; tieni premuto, parli, rilasci.
- Riconoscimento solo sul telefono: Android 12 e successivi hanno un riconoscitore garantito sul dispositivo (lo stesso modello che usa la dettatura offline). Sordina usa solo quello, mai il riconoscitore normale che può passare dal cloud.
- Se sul telefono manca il pacchetto della lingua, Sordina lo dice e porta all'impostazione per scaricarlo; non ripiega mai sul cloud. **[da verificare su One UI 9 con l'italiano]**
- Alternativa open source se il sistema non lo offre: modello Vosk italiano (circa 50 MB), scaricato solo su richiesta.
- Quello che dici finisce nella barra come testo e passa dalle stesse regole della ricerca scritta: "chiama Anna", "spegni luci sala", "Spotify". Nessuna intelligenza artificiale esterna.
- In auto il microfono diventa il pulsante più grande.
- Serve il permesso del microfono, chiesto solo al primo uso.
- Al riconoscitore vengono passati come suggerimento i nomi dei contatti, delle app e dei dispositivi di casa, così nomi insoliti di persone, app o dispositivi vengono capiti meglio (funzione di Android 13 e successivi).

**Motore delle intenzioni**

Obiettivo: la prima proposta è quella giusta quasi sempre, e quando non lo è, **non succede mai la cosa sbagliata in silenzio**.

Perché è realistico: non deve capire qualsiasi frase del mondo. Deve scegliere tra cose che esistono sul *tuo* telefono (app, contatti, scorciatoie, dispositivi di casa, impostazioni), e la maggior parte di ciò che si fa ogni giorno è ripetitivo.

Come funziona, tutto sul telefono:
1. **Catalogo**: indice locale di app, scorciatoie delle app, contatti, entità e script di Home Assistant (con stanze), impostazioni di sistema. Aggiornato quando qualcosa cambia.
2. **Normalizzazione** (regole dal pacchetto della lingua, vedi Lingue): minuscole, accenti, numeri scritti a parole, sinonimi ("luce/luci", "spegni/stacca", "mamma" → contatto scelto).
3. **Frasi con struttura**: una quindicina di verbi con parti variabili.
   - chiama *persona* · scrivi a *persona* (*testo*) · naviga verso *luogo*
   - accendi/spegni/apri/chiudi *dispositivo* (*stanza*) · imposta *dispositivo* a *valore*
   - sveglia alle *ora* · timer *durata* · promemoria *testo* *quando*
   - metti *musica/artista/playlist* · apri *app*
4. **Corrispondenza tollerante**: errori di battitura e di riconoscimento vocale ("ana", "spotfy"), confronto anche per suono.
5. **Classifica personale**: a parità di testo vince ciò che usi di più a quell'ora e in quel posto, come per le sei proposte.
6. **Impara dalle correzioni**: se scegli il secondo risultato, quella frase porterà lì la prossima volta. Gli alias si possono anche scrivere a mano ("pane" → lista della spesa).

Cosa può fare un'intenzione, senza servizio di accessibilità:
| Livello | Esempi | Come |
|---|---|---|
| Fatto senza aprire nulla | "timer pasta 10 minuti", "sveglia alle 7", "spegni luci sala", "torcia", "pausa", "prossima canzone" | Funzioni di sistema (sveglie, torcia, controllo della musica in riproduzione) e API di Home Assistant |
| App aperta nel punto giusto | "verso casa" (navigazione già partita), "Daily Mix" (playlist in riproduzione), "chat con Paolo su Telegram", "evento domani alle 15" | Collegamenti diretti e scorciatoie che le app espongono |
| Pronto, manca il tuo tocco | "scrivi a Anna che arrivo tra 10 minuti" → chat WhatsApp aperta col testo già scritto; "chiama Anna" | Le app non permettono di inviare al posto tuo: premi Invia |
| Non possibile | Pagare il parcheggio, inviare un messaggio senza toccare, premere pulsanti dentro altre app | Richiederebbe il servizio di accessibilità, escluso per privacy e per le regole del Play Store. Qui Sordina apre l'app e basta |

**Dove finisce l'azione** (quale app, quale calendario):

Regola generale: un **predefinito sensato**, una **scheda che mostra cosa sta per succedere** con la destinazione cambiabile con un tocco, e **la scelta si impara**. Se la frase la dice ("su Telegram", "nel calendario lavoro"), vince la frase.

| Azione | Come sceglie la destinazione | Quanto è affidabile |
|---|---|---|
| Impegno ("aggiungi calcetto alle 17") | Calendario predefinito scelto al primo avvio, tra quelli sincronizzati su Android (Google, Exchange…) o un calendario di Home Assistant. Inserito direttamente, senza aprire app. | Alta: scrittura diretta nel calendario |
| Messaggio a una persona | L'app con cui *quella persona* ti scrive di solito, ricavata dalle notifiche ricevute e dai profili collegati al contatto (WhatsApp, Telegram, Signal…). Se nessuna: SMS. | Alta per WhatsApp e SMS (chat giusta, testo già scritto); media per Telegram, che non sempre apre la chat giusta da numero di telefono |
| Chiamata | Telefono, oppure l'app usata di solito per chiamare quella persona | Alta |
| Promemoria | App promemoria predefinita o lista di Home Assistant (es. spesa) | Media: dipende dall'app scelta |
| Musica | Ultima app musicale usata | Alta per Spotify |

Comprensione di date e ore in italiano ("alle 17", "domani alle 17", "giovedì", "tra mezz'ora", "stasera"): con regole scritte apposta e una raccolta di frasi di prova. Se "alle 17" è già passato, si intende domani e la scheda lo dice. Durata predefinita di un impegno: un'ora.

Azioni verso te stesso (impegno, promemoria, timer): fatte subito, con **Annulla** visibile per qualche secondo. Azioni verso altri (messaggi, chiamate): sempre con il tuo tocco finale.

Più avanti, **sequenze** definite dall'utente: una frase, più passi del primo e secondo livello. Esempio "vado al lavoro" = navigazione verso l'ufficio + podcast su Spotify + momento Lavoro + script "Esci" di Home Assistant.

Quando agire e quando chiedere:
| Sicurezza | Azione verso te o la casa (apri app, luci, timer) | Azione verso altri (chiama, invia messaggio) |
|---|---|---|
| Alta | La fa subito | Mostra un pulsante grande già pronto, un tocco (o "sì") per confermare |
| Media | Mostra le prime tre, la prima evidenziata | Mostra le prime tre |
| Bassa | "Cerca sul web" in cima | "Cerca sul web" in cima |

Le azioni verso altre persone chiedono sempre conferma: una chiamata alla persona sbagliata è il peggior errore possibile.

Facoltativo, più avanti: per le frasi libere che le regole non capiscono, un modello linguistico **sul telefono** se Android lo offre (sui telefoni compatibili, tramite i servizi AI del sistema). Mai cloud. **[da verificare se disponibile sul S24 FE]**

Come si misura: Sordina conta in locale quante volte la prima proposta era quella scelta. Pagina in Impostazioni → Dati, utile per capire se il 99% è vero. In sviluppo: una raccolta di frasi di prova (scritte e dettate) da far passare a ogni versione.

### 3.3 Le sei proposte
Quello che di solito fai a quell'ora, in quel posto.

**Cosa può essere proposto**: app, persone ("Chiama Anna", "Scrivi a Paolo"), scorciatoie delle app ("Verso casa", "Paga parcheggio"), comandi Home Assistant ("Spegni luci sala").

**Come impara** (tutto sul telefono):
- Ogni volta che apri qualcosa, Sordina annota ora, giorno feriale o festivo, luogo e momento.
- Il luogo è approssimato e senza GPS continuo: Wi-Fi di casa o del lavoro, auto collegata, zona di Home Assistant. Se non è noto, conta solo l'ora.
- Punteggio di ogni candidato: quante volte l'hai usato in una fascia di un'ora attorno a adesso, nello stesso tipo di giorno e luogo, con più peso alle ultime due settimane.
- Conta anche la sequenza: se dopo Android Auto apri sempre Spotify, Spotify sale.
- Primo giorno: parte dalla cronologia di utilizzo di Android delle ultime settimane (solo ora e giorno, il luogo si impara da lì in poi).

**Regole di esperienza**:
- **Stabili**: la disposizione cambia solo al cambio di fascia oraria, di luogo o di momento, mai mentre guardi. Così la mano impara dove sono le cose.
- **Mai le app da frenare**: anche se le apri spesso, non vengono proposte.
- **Varie**: al massimo due voci della stessa app (es. due chat WhatsApp).
- **Fissabili**: pressione lunga, "Tieni qui". Al massimo due fisse.
- **Spiegabili**: pressione lunga, "Perché è qui?" → "La apri quasi ogni giorno feriale tra le 8 e le 9, a casa."
- **Correggibili**: pressione lunga, "Non propormela più" (in questo momento o mai).

**In auto e negli altri momenti**: le stesse proposte, ma solo le prime tre e più grandi.

### 3.3 bis Tutte le app
La rete di sicurezza: per l'app che usi una volta l'anno e non ricordi come si chiama.
- **Come ci arrivi**: dall'Intenzione, la voce discreta "Tutte le app" sotto le sei proposte, o scorrendo ancora in su. In più, scrivere nella barra trova comunque qualsiasi app.
- **Com'è**: elenco alfabetico di soli nomi, nel carattere del tema, con un'icona piccola e attenuata accanto. Nessun pallino di notifica, nessun "più usate", nessuna sezione "nuove".
- **Indice delle lettere** sul bordo destro per saltare: si scorre col pollice, una lettera alla volta.
- **Filtro** in cima: scrivere restringe l'elenco (è la stessa barra delle intenzioni, limitata alle app).
- **Pressione lunga** su un'app: Informazioni app, Disinstalla, Frena (attrito), Nascondi dall'elenco.
- **App nascoste**: in fondo all'elenco una riga "Nascoste (3)", chiusa. Restano raggiungibili, solo non in vista.
- **Profilo di lavoro**: se esiste, una sezione separata in fondo con le sue app, con l'interruttore per sospenderlo.
- Le app da frenare mantengono l'attrito anche da qui.
- Aprire un'app da qui vale come uso: anche lei può salire tra le proposte, se diventa un'abitudine.

### 3.4 In attesa
- Raggruppata per persona o, se non c'è una persona, per app.
- Ogni gruppo mostra l'ultimo messaggio intero e il numero degli altri.
- Tocco sul gruppo: si espande con tutti i messaggi.
- Tocco su un messaggio: apre l'app nel punto giusto (azione originale della notifica).
- "Letto" per gruppo e "Tutto letto" in fondo. Quello che leggi nell'app di origine sparisce da solo.
- Nulla scade: resta finché non lo leggi.

### 3.5 Momento attivo
- Le prime tre delle sei proposte, grandi, direttamente in Casa.
- Cambio di momento discreto: la frase cambia con una dissolvenza, nessun avviso.

### 3.6 Attrito
- Solo per le app scelte come "da frenare".
- Schermata a tutto tema: nome dell'app, "Perché lo apri?", due scelte (un motivo / per abitudine), conto delle aperture di oggi.
- L'app si apre dopo 5 secondi (modificabile, da 0 a 15) o subito se scegli "un motivo".
- "Per abitudine" non blocca: registra e apre. Lo specchio serale lo mostrerà.

### 3.7 Specchio serale
- Una scheda in Casa dopo le 21 (orario modificabile), una sola frase e un dettaglio a tocco.
- Nessun grafico, nessun obiettivo, nessuna serie di giorni.

### 3.8 Impostazioni
Una sola lista, in quest'ordine:
1. Aspetto: tema, chiaro/scuro, accento, sfondo, orologio, movimento ridotto
2. Persone importanti
3. Cosa passa e cosa aspetta: livello per app, eccezioni per canale
4. Riepiloghi: orari
5. Momenti: attivi, come si riconoscono, cosa propongono
6. Proposte: fisse, escluse, azzera ciò che ha imparato
7. App da frenare
8. Home Assistant
9. Dati: esporta e importa la configurazione (file), cancella tutto
10. Informazioni: versione, codice sorgente, licenza

Aggiunta: **Schermata di blocco** (cosa mostrare, quanto dettaglio), tra Riepiloghi e Momenti.

### 3.9 Schermata di blocco: il riepilogo al volo
Prendi il telefono, vedi cosa ti aspetta senza sbloccare, poi sblocchi solo se serve.

Vincolo: su Android nessuna app può sostituire la schermata di blocco (PIN, impronta, orologio restano del sistema). Sordina ci entra con due strumenti che funzionano su tutti i telefoni:

**1. La scheda di Sordina (base, ovunque)**
- Una sola notifica silenziosa e fissa di Sordina, sempre in cima: stato + riepilogo per persona.
  - Chiusa: "Niente di urgente fino alle 19 · Anna, Paolo e altri 4"
  - Aperta: una riga per persona con l'inizio del messaggio
- Le notifiche trattenute non compaiono sul blocco (sono posticipate): resta solo questa scheda e le cose importanti.
- Tocco su una persona: sblocchi e arrivi direttamente nella chat giusta. Tocco sulla scheda: sblocchi e arrivi in In attesa.
- Pulsanti (al massimo tre): "Letto tutto", e le due persone più recenti.
- Stile: segue il tema nei limiti che Android concede alle notifiche (colore d'accento, icona, testo); non può avere la grana o il carattere del tema.
- Va bene anche sull'Always On Display, dove il telefono lo supporta, e sullo smartwatch se l'utente lo vuole (spento di default).

**2. Sfondo vivo del tema (facoltativo, la parte bella)**
- Sordina fornisce uno sfondo animato che, solo quando il telefono è bloccato, disegna in stile tema: linea della giornata, frase di stato, e il riepilogo per persona.
- Si ridisegna solo quando cambia qualcosa; fermo a schermo spento. Consumo trascurabile.
- Sulla Home lo stesso sfondo mostra solo la foto o la carta, senza testi.
- Da verificare: alcuni produttori limitano gli sfondi animati sul blocco, o li condividono con la Home. Se non è possibile, resta la scheda (punto 1).

**Privacy** (Impostazioni → Schermata di blocco):
| Livello | Cosa si vede bloccato | Default |
|---|---|---|
| Solo numeri | "3 in attesa" | |
| Nomi | "Anna, Paolo, Calcetto" | ✓ |
| Messaggi | "Anna: passi tu a prendere il pane?" | |
Rispetta anche l'impostazione di sistema "nascondi contenuti sensibili": se attiva, al massimo i nomi.

## 4. Notifiche: come funziona davvero

Il cuore dell'app. Deve essere affidabile prima che bello.

### 4.1 Classificazione
Ogni notifica che arriva passa queste regole, la prima che vale decide:

1. **Sempre passano, senza toccarle**: chiamate, sveglie e timer, navigazione in corso, musica in riproduzione, download e caricamenti in corso, notifiche di sistema.
2. **Codici di accesso** (OTP, codici di verifica, conferme di pagamento): Subito in silenzio, riconosciuti dal testo.
3. **Allarmi Home Assistant scelti**: Sempre.
4. **Persone importanti**: Sempre (chiamate) o Subito in silenzio (messaggi), secondo il momento.
5. **Livello dell'app** scelto dall'utente.
6. **Default**: Riepilogo.

### 4.2 Cosa succede a ogni livello
| Livello | Tendina di sistema | Suono | In Sordina |
|---|---|---|---|
| Sempre | Resta | Sì, anche di notte | In Casa, in evidenza |
| Subito in silenzio | Resta | No | Conta in "In attesa" |
| Riepilogo | Nascosta fino al riepilogo | No | In "In attesa" |

### 4.3 Meccanismo Android
- Sordina tiene attivo Non disturbare in modalità priorità.
- Le persone importanti diventano i **contatti preferiti** di Android: così le loro chiamate suonano anche con Non disturbare, gestito dal sistema e quindi affidabile.
- Le notifiche trattenute vengono **posticipate** fino all'ora del riepilogo (funzione standard di Android), non cancellate: l'azione di apertura resta valida.
- Per gli allarmi "Sempre" che non sono chiamate, Sordina pubblica una propria notifica su un canale che supera Non disturbare. **[da verificare su One UI 9]**
- Al riepilogo: una sola notifica di Sordina, silenziosa, "Riepilogo delle 13 · 6 cose".

### 4.4 Garanzie
- Se Sordina si chiude o il telefono si riavvia, nessuna notifica resta nascosta per sempre: le posticipate tornano comunque da sole.
- Pagina "Oggi": cosa è passato, cosa è stato trattenuto e perché. Da lì, con un tocco, "questa app passa sempre" o "questa aspetta".

## 5. Momenti: riconoscimento
| Momento | Entra | Esce |
|---|---|---|
| Notte | Orario o Buonanotte di HA | Sveglia spenta, Buongiorno di HA, o orario |
| Mattina | Sveglia spenta | Dopo un'ora o all'arrivo al lavoro |
| Lavoro | Calendario o orario, nei giorni scelti | Fine orario o uscita dal luogo |
| In auto | Android Auto o Bluetooth dell'auto | Disconnessione |
| Sera a casa | Wi-Fi di casa dopo le 19 | Notte |

Priorità se ne valgono due: In auto > Notte > Lavoro > Sera > Mattina. Il cambio a mano vale fino al prossimo cambio automatico.

## 6. Casi limite da gestire

- **Riavvio o aggiornamento**: Sordina si riattiva da sola; se Android ha rimesso One UI come Home, una notifica lo dice con il pulsante per correggere.
- **Sordina disattivata dall'utente**: Non disturbare torna come prima, le notifiche posticipate vengono rilasciate subito.
- **Emergenze**: chiamate ripetute dello stesso numero entro 15 minuti suonano sempre (opzione di sistema, attivata di default).
- **Telefono ospite**: chi prende il telefono vede Casa e la barra; nessun dato in più è visibile senza sblocco (la schermata di blocco resta quella del sistema).
- **App nuova installata**: le sue notifiche vanno in Riepilogo; Sordina la segnala una volta in "In attesa".

## 7. Qualità

- **Velocità**: Casa visibile in meno di 100 ms dal tasto Home, risultati della barra in meno di 50 ms. **Misurati su un telefono economico di qualche anno fa** (2–3 GB di RAM, processore di fascia bassa), non sul S24 FE.
- **Telefoni lenti**, requisito di progetto e non ottimizzazione finale:
  - Android 8 come minimo; le funzioni che richiedono versioni recenti (voce sul telefono, sfondo vivo sul blocco) si spengono da sole dove non ci sono.
  - Modalità leggera automatica sui telefoni con poca RAM: niente sfocature né trasparenze, grana della carta come immagine già pronta, animazioni ridotte a dissolvenze brevi, sfondo statico al posto di quello vivo.
  - Icone delle app caricate solo quando servono e in piccolo; indice di ricerca in un database locale, non in memoria.
  - Memoria in uso sotto i 60 MB, app sotto i 10 MB (i modelli vocali facoltativi si scaricano a parte).
  - Avvio ottimizzato per la Home (profili di avvio precompilati).
  - Un telefono economico vero tra i telefoni di prova, usato a ogni versione.
- **Batteria**: nessun lavoro in background oltre all'ascolto delle notifiche e alla connessione Home Assistant (se attiva). Obiettivo: meno dell'1% al giorno.
- **Accessibilità**: TalkBack, testo grande, contrasto verificato in tutti i temi e con il testo adattivo.
- **Lingue**: vedi sotto.

### Lingue

Tre parti, con difficoltà molto diverse:

| Parte | Difficoltà | Come |
|---|---|---|
| Testi dell'interfaccia | Bassa | Risorse standard di Android, tradotte dalla comunità su una piattaforma di traduzione gratuita per progetti open source (es. Weblate). |
| Voce | Bassa | Il riconoscitore sul telefono supporta le lingue che l'utente ha scaricato; Vosk copre una ventina di lingue come alternativa. |
| Motore delle intenzioni | Alta | **Pacchetti di lingua**, vedi sotto. |

**Pacchetti di lingua.** Il motore è indipendente dalla lingua; tutto ciò che è linguistico sta in un file per lingua, scritto e corretto anche da chi non programma:
- verbi e sinonimi ("chiama", "call", "appelle", "llama")
- espressioni di data e ora ("domani alle 17", "tomorrow at 5 pm", "demain à 17 h")
- parole di collegamento ("su", "nel", "a", "on", "in")
- frasi di prova: ogni pacchetto porta le sue, e vengono verificate a ogni versione.

**Cosa funziona anche senza pacchetto.** La ricerca per nome (app, contatti, dispositivi di casa, impostazioni) funziona in qualsiasi lingua, perché i nomi sono già nella lingua dell'utente. Mancano solo le frasi con struttura ("scrivi a X …", "impegno alle …"). Nessuna lingua è quindi esclusa: parte dal livello base e migliora quando arriva il pacchetto.

**Da riusare**: Home Assistant mantiene una raccolta aperta di frasi per comandi di casa e timer in decine di lingue. Si potrebbe partire da lì per quei comandi. **[da verificare licenza e compatibilità]**

**Il resto dell'interfaccia**:
- Lingue scritte da destra a sinistra (arabo, ebraico): layout specchiato, già supportato da Android.
- Ogni tema dichiara un carattere di riserva per alfabeti che il suo carattere non copre (cinese, giapponese, coreano, cirillico…).
- Formati di ora, data e numeri presi dalle impostazioni del telefono.

**Piano**: 1.0 con italiano e inglese completi (interfaccia e pacchetto). Interfaccia nelle altre lingue man mano che la comunità traduce; pacchetti di lingua in ordine di richiesta, partendo da spagnolo, francese, tedesco, portoghese.
- **Dati**: tutto in un database locale; unico accesso a internet verso l'indirizzo Home Assistant scelto dall'utente.

## 8. Versioni

| Versione | Contiene |
|---|---|
| 0.1 · prova personale | Casa, Intenzione, sei proposte apprese, In attesa, notifiche a tre livelli, momenti Notte e In auto, temi Carta e Quaderno, HA minimo |
| 0.2 · amici | Primo avvio completo, impostazioni complete, pagina "Oggi", esporta e importa |
| 1.0 · Play Store | Tutti i momenti, attrito, specchio, altri temi, inglese, accessibilità verificata |

## 9. Da decidere

1. ~~Scorri in giù~~: deciso, tendina di sistema come default (è ciò che ci si aspetta su Android), modificabile.
2. Doppio tocco per bloccare lo schermo: richiede il servizio di accessibilità o l'amministrazione dispositivo. Proposta: niente.
3. Meteo in Casa: richiede internet. Da Home Assistant se collegato, altrimenti niente?
4. Widget delle altre app (es. Garmin, Spotify): proposta niente, per coerenza.
5. Nome dell'app.
