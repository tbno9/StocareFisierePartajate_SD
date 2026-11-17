package ro.ubm.sd.common;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Încadrarea mesajelor TCP: un antet text (writeUTF), urmat opțional de exact
 * <dimensiune> octeți de conținut. Dimensiunea este mereu în antet, înaintea octeților.
 */
public final class Protocol {

    private static final int BUFFER = 64 * 1024;

    private Protocol() {
    }

    /** Scrie antetul: 2 octeți cu lungimea, apoi textul. */
    public static void writeHeader(DataOutputStream out, String header) throws IOException {
        out.writeUTF(header);
        out.flush();
    }

    /** Citește un antet și îl împarte în cuvinte: "PUT a.txt 12 9f86..." → [PUT, a.txt, 12, 9f86...]. */
    public static String[] readHeader(DataInputStream in) throws IOException {
        return in.readUTF().split(" ");
    }

    /** Citește exact size octeți în fișierul target, calculând SHA-256 pe drum. */
    public static String receiveBytes(InputStream in, long size, Path target) throws IOException {
        MessageDigest sha = sha256();
        byte[] buffer = new byte[BUFFER];
        long remaining = size;
        try (OutputStream out = Files.newOutputStream(target)) {
            while (remaining > 0) {
                int n = in.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (n < 0) {
                    throw new EOFException("conexiune închisă după " + (size - remaining) + " din " + size + " octeți");
                }
                out.write(buffer, 0, n);
                sha.update(buffer, 0, n);
                remaining -= n;
            }
        }
        return HexFormat.of().formatHex(sha.digest());
    }

    /** SHA-256 al unui fișier local, citit pe bucăți (merge și pentru fișiere mari). */
    public static String checksum(Path file) throws IOException {
        MessageDigest sha = sha256();
        byte[] buffer = new byte[BUFFER];
        try (InputStream in = Files.newInputStream(file)) {
            int n;
            while ((n = in.read(buffer)) > 0) {
                sha.update(buffer, 0, n);
            }
        }
        return HexFormat.of().formatHex(sha.digest());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK fără SHA-256", e); // orice JDK îl are, nu se întâmplă
        }
    }
}
