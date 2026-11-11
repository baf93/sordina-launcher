# Sordina

Un launcher Android che mette il telefono in sordina: lo prendi perché ti serve, fai la cosa, e lui torna in silenzio. Niente di importante si perde.

Open source (GPL-3.0), gratuito, senza account, senza pubblicità, senza server: tutto resta sul telefono.

## Stato

Versione 0.1, in prova personale. Cosa c'è:

- **Casa**: data, ora, frase di stato ("Niente di urgente fino alle 19"), linea della giornata con i riepiloghi, righe *Casa* (Home Assistant) e *In attesa*, notifiche "Sempre" in evidenza, specchio serale. Pressione lunga sulla frase: cambia momento.
- **Intenzione** (scorri in su): barra con motore delle intenzioni e microfono (riconoscimento solo sul telefono, Android 12+), sei proposte imparate da cosa apri a ogni ora. Pressione lunga su una proposta: tieni qui, perché è qui, non propormela più.
- **Motore delle intenzioni**: app, persone ("chiama Anna", "scrivi a Anna che arrivo" su WhatsApp/Telegram/Signal/SMS), timer e sveglie senza aprire l'orologio, impegni scritti nel calendario con Annulla ("calcetto domani alle 17"), torcia, musica, navigazione, comandi Home Assistant ("spegni luci sala"), impostazioni di sistema, calcoli e unità. Impara dalle correzioni.
- **Tutte le app** (scorri ancora in su): elenco alfabetico con indice, filtro, app nascoste, profilo di lavoro, "Frena".
- **Notifiche a tre livelli**: Sempre, Subito in silenzio, Riepilogo. Quelle che aspettano vengono posticipate (non cancellate) fino al riepilogo; codici OTP, persone importanti e promemoria passano subito.
- **In attesa**: raggruppata per persona, tocco per aprire nel punto giusto, "Letto" e "Tutto letto". Pagina **Oggi** con cosa è passato e perché.
- **Scheda sulla schermata di blocco**: una notifica silenziosa con stato e riepilogo per persona, tre livelli di privacy.
- **Non disturbare gestito** (solo se scelto): di giorno suonano tutte le chiamate, di notte e in auto solo le persone importanti.
- **Momenti** Notte (a orario) e In auto (Android Auto / modalità auto).
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
| Accesso alle notifiche | Decidere cosa passa e cosa aspetta il riepilogo. |
| Accesso all'utilizzo | Le sei proposte e lo specchio serale. |
| Non disturbare | Solo se scegli che Sordina gestisca il silenzio. |
| Notifiche | La scheda sulla schermata di blocco. |
| Contatti | Persone importanti, "chiama" e "scrivi a". Le importanti diventano preferite di Android. |
| Calendario | "Calcetto domani alle 17" scritto direttamente. |
| Microfono | Voce, solo con il riconoscitore sul telefono. |
| Chiamate | "Chiama Anna" chiama dopo il tuo tocco; senza, apre il tastierino. |
| Internet | Solo verso l'indirizzo di Home Assistant che scegli. |
| Avvio del telefono | Riprogrammare riepiloghi e notte dopo un riavvio. |

## Licenze

Codice: GPL-3.0 ([LICENSE](LICENSE)).
