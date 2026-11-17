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
- Verso altre persone serve sempre il tuo tocco. Anche per i comandi di casa che aprono o disarmano (porte, serrature, cancelli, garage, allarme): mai da soli, né a voce né dalle schede.

## 4. Notifiche

- **Passa:** persone importanti, codici, avvisi di pagamento sospetti, chiamate, sveglie, app scelte da te. Nella tendina, come le manda l'app.
- **In sordina:** tutto il resto, nella pagina Sordina finché non la apri. Niente riepiloghi a orario.
- Sordina le sospende con la funzione standard di Android, non le copia: aperte dalla pagina Sordina si aprono **originali**, nel punto giusto.
- Nulla si perde: se Sordina si ferma, Android le rimette nella tendina entro un giorno.
- Le app nascoste sono una riga sola ("App nascoste · N"): chi e cosa si vedono solo dopo lo sblocco.
- Urgenze e insistenza contano solo dai tuoi contatti: un SMS "URGENTE" da un numero sconosciuto resta in sordina.
- In Casa la frase dice quante aspettano; toccandola, la lista.

Notte (orario) e auto (Android Auto): passano solo le persone importanti, da sole, senza menu.

## 5. Aspetto

- Un tema sobrio, chiaro o scuro automatico. Sfondo pieno, un carattere, un accento.
- L'ora grande e leggera; il fumetto è l'unico elemento in rilievo.
- Frasi corte. Nessuna etichetta maiuscola, nessun riquadro dentro un riquadro.

## 6. Primo avvio

Quattro passi, ognuno un titolo e un pulsante: Home · Notifiche · Persone importanti · Fatto. Gli altri permessi si chiedono la prima volta che servono.

## 7. Impostazioni

Persone importanti · Cosa passa subito (per app) · Orari dei riepiloghi · Notte · Home Assistant · Dati (esporta, importa, cancella) · Informazioni.

## 8. Tolto rispetto alla bozza 1

Schermata In attesa a pagina intera, pagina Oggi, specchio serale, linea della giornata, menu dei momenti, riga Casa, tema Quaderno, grana della carta, primo avvio in dieci passi, regole automatiche dentro Sordina.

## 9. Più avanti

Schermata di blocco "live" (sfondo animato), altri temi, inglese.

## 10. Da verificare

- Il rilascio al riepilogo: le notifiche tornano una per una con suono? Devono tornare in silenzio, o con un solo suono.
- Riconoscere il Wi-Fi di casa richiede il permesso di posizione (solo per il nome della rete): accettabile, o meglio la zona di Home Assistant?
- Biglietti dalle mail: quanto testo mostra davvero Gmail nella notifica.
