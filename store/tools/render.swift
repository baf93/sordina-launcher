// Disegna una pagina HTML in un PNG della misura esatta, con WebKit (niente browser da installare).
//   swift store/tools/render.swift <pagina.html> <uscita.png> <larghezza> <altezza> [#frammento]
// Il frammento (es. "#it-1") arriva alla pagina in location.hash: una pagina sola per tutte le immagini.
import AppKit
import WebKit

let args = CommandLine.arguments
guard args.count >= 5, let w = Double(args[3]), let h = Double(args[4]) else {
    FileHandle.standardError.write("uso: render.swift pagina.html uscita.png larghezza altezza [#frammento]\n".data(using: .utf8)!)
    exit(2)
}
let page = URL(fileURLWithPath: args[1]).standardizedFileURL
let out = URL(fileURLWithPath: args[2])
let hash = args.count > 5 ? args[5] : ""

final class Shot: NSObject, WKNavigationDelegate {
    let view: WKWebView
    let w: Double, h: Double
    init(w: Double, h: Double) {
        self.w = w; self.h = h
        let cfg = WKWebViewConfiguration()
        view = WKWebView(frame: NSRect(x: 0, y: 0, width: w, height: h), configuration: cfg)
        super.init()
        view.navigationDelegate = self
    }
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        // Aspetta i font e un paio di frame, poi fotografa a 1:1 (pixel = punti CSS)
        let w = self.w, h = self.h
        webView.callAsyncJavaScript("await document.fonts.ready; await new Promise(r => setTimeout(r, 600)); return 1", arguments: [:], in: nil, in: .page) { _ in
            let c = WKSnapshotConfiguration()
            c.rect = NSRect(x: 0, y: 0, width: w, height: h)
            c.snapshotWidth = NSNumber(value: w)
            webView.takeSnapshot(with: c) { img, err in
                guard let img, let tiff = img.tiffRepresentation, let rep = NSBitmapImageRep(data: tiff) else {
                    FileHandle.standardError.write("snapshot fallito: \(String(describing: err))\n".data(using: .utf8)!); exit(1)
                }
                // Ridisegna esattamente w×h, senza trasparenza (Play non vuole il canale alfa)
                guard let src = rep.cgImage,
                      let ctx = CGContext(data: nil, width: Int(w), height: Int(h), bitsPerComponent: 8, bytesPerRow: 0,
                                          space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)
                else { exit(1) }
                ctx.interpolationQuality = .high
                ctx.draw(src, in: CGRect(x: 0, y: 0, width: w, height: h))
                let dest = CGImageDestinationCreateWithURL(out as CFURL, "public.png" as CFString, 1, nil)!
                CGImageDestinationAddImage(dest, ctx.makeImage()!, nil)
                CGImageDestinationFinalize(dest)
                exit(0)
            }
        }
    }
}

let app = NSApplication.shared
app.setActivationPolicy(.prohibited)
let shot = Shot(w: w, h: h)
// Senza una finestra WebKit non disegna: una finestra senza bordi, fuori dallo schermo
let window = NSWindow(contentRect: NSRect(x: -20000, y: -20000, width: w, height: h), styleMask: [.borderless], backing: .buffered, defer: false)
window.contentView = shot.view
window.orderBack(nil)
var comps = URLComponents(url: page, resolvingAgainstBaseURL: false)!
if hash.hasPrefix("#") { comps.fragment = String(hash.dropFirst()) }
shot.view.loadFileURL(comps.url!, allowingReadAccessTo: page.deletingLastPathComponent().deletingLastPathComponent())
DispatchQueue.main.asyncAfter(deadline: .now() + 30) { FileHandle.standardError.write("timeout\n".data(using: .utf8)!); exit(1) }
app.run()
