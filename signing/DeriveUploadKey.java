import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/**
 * Ricava la chiave di caricamento Play di Sordina da una password che ricordi.
 * Nessun segreto sta su disco o nel repo: stessa password, stessa chiave, stesso certificato.
 *
 *   password ──PBKDF2-HMAC-SHA256 (2^21 giri, sale fisso)──▶ seme 32 byte
 *   seme ──HMAC-SHA256 a contatore──▶ due primi da 1536 bit ──▶ RSA 3072, e = 65537
 *   certificato X.509 autofirmato con date e numero di serie fissi (la firma PKCS#1 v1.5 è deterministica)
 *
 * Uso: java DeriveUploadKey.java <uscita.p12> <password-keystore-usa-e-getta>   (la password vera da stdin)
 * Stampa lo SHA-256 del certificato: è l'impronta che Play registra come chiave di caricamento.
 * Non cambiare nulla qui dentro (costanti, ordine, formato): cambierebbe la chiave.
 */
public class DeriveUploadKey {
    static final String SALT = "sordina/play-upload-key/v1";
    static final int ROUNDS = 1 << 21;
    static final int BITS = 3072;
    static final BigInteger E = BigInteger.valueOf(65537);

    public static void main(String[] a) throws Exception {
        if (a.length != 2) { System.err.println("uso: DeriveUploadKey <out.p12> <storepass>"); System.exit(2); }
        char[] pw = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8)).readLine().toCharArray();
        if (pw.length < 12) { System.err.println("password troppo corta (minimo 12 caratteri)"); System.exit(3); }

        byte[] seed = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(new PBEKeySpec(pw, SALT.getBytes(StandardCharsets.UTF_8), ROUNDS, 256)).getEncoded();
        Arrays.fill(pw, '\0');

        Drbg rng = new Drbg(seed);
        BigInteger p = prime(rng, BITS / 2), q = prime(rng, BITS / 2);
        if (p.compareTo(q) < 0) { BigInteger t = p; p = q; q = t; }
        BigInteger n = p.multiply(q);
        BigInteger phi = p.subtract(BigInteger.ONE).multiply(q.subtract(BigInteger.ONE));
        BigInteger d = E.modInverse(phi);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        PrivateKey priv = kf.generatePrivate(new RSAPrivateCrtKeySpec(n, E, d, p, q,
            d.mod(p.subtract(BigInteger.ONE)), d.mod(q.subtract(BigInteger.ONE)), q.modInverse(p)));
        PublicKey pub = kf.generatePublic(new RSAPublicKeySpec(n, E));

        X509Certificate cert = certificate(pub, priv);
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        char[] store = a[1].toCharArray();
        ks.setKeyEntry("upload", priv, store, new java.security.cert.Certificate[]{cert});
        try (OutputStream o = new FileOutputStream(a[0])) { ks.store(o, store); }

        byte[] fp = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < fp.length; i++) s.append(i == 0 ? "" : ":").append(String.format("%02X", fp[i]));
        System.out.println(s);
    }

    /** Primo da [bits] bit con i due bit alti accesi (così n ha esattamente 2*bits bit) e p-1 coprimo con e. */
    static BigInteger prime(Drbg rng, int bits) {
        byte[] b = rng.next(bits / 8);
        b[0] |= (byte) 0xC0;
        b[b.length - 1] |= 1;
        BigInteger c = new BigInteger(1, b);
        while (!(c.isProbablePrime(128) && c.subtract(BigInteger.ONE).gcd(E).equals(BigInteger.ONE))) c = c.add(BigInteger.TWO);
        return c;
    }

    /** HMAC-SHA256(seme, contatore) concatenati: deterministico e indipendente dalla JVM. */
    static final class Drbg {
        final Mac mac; long counter = 0;
        Drbg(byte[] seed) throws Exception { mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(seed, "HmacSHA256")); }
        byte[] next(int len) {
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            while (o.size() < len) o.writeBytes(mac.doFinal(BigInteger.valueOf(counter++).toByteArray()));
            return Arrays.copyOf(o.toByteArray(), len);
        }
    }

    // ── Certificato X.509 v3 minimo, scritto a mano in DER ─────────────────────────
    static X509Certificate certificate(PublicKey pub, PrivateKey priv) throws Exception {
        byte[] sha256Rsa = seq(oid(new int[]{1, 2, 840, 113549, 1, 1, 11}), new byte[]{0x05, 0x00});
        byte[] name = seq(set(seq(oid(new int[]{2, 5, 4, 3}), tlv(0x0C, "Sordina".getBytes(StandardCharsets.UTF_8)))));
        byte[] serialBytes = Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(pub.getEncoded()), 8);
        serialBytes[0] &= 0x7F;
        byte[] tbs = seq(
            tlv(0xA0, tlv(0x02, new byte[]{2})),                 // v3
            tlv(0x02, new BigInteger(1, serialBytes).toByteArray()),
            sha256Rsa, name,
            seq(tlv(0x17, "260101000000Z".getBytes(StandardCharsets.US_ASCII)),       // UTCTime 2026-01-01
                tlv(0x18, "20760101000000Z".getBytes(StandardCharsets.US_ASCII))),    // GeneralizedTime 2076-01-01
            name, pub.getEncoded());
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(priv);
        sig.update(tbs);
        byte[] s = sig.sign();
        byte[] bits = new byte[s.length + 1];
        System.arraycopy(s, 0, bits, 1, s.length);
        byte[] der = seq(tbs, sha256Rsa, tlv(0x03, bits));
        return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(der));
    }

    static byte[] tlv(int tag, byte[] v) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(tag);
        if (v.length < 128) o.write(v.length);
        else { byte[] l = BigInteger.valueOf(v.length).toByteArray(); int off = l[0] == 0 ? 1 : 0; o.write(0x80 | (l.length - off)); o.write(l, off, l.length - off); }
        o.writeBytes(v);
        return o.toByteArray();
    }
    static byte[] cat(byte[]... parts) { ByteArrayOutputStream o = new ByteArrayOutputStream(); for (byte[] p : parts) o.writeBytes(p); return o.toByteArray(); }
    static byte[] seq(byte[]... parts) { return tlv(0x30, cat(parts)); }
    static byte[] set(byte[]... parts) { return tlv(0x31, cat(parts)); }
    static byte[] oid(int[] arcs) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(arcs[0] * 40 + arcs[1]);
        for (int i = 2; i < arcs.length; i++) {
            int v = arcs[i]; byte[] tmp = new byte[5]; int k = 0;
            do { tmp[k++] = (byte) (v & 0x7F); v >>>= 7; } while (v != 0);
            for (int j = k - 1; j >= 0; j--) o.write(tmp[j] | (j > 0 ? 0x80 : 0));
        }
        return tlv(0x06, o.toByteArray());
    }
}
