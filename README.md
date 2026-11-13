# Sordina

Un launcher Android che mette il telefono in sordina: lo prendi perché ti serve, fai la cosa, e lui torna in silenzio. Niente di importante si perde.

Open source (GPL-3.0), gratuito, senza account, senza pubblicità, senza server: tutto resta sul telefono.

## Stato

Versione 0.1, in prova personale. Cosa c'è:

- **Casa**: data, ora, frase di stato ("3 in sordina", "Modalità accesa · solo l'importante"), linea della giornata, righe *Casa* (Home Assistant) e *In attesa*, notifiche "Sempre" in evidenza, specchio serale. Pressione lunga sulla frase: cambia momento.
- **Intenzione** (scorri in su): barra con motore delle intenzioni e microfono (riconoscimento solo sul telefono, Android 12+), sei proposte imparate da cosa apri a ogni ora. Pressione lunga su una proposta: tieni qui, perché è qui, non propormela più.
- **Motore delle intenzioni**: app, persone ("chiama Anna", "scrivi a Anna che arrivo" su WhatsApp/Telegram/Signal/SMS), timer e sveglie senza aprire l'orologio, impegni scritti nel calendario con Annulla ("calcetto domani alle 17"), torcia, musica, navigazione, comandi Home Assistant ("spegni luci sala"), impostazioni di sistema, calcoli e unità. Impara dalle correzioni.
- **Tutte le app** (scorri ancora in su): elenco alfabetico con indice, filtro, app nascoste, profilo di lavoro, "Frena".
- **Notifiche a tre livelli**: Passa sempre, Passa in silenzio, In sordina. Quelle in sordina non tornano da sole: restano nella pagina Sordina finché le leggi (posticipate di 24 ore alla volta, rinnovate finché Sordina è viva). Codici OTP, persone importanti, urgenze e promemoria passano subito.
- **In attesa**: raggruppata per persona, tocco per aprire nel punto giusto, "Letto" e "Tutto letto". Pagina **Oggi** con cosa è passato e perché.
- **Scheda sulla schermata di blocco**: una notifica silenziosa con stato e riepilogo per persona, tre livelli di privacy.
- **Modalità di Android**: il silenzio lo decidono Non disturbare, Riposo, Guida o una tua modalità. Con una modalità accesa passa solo l'importante; Sordina non ha orari suoi.
- **Sordina a tempo**: "sordina per 45 minuti", "silenzio fino alle 15" accendono Non disturbare e lo spengono alla fine (tocco sulla riga per finire prima).
- **Urgenze**: "urgente", "chiamami" o tre messaggi in cinque minuti in una chat a due passano anche se non sono di una persona importante.
- **Regole**: per tipo di notifica (il canale, es. la notifica fissa di Home Assistant), per persona o gruppo, per app; la più stretta vince. Si danno toccando una notifica in sordina o, in Impostazioni › Regole, una di quelle nella tendina. Nella pagina Sordina ogni blocco dice perché aspetta.
- **Schede**: pressione lunga per toglierle per oggi; quelle da vedere una volta (pacco, biglietto, chiamata persa, pagamento) spariscono quando le apri o leggi la notifica; riunioni di Outlook/Teams con "Tra 10 min".
- **Salute del motore**: se Sordina non vede le notifiche, è ferma o Android può addormentarla, Casa lo dice in cima.
- **Attrito** per le app da frenare, **Home Assistant** minimo (REST, token cifrato nel Keystore), **primo avvio** in dieci passi, **impostazioni** complete, esporta/importa.
- **Temi** Carta e Quaderno, chiaro/scuro/automatico. Modalità leggera automatica sui telefoni con poca RAM.

Più avanti: temi Strumento e Morbido, sfondo foto, sfondo vivo sulla schermata di blocco, inglese. Vedi [docs/UX.md](docs/UX.md) e [docs/CONCETTO.md](docs/CONCETTO.md).

## Compilare

Serve l'SDK Android 36 e un JDK 17–21 (quello di Android Studio va bene):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

La versione di debug si installa come `app.sordina.debug`, accanto a quella normale.

## Permessi

| Permesso | Perché |
|---|---|
| Accesso alle notifiche | Decidere cosa passa e cosa resta in sordina. |
| Accesso all'utilizzo | Le sei proposte e lo specchio serale. |
| Non disturbare | Solo per "sordina per 45 minuti": accende e spegne Non disturbare. |
| Notifiche | La scheda sulla schermata di blocco. |
| Contatti | Persone importanti, "chiama" e "scrivi a". Le importanti diventano preferite di Android. |
| Calendario | "Calcetto domani alle 17" scritto direttamente. |
| Microfono | Voce, solo con il riconoscitore sul telefono. |
| Chiamate | "Chiama Anna" chiama dopo il tuo tocco; senza, apre il tastierino. |
| Internet | Solo verso l'indirizzo di Home Assistant che scegli. |
| Avvio del telefono | Ripartire dopo un riavvio (fine della sordina a tempo, rinnovo delle notifiche in sordina). |

## Licenze

Codice: GPL-3.0 ([LICENSE](LICENSE)).
