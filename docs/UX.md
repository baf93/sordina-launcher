# Sordina: come funziona

Versione 3, 19 novembre 2026 (app 0.2.7). Il perché è in [CONCETTO.md](CONCETTO.md); le versioni precedenti sono nella cronologia git.

**In una riga:** prendi il telefono, fai la cosa, lui torna in silenzio. Sordina impara cosa fai e te lo propone; a fare sei sempre tu.

Principio: **KISS & Smart**. Poche cose sullo schermo, frasi di 3–4 parole, nessuna spiegazione nell'interfaccia. L'intelligenza si vede solo quando serve.

## 1. Tre gesti

| Dove | Cosa c'è |
|---|---|
| **Casa** | Data, meteo, i prossimi impegni, la barra e la **sordina col suo fumetto** (con il numero di quelle in attesa) |
| **Su** | Barra (scrivi o parli) e sei app proposte. Ancora su: tutte le app |
| **Giù** | La tendina di Android, intatta |

Tasto Home o Indietro: sempre Casa. Pressione lunga sul vuoto: impostazioni. Doppio tocco sul fondo (facoltativo, con l'accessibilità): spegne lo schermo.

La Casa cambia nei momenti accesi in Impostazioni → Home: **Sera** (da un'ora scelta, con il riepilogo di una riga: tempo al telefono, sblocchi), **Notte** (da un'ora scelta, solo l'essenziale) e **Auto** (Android Auto: orologio grande, fino a 3 app in una riga, fino a 4 persone con la foto, microfono grande).

## 2. Casa e il fumetto della sordina

Sotto la barra di ricerca c'è una zona alta sempre uguale, così la barra non si sposta mai. Lì sta la sordina: al centro quando non ha niente da dire, a sinistra quando parla. Il tocco sulla sordina apre sempre le notifiche in attesa (il numero le sta addosso mentre parla).

Il fumetto dice **una cosa sola**: la più importante in corso, oppure, se non c'è niente in corso, quello che di solito fai adesso.

- **Tocco sul fumetto:** apre la cosa (l'app, la chiamata, la riunione).
- **Il tondo:** l'azione principale, senza aprire niente (copia, richiama, pausa, partecipa, naviga). La musica ha anche il brano dopo.
- **Scorri di lato:** via. Le altre cose in corso, se ci sono ("· +1"), vengono avanti.
- **Tieni premuto:** le altre azioni e "nascondi per oggi".
- Titolo e riga sotto vanno su due righe quando serve: il testo non si taglia.

### Cose in corso

| Fumetto | Tondo | Da dove lo sa |
|---|---|---|
| Sordina ferma | Sistema | Sordina stessa |
| Codice OTP (barra che si svuota) | Copia | Testo della notifica |
| La casa chiede | Le risposte di Home Assistant, fino a tre, larghe quanto il fumetto | Notifica di Home Assistant |
| Chiamata persa | Richiama | Notifica |
| Corsa o consegna (barra di avanzamento) | — (tocco: apre) | Notifica in corso dell'app |
| Timer in corso | — (tocco: apre) | Notifica dell'orologio |
| Musica in riproduzione | ⏯ e ⏭ | Riproduzione di Android |
| Riunione o impegno con indirizzo, da 15 minuti prima | Partecipa · Naviga | Calendario, messaggi |

Gli avvisi (pagamenti, pacchi, biglietti, promemoria) restano nelle notifiche; gli impegni nell'agenda finché non mancano 15 minuti.

### "Di solito adesso"

Se non c'è niente in corso, il fumetto propone una cosa che di solito fai a quest'ora, scritta per intero ("Spegni luce bagno"), col suo perché sopra.

- Sono le proposte della ricerca (senza le fissate): cambiano ogni mezz'ora, mai mentre le guardi.
- Tocco o tondo: la fa. Scorri via: la prossima; dopo la terza il fumetto tace fino alla prossima mezz'ora. Tieni premuto: non suggerire più.
- **Sordina non fa niente da sola.** Propone, tu tocchi. Le automazioni si creano nelle routine del telefono o in Home Assistant.

In auto il fumetto è lo stesso, sotto "Tocca e parla"; il "di solito" non c'è, perché persone e app sono già lì.

## 3. Su: fai

- Barra in alto, senza tastiera; microfono a destra (riconoscimento solo sul telefono).
- Sotto, sei app: quelle che usi a quell'ora. Pressione lunga: tieni qui, togli.
- Scrivendo: app, persone ("chiama Anna", "scrivi a Paolo che arrivo"), timer, sveglie, impegni ("calcetto domani alle 17"), calcoli, impostazioni.
- Comandi di casa ("spegni luci sala"): passati ad **Assist** di Home Assistant, che conosce stanze e alias. Solo le entità che esponi ad Assist.
- Verso altre persone serve sempre il tuo tocco.
- I comandi di casa che aprono o disarmano (porte, serrature, cancelli, garage, allarme) chiedono impronta o PIN, da qualunque parte arrivino (voce, tastiera, proposta). Si riconoscono dalle parole e dal nome vero in Home Assistant: se la serratura si chiama "Ingresso", "apri ingresso" chiede l'impronta, "accendi luce ingresso" no. Chiudere, bloccare, inserire l'allarme: subito. Senza blocco schermo sul telefono basta un "Procedi". I pulsanti delle notifiche di Home Assistant restano come li hai configurati.
- Allarmi: in Impostazioni › Home Assistant scegli quali allarmi (qualunque `alarm_control_panel`) disattivare con l'impronta, ciascuno col suo codice. "Disattiva allarme" (o il suo nome) chiede l'impronta e manda il codice; con più allarmi salvati, se non lo nomini, chiede quale. Il codice è chiuso da una chiave che si apre solo con l'impronta (non col PIN del telefono) e si annulla se registri un'impronta nuova. Se l'impronta non va, il codice si scrive a mano e non si salva.
- Routine: in Impostazioni › Home Assistant › Routine vedi scene e script della casa e ognuna ha la sua pagina con gli interruttori: Usa questa routine, Con impronta, e per ogni allarme salvato Inserisci (dopo la routine: "Esco" + centrale) o Disattiva (prima: "Entra" + centrale; se l'allarme non si disattiva, la routine non parte). Le due cose sull'allarme si escludono e con un allarme l'impronta è sempre accesa. Anche ogni allarme ha la sua pagina (codice, anche inserire, modalità, togli il codice): niente fogli dal basso in Home Assistant. Si chiamano per nome ("buonanotte", "attiva scena esci") o con le tue frasi ("sto entrando", una per riga in Impostazioni) e possono comparire fra le proposte; il tocco chiede comunque l'impronta, se scelta.
- Inserire: per ogni allarme salvato si può accendere "Anche inserire" (spento all'inizio: di solito inserisce già Home Assistant con le sue automazioni) e scegliere la modalità predefinita fra quelle che l'allarme sa fare (Fuori casa, In casa, Notte, Vacanza, Personalizzata). "Inserisci allarme" usa la predefinita, "inserisci allarme notte" quella detta. Se l'allarme vuole il codice anche per inserire, impronta; se no subito. Già inserito: lo dice e basta.
- Allarme già disattivato: "disattiva allarme", o una routine legata a quell'allarme, dice "è già disattivato" e si ferma lì (niente impronta, niente routine).

## 4. Notifiche

Tutto aspetta in sordina, tranne quello che scegli in Impostazioni → Notifiche. La pagina va dal generico allo specifico:

- **Oggi**: una riga in cima ("12 passate · 48 in sordina") che apre le notifiche di oggi.
- **Importanti:** codici e sicurezza, chiamate perse, promemoria e impegni, messaggi urgenti, messaggi di risposta, notifiche di sistema. Ognuno si spegne con il suo interruttore; codici e sicurezza no, si spengono solo per un'app che sbaglia (le offerte con "codice SCONTO20"), nella loro pagina.
- **App:** le app che entrano. Nessuna nota se entra tutto; "1 eccezione", "N eccezioni" se dentro hai spento dei tipi o delle chat. Toccandola si vede ogni tipo con il suo stato. I tipi si imparano man mano che arrivano; per le app di messaggi i tipi sono le chat.
- **Persone:** ovunque o "su WhatsApp"; nei gruppi solo se scelto. Vince la regola più specifica.
- **Dalla tendina:** la direzione opposta, per quello che passerebbe comunque (meteo, calendario, sistema). "Aggiungi una notifica" mostra quello che c'è adesso nella tendina: scelta una riga, va in sordina solo quel tipo, mai l'app. Ogni riga si toglie.
- In fondo, l'ordine della pagina Sordina.

Gli elenchi per aggiungere app e persone sono uguali: alfabetici, con il cerchio di spunta, e l'ordine non cambia mentre la pagina è aperta.

### Oggi
Le notifiche arrivate da mezzanotte, in ordine di tempo, divise in mattina, pomeriggio e sera. Due pillole: **Passate** e **In sordina**. Ogni riga: chi e l'app, l'ora, e sotto il motivo con le parole delle sezioni ("Codici e sicurezza", "persona, ovunque", "nessuna regola la fa passare").

Si guarda e basta: le righe non si toccano. Per cambiare una regola si torna in Notifiche.

Le app nascoste non compaiono. A mezzanotte si ricomincia; il registro resta sul telefono, fuori dal backup, e si cancella con "Cancella tutto".

### La pagina Sordina
- Raggruppata per persona o, se non c'è una persona, per app. Ogni gruppo parte chiuso.
- Tieni premuto un gruppo: un foglio con le righe a spunta ("Fai entrare Elisa", "Fai entrare tutto da WhatsApp", "Fai aspettare…") e tre pillole per lasciar passare per un po'. Nessuna spiegazione nel foglio.
- Le regole si dicono con le parole di Sordina ("lo faccio passare", "lo tengo io"), mai con nomi di livelli.
- Sordina le posticipa con la funzione standard di Android, non le copia: aperte dalla pagina si aprono **originali**, nel punto giusto. Se Sordina si ferma, Android le rimette nella tendina entro un giorno.
- Una notifica letta nell'app di origine esce anche dalla sordina.
- Le app nascoste sono una riga sola ("App nascoste · N"): chi e cosa si vedono solo dopo lo sblocco.
- Urgenze e insistenza contano solo dai tuoi contatti: un SMS "URGENTE" da un numero sconosciuto resta in sordina.

### Silenzio, notte e auto
- "Silenzio per 45 minuti", "silenzio fino alle 15" dalla barra: Non disturbare a tempo, poi tutto riprende.
- Con Non disturbare o una modalità di Android accesa aspettano anche le persone che entrano da un'app sola, le chiamate perse e i promemoria; il resto delle eccezioni passa.
- In auto, Non disturbare fa passare solo i messaggi delle persone importanti e, mentre scrivi a qualcuno, di tutti: Android Auto annuncia una notifica appena arriva, prima che Sordina la metta in sordina. Le chiamate, se scegli, solo dai contatti; chi richiama entro pochi minuti passa comunque.

## 5. Aspetto

- Un tema sobrio, chiaro, scuro o automatico, con un colore principale (anche quello del telefono).
- Sfondo: tinta unita, disegni, o lo sfondo del telefono (anche sulla schermata di blocco). Il testo diventa chiaro o scuro in base all'immagine sotto.
- L'ora grande e leggera; il fumetto è l'unico elemento in rilievo.
- Frasi corte. Nessuna etichetta maiuscola, nessun riquadro dentro un riquadro.

## 6. Primo avvio

Una cosa per schermata. In alto l'onda che si calma a ogni passo e dove sei ("Permessi · 2 di 4"). Frasi corte, Sordina parla in prima persona.

1. **Ciao**
2. **Come funziona**: le notifiche aspettano, le ritrovi, le app si aprono con calma, chiedimi le cose.
3. **Permessi**, uno per schermata, ciascuno con "Cosa succede adesso" in passi numerati e una spunta grande al ritorno: notifiche, Home, batteria, Samsung (solo sui Samsung), notifiche di Sordina.
4. **Le tue scelte**: Importanti, app che entrano subito, persone, app che fanno perdere tempo, cosa mostra la Home (meteo, impegni), Home Assistant sì o no.
5. **Tutto pronto**, con un riassunto semplice.

Gli altri permessi si chiedono la prima volta che servono.

## 7. Impostazioni

In cima, solo se manca qualcosa: i permessi da sistemare. Poi: I tuoi progressi · Notifiche (Oggi · Importanti · App · Persone · Dalla tendina · ordine della pagina Sordina) · Freno (app frenate, budget, secondi di attesa) · Home (cosa mostra, momenti, auto, gesti) · Temi · Ricerca · Home Assistant (allarmi, routine) · Generali (permessi, lingua, dati, errori, crediti, perché Sordina).

## 8. Tolto lungo la strada

Schermata In attesa a pagina intera, pagina Oggi in Casa (il registro di oggi vive ora in Impostazioni → Notifiche), specchio serale separato (ora è il riepilogo della Sera), linea della giornata, menu dei momenti, riga Casa, temi multipli, grana della carta, primo avvio in dieci passi, regole automatiche dentro Sordina, livelli di notifica (Sempre, Silenziosa, Riepilogo), riepiloghi a orario.

## 9. Più avanti

Schermata di blocco "live" (sfondo animato), altri temi, F-Droid.

## 10. Da verificare

L'elenco aggiornato delle prove sul telefono è in [TODO.md](../TODO.md).

- Biglietti dalle mail: quanto testo mostra davvero Gmail nella notifica.
