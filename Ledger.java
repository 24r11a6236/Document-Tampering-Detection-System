import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

public class Ledger {
    public static final String GENESIS = "0".repeat(64);
    public static class Record {
        public int id; public String time, name, sha, chunks, prev, hash;
        public long size; public int chunkSize;
        String payload() { return id + "|" + time + "|" + name + "|" + size + "|" + sha + "|" + chunkSize + "|" + chunks + "|" + prev; }
        String line() { return String.join("\t", "" + id, time, name, "" + size, sha, "" + chunkSize, chunks, prev, hash); }
        public String json() {
            return "{\"id\":" + id + ",\"time\":\"" + time + "\",\"name\":" + Json.str(name) + ",\"size\":" + size
                + ",\"sha256\":\"" + sha + "\",\"chunkSize\":" + chunkSize + ",\"prev\":\"" + prev + "\",\"hash\":\"" + hash + "\"}";
        }
    }
    private final Path file;
    private final List<Record> records = new ArrayList<>();
    public Ledger(Path file) throws IOException {
        this.file = file;
        Files.createDirectories(file.toAbsolutePath().getParent());
        if (Files.exists(file)) {
            int i = 0;
            for (String l : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (l.isBlank()) continue;
                i++;
                String[] f = l.split("\t", -1);
                Record r = new Record();
                try {
                    r.id = Integer.parseInt(f[0]); r.time = f[1]; r.name = f[2]; r.size = Long.parseLong(f[3]);
                    r.sha = f[4]; r.chunkSize = Integer.parseInt(f[5]); r.chunks = f[6]; r.prev = f[7]; r.hash = f[8];
                } catch (Exception e) {
                    r.id = i; r.time = r.name = r.sha = r.chunks = r.prev = r.hash = "?"; r.chunkSize = 4096;
                }
                records.add(r);
            }
        }
    }
    public synchronized Record add(String name, long size, String sha, int chunkSize, String[] chunks) throws IOException {
        Record r = new Record();
        r.id = records.size() + 1;
        r.time = Instant.now().toString();
        r.name = name.replaceAll("[\\t\\r\\n|]", " ");
        r.size = size; r.sha = sha; r.chunkSize = chunkSize; r.chunks = String.join(",", chunks);
        r.prev = records.isEmpty() ? GENESIS : records.get(records.size() - 1).hash;
        r.hash = Hasher.sha256(r.payload());
        records.add(r);
        Files.write(file, (r.line() + "\n").getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        return r;
    }

    public synchronized Record findBySha(String sha) {
        for (Record r : records) if (r.sha.equals(sha)) return r;
        return null;
    }

    public synchronized Record latestByName(String name) {
        String n = name.replaceAll("[\\t\\r\\n|]", " ");
        for (int i = records.size() - 1; i >= 0; i--) if (records.get(i).name.equals(n)) return records.get(i);
        return null;
    }
    public synchronized List<Record> all() { return new ArrayList<>(records); }
    
    public synchronized int firstBroken() {
        String prev = GENESIS;
        for (Record r : records) {
            if (!r.prev.equals(prev) || !r.hash.equals(Hasher.sha256(r.payload()))) return r.id;
            prev = r.hash;
        }
        return -1;
    }
}