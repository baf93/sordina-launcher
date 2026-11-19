# Materiale per Google Play

Tutto si rigenera da `src/store.html` (una pagina, il frammento sceglie l'immagine) con WebKit:

    swift tools/render.swift src/store.html graphics/icon-512.png 512 512 "#icon"
    swift tools/render.swift src/store.html graphics/it/feature-1024x500.png 1024 500 "#feature-it"
    swift tools/render.swift src/store.html graphics/it/1.png 1080 1920 "#shot-it-1"   # … fino a 7, e "en"

Dati solo inventati: nessun contatto, notifica o luogo vero.

| Play Console                         | File                                  |
|--------------------------------------|---------------------------------------|
| Nome, descrizioni, novità            | `listing/<lingua>.md`: it, en, es, fr, de, pt |
| Icona app (512×512)                  | `graphics/icon-512.png`               |
| Grafica in primo piano (1024×500)    | `graphics/<lingua>/feature-1024x500.png` |
| Screenshot telefono (1080×1920)      | `graphics/<lingua>/1.png` … `7.png`   |

Lingua predefinita della scheda: **inglese (en-US)**. Le altre lingue senza un file proprio mostrano quella. Lo spagnolo va anche su es-419 ed es-US, il francese su fr-CA, il portoghese su pt-BR e pt-PT. Grafiche: it per l'italiano, en per tutte le altre finché non ce ne sono di proprie.

Ordine delle schermate: 1 Casa · 2 Pagina Sordina · 3 Codice OTP · 4 Fumetto con immagine · 5 Intenzione · 6 Auto · 7 Privacy.
