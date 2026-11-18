# Sordina: come funziona

Versione 2, 12 novembre 2026. Sostituisce la bozza 1 (nella cronologia git).

**In una riga:** prendi il telefono, fai la cosa, lui torna in silenzio. Sordina impara cosa fai e te lo propone; a fare sei sempre tu.

Principio: **KISS & Smart**. Poche cose sullo schermo, frasi di 3–4 parole, nessuna spiegazione nell'interfaccia. L'intelligenza si vede solo quando serve.

## 1. Tre gesti

| Dove | Cosa c'è |
|---|---|
| **Casa** | Ora, data, una frase ("tutto calmo", "3 in attesa") e la **sordina col suo fumetto** |
| **Su** | Barra (scrivi o parli) e sei app proposte. Ancora su: tutte le app |
| **Giù** | La tendina di Android, intatta |

Tasto Home o Indietro: sempre Casa. Pressione lunga sul vuoto: impostazioni.

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

Tutto aspetta in sordina, tranne:

- **Importanti:** codici e sicurezza, chiamate perse, promemoria, urgenze, conversazione in corso, sistema. Codici e sicurezza non si spengono; si spengono solo per un'app che sbaglia (le offerte con "codice SCONTO20").
- **App:** le app che entrano. Toccandone una, si spengono i tipi di notifica o le chat che non servono.
- **Persone:** le persone importanti, ovunque o solo da un'app.
- **In sordina:** tutto il resto, nella pagina Sordina finché non la apri. Niente riepiloghi a orario.

Dalla pagina Sordina, tieni premuta una notifica: "entra sempre", "spegni questo tipo", "spegni questa chat". Le regole si spiegano con le parole di Sordina ("lo faccio passare", "lo tengo io"), mai con nomi di livelli.
- Sordina le sospende con la funzione standard di Android, non le copia: aperte dalla pagina Sordina si aprono **originali**, nel punto giusto.
- Nulla si perde: se Sordina si ferma, Android le rimette nella tendina entro un giorno.
- Le app nascoste sono una riga sola ("App nascoste · N"): chi e cosa si vedono solo dopo lo sblocco.
- Urgenze e insistenza contano solo dai tuoi contatti: un SMS "URGENTE" da un numero sconosciuto resta in sordina.
- In Casa la frase dice quante aspettano; toccandola, la lista.

Notte (orario) e auto (Android Auto): aspettano anche chiamate perse, promemoria e le persone che entrano da un'app sola; il resto delle eccezioni passa, senza menu.
In auto, Non disturbare fa passare solo i messaggi delle persone importanti (i preferiti) e, mentre scrivi a qualcuno, di tutti: Android Auto annuncia una notifica appena arriva, prima che Sordina la metta in sordina, quindi è Non disturbare a doverla fermare. Il resto aspetta nella tendina e in Sordina.

## 5. Aspetto

- Un tema sobrio, chiaro o scuro automatico. Sfondo pieno, un carattere, un accento.
- L'ora grande e leggera; il fumetto è l'unico elemento in rilievo.
- Frasi corte. Nessuna etichetta maiuscola, nessun riquadro dentro un riquadro.

## 6. Primo avvio

Quattro passi, ognuno un titolo e un pulsante: Home · Notifiche · Persone importanti · Fatto. Gli altri permessi si chiedono la prima volta che servono.

## 7. Impostazioni

Notifiche (Importanti · App · Persone · ordine della pagina Sordina) · Casa · Freno · Ricerca · Temi · Progressi · Generali (permessi, lingua, dati: esporta, importa, copia giornaliera) · Home Assistant.

## 8. Tolto rispetto alla bozza 1

Schermata In attesa a pagina intera, pagina Oggi, specchio serale, linea della giornata, menu dei momenti, riga Casa, tema Quaderno, grana della carta, primo avvio in dieci passi, regole automatiche dentro Sordina.

## 9. Più avanti

Schermata di blocco "live" (sfondo animato), altri temi, inglese.

## 10. Da verificare

- Il rilascio al riepilogo: le notifiche tornano una per una con suono? Devono tornare in silenzio, o con un solo suono.
- Riconoscere il Wi-Fi di casa richiede il permesso di posizione (solo per il nome della rete): accettabile, o meglio la zona di Home Assistant?
- Biglietti dalle mail: quanto testo mostra davvero Gmail nella notifica.
