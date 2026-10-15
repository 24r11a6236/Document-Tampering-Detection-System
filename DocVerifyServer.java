import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class DocVerifyServer {
    static final long MAX = 50L * 1024 * 1024;
    static Ledger ledger;
    interface H { String run(HttpExchange ex) throws Exception; }
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        ledger = new Ledger(Paths.get("data", "ledger.tsv"));
        HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
        s.createContext("/api/register", ex -> handle(ex, "POST", DocVerifyServer::register));
        s.createContext("/api/verify", ex -> handle(ex, "POST", DocVerifyServer::verify));
        s.createContext("/api/ledger", ex -> handle(ex, "GET", DocVerifyServer::ledgerJson));
        s.createContext("/api/check", ex -> handle(ex, "GET", DocVerifyServer::check));
        s.createContext("/", ex -> {
            byte[] page = Files.readAllBytes(Paths.get("web", "index.html"));
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, page.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(page); }
        });
        s.start();
        System.out.println("DocVerify running -> http://localhost:" + port);
    }
    static void handle(HttpExchange ex, String method, H h) throws IOException {
        int code = 200; String out;
        try {
            if (!ex.getRequestMethod().equals(method)) { code = 405; out = "{\"error\":\"Method not allowed\"}"; }
            else out = h.run(ex);
        } catch (IllegalArgumentException e) { code = 400; out = "{\"error\":" + Json.str(e.getMessage()) + "}"; }
        catch (Exception e) { code = 500; out = "{\"error\":\"Server error\"}"; e.printStackTrace(); }
        byte[] b = out.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(b); }
    }
    static byte[] body(HttpExchange ex) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        byte[] buf = new byte[8192]; int n; long t = 0;
        try (InputStream in = ex.getRequestBody()) {
            while ((n = in.read(buf)) > 0) {
                t += n;
                if (t > MAX) throw new IllegalArgumentException("File exceeds the 50 MB limit");
                o.write(buf, 0, n);
            }
        }
        if (t == 0) throw new IllegalArgumentException("Empty file");
        return o.toByteArray();
    }
    static String fileName(HttpExchange ex) throws Exception {
        String n = ex.getRequestHeaders().getFirst("X-File-Name");
        if (n == null || n.isBlank()) throw new IllegalArgumentException("Missing file name");
        return URLDecoder.decode(n, "UTF-8");
    }
    static String register(HttpExchange ex) throws Exception {
        String name = fileName(ex); byte[] d = body(ex);
        String sha = Hasher.sha256(d);
        Ledger.Record dup = ledger.findBySha(sha);
        if (dup != null) return "{\"status\":\"ALREADY_REGISTERED\",\"record\":" + dup.json() + "}";
        int cs = Hasher.chunkSizeFor(d.length);
        Ledger.Record r = ledger.add(name, d.length, sha, cs, Hasher.chunks(d, cs));
        return "{\"status\":\"REGISTERED\",\"record\":" + r.json() + "}";
    }
    static String verify(HttpExchange ex) throws Exception {
        String name = fileName(ex); byte[] d = body(ex);
        String sha = Hasher.sha256(d);
        String head = "{\"sha256\":\"" + sha + "\",\"size\":" + d.length + ",";
        Ledger.Record exact = ledger.findBySha(sha);
        if (exact != null) return head + "\"status\":\"VERIFIED\",\"record\":" + exact.json() + "}";
        Ledger.Record base = ledger.latestByName(name);
        if (base == null) return head + "\"status\":\"UNREGISTERED\"}";
        
        String[] old = base.chunks.split(","), now = Hasher.chunks(d, base.chunkSize);
        int total = Math.max(old.length, now.length), changed = 0;
        StringBuilder map = new StringBuilder();
        List<String> ranges = new ArrayList<>();
        long limit = Math.max(d.length, base.size), start = -1;
        for (int i = 0; i <= total; i++) {
            boolean diff = i < total && (i >= old.length || i >= now.length || !old[i].equals(now[i]));
            if (i < total) { map.append(diff ? '1' : '0'); if (diff) changed++; }
            if (diff && start < 0) start = (long) i * base.chunkSize;
            if (!diff && start >= 0) {
                ranges.add("\"" + start + "-" + (Math.min((long) i * base.chunkSize, limit) - 1) + "\"");
                start = -1;
            }
        }
        if (ranges.size() > 20) ranges = ranges.subList(0, 20);
        return head + "\"status\":\"TAMPERED\",\"base\":" + base.json() + ",\"sizeBefore\":" + base.size
            + ",\"changedChunks\":" + changed + ",\"totalChunks\":" + total + ",\"chunkSize\":" + base.chunkSize
            + ",\"map\":\"" + map + "\",\"ranges\":[" + String.join(",", ranges) + "]}";
    }
    static String ledgerJson(HttpExchange ex) {
        List<String> items = new ArrayList<>();
        for (Ledger.Record r : ledger.all()) items.add(r.json());
        Collections.reverse(items);
        return "{\"records\":[" + String.join(",", items) + "]}";
    }
    static String check(HttpExchange ex) {
        int bad = ledger.firstBroken();
        return "{\"intact\":" + (bad < 0) + ",\"brokenAt\":" + bad + ",\"records\":" + ledger.all().size() + "}";
    }
}