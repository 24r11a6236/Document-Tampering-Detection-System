import java.security.MessageDigest;

public class Hasher {
    public static String hex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
    public static String sha256(byte[] data, int off, int len) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(data, off, len);
            return hex(md.digest());
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    public static String sha256(byte[] data) { return sha256(data, 0, data.length); }
    public static String sha256(String s) { return sha256(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }

    public static int chunkSizeFor(long size) { return (int) Math.max(4096, (size + 255) / 256); }
    
    public static String[] chunks(byte[] data, int chunkSize) {
        int n = (data.length + chunkSize - 1) / chunkSize;
        String[] out = new String[n];
        for (int i = 0; i < n; i++) {
            int off = i * chunkSize, len = Math.min(chunkSize, data.length - off);
            out[i] = sha256(data, off, len).substring(0, 8);
        }
        return out;
    }
}