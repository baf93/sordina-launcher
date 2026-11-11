# Sordina: come funziona

Versione 2, 12 novembre 2026. Sostituisce la bozza 1 (nella cronologia git).

**In una riga:** prendi il telefono, fai la cosa, lui torna in silenzio. Sordina impara cosa fai e te lo propone; a fare sei sempre tu.

Principio: **KISS & Smart**. Poche cose sullo schermo, frasi di 3–4 parole, nessuna spiegazione nell'interfaccia. L'intelligenza si vede solo quando serve.

## 1. Tre gesti

| Dove | Cosa c'è |
|---|---|
| **Casa** | Ora, data, una frase ("tutto calmo", "3 in attesa") e la **pila di schede** |
| **Su** | Barra (scrivi o parli) e sei app proposte. Ancora su: tutte le app |
| **Giù** | La tendina di Android, intatta |

Tasto Home o Indietro: sempre Casa. Pressione lunga sul vuoto: impostazioni.

## 2. Casa e la pila di schede

Casa mostra solo l'ora, finché non succede qualcosa. Allora compare una scheda con la cosa utile adesso e al massimo due pulsanti.

- **Più schede:** la più urgente davanti, le altre dietro; si scorrono in orizzontale, i puntini dicono quante sono.
- **Una sola:** niente pila, niente puntini. **Nessuna:** solo l'ora.
- Una scheda sparisce da sola quando non serve più.

### Schede del momento

| Scheda | Pulsanti | Da dove lo sa |
|---|---|---|
| Codice OTP | Copia | Testo della notifica |
| Chiamata persa (persona importante) | Richiama | Notifica |
| Evento tra poco | Naviga · Partecipa | Calendario |
| Musica in riproduzione | ⏮ ⏯ ⏭ | Riproduzione di Android |
| Timer o sveglia in corso | Ferma | Notifica dell'orologio |
| Biglietto aereo o treno | Aggiungi al calendario | Anteprima della mail nella notifica |
| Pacco in consegna | Apri | Notifica del corriere |

Priorità: OTP > chiamata > evento imminente > timer > musica > il resto.

### Schede "di solito"

Sordina riconosce un momento e ricorda cosa fai subito dopo. Dopo qualche volta lo propone:

> A casa · di solito: luci sala — **[Fallo]**

- Momenti: arrivo a casa (Wi-Fi), in auto (Bluetooth), sveglia spenta, cuffie collegate, in carica la sera.
- Cosa impara: app aperte (accesso all'utilizzo), comandi dati dalla barra, azioni di Home Assistant (dal suo registro).
- **Sordina non fa niente da sola.** Propone, tu tocchi. Le automazioni si creano nelle routine del telefono o in Home Assistant.

## 3. Su: fai

- Barra in alto, senza tastiera; microfono a destra (riconoscimento solo sul telefono).
- Sotto, sei app: quelle che usi a quell'ora. Pressione lunga: tieni qui, togli.
- Scrivendo: app, persone ("chiama Anna", "scrivi a Paolo che arrivo"), timer, sveglie, impegni ("calcetto domani alle 17"), calcoli, impostazioni.
- Comandi di casa ("spegni luci sala"): passati ad **Assist** di Home Assistant, che conosce stanze e alias. Solo le entità che esponi ad Assist.
- Verso altre persone serve sempre il tuo tocco.

## 4. Notifiche

- **Subito:** persone importanti, codici, chiamate, sveglie, app scelte da te.
- **Aspetta:** tutto il resto, fino al riepilogo (8, 13, 19).
- Al riepilogo ricompaiono nella tendina **originali** (icona, pulsanti, apertura nel punto giusto): Sordina le posticipa con la funzione standard di Android, non le copia.
- Nulla si perde: se Sordina si ferma, Android le rimette comunque all'ora prevista.
- In Casa la frase dice quante aspettano; toccandola, la lista.

Notte (orario) e auto (Android Auto): passano solo le persone importanti, da sole, senza menu.

## 5. Aspetto

- Un tema sobrio, chiaro o scuro automatico. Sfondo pieno, un carattere, un accento.
- L'ora grande e leggera; la scheda è l'unico elemento in rilievo.
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
