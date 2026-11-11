# Sordina

Un launcher Android che mette il telefono in sordina: lo prendi perché ti serve, fai la cosa, e lui torna in silenzio. Niente di importante si perde.

Open source (GPL-3.0), gratuito, senza account, senza pubblicità, senza server: tutto resta sul telefono.

## Stato

Versione 0.1, in prova personale. Cosa c'è:

- **Casa**: data, ora, frase di stato, linea della giornata con i riepiloghi.
- **Intenzione** (scorri in su): barra di ricerca tollerante agli errori e le **sei proposte** imparate da cosa apri a ogni ora, negli ultimi 28 giorni. Pressione lunga: tieni qui, non propormela più, informazioni.
- **Tutte le app** (scorri ancora in su): elenco alfabetico con indice, filtro, app nascoste, profilo di lavoro.
- **Temi** Carta e Quaderno, chiaro/scuro/automatico. Modalità leggera automatica sui telefoni con poca RAM.

Prossimi passi: notifiche a tre livelli e riepiloghi per persona, momenti Notte e In auto, Home Assistant minimo. Vedi [docs/UX.md](docs/UX.md) e [docs/CONCETTO.md](docs/CONCETTO.md).

## Compilare

Serve l'SDK Android 36 e un JDK 17–21 (quello di Android Studio va bene):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

La versione di debug si installa come `app.sordina.debug`, accanto a quella normale.

## Permessi

| Permesso | Perché |
|---|---|
| Accesso all'utilizzo | Le sei proposte. I dati non lasciano il telefono. |
| Espandere la barra di stato | Scorrere in giù da Casa apre le notifiche. |

## Licenze

Codice: GPL-3.0 ([LICENSE](LICENSE)). Carattere Newsreader: SIL Open Font License ([fonts-license/Newsreader-OFL.txt](fonts-license/Newsreader-OFL.txt)).
