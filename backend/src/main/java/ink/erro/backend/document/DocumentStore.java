package ink.erro.backend.document;

import java.io.IOException;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import jakarta.annotation.PreDestroy;
import ink.erro.backend.ai.AiException;
import static ink.erro.backend.document.DocumentModels.*;

/** Ephemeral capability-scoped document data. No chat, database, Redis, S3 or provider file storage. */
@Service
@EnableConfigurationProperties(DocumentProperties.class)
public class DocumentStore {
    public static final int MAX_FILES = 8;
    private final DocumentProcessor extractor;
    private final DocumentProperties properties;
    private final Clock clock;
    private final Map<String, Workspace> workspaces = new HashMap<>();
    private int cachedCharacters;
    private static final int MAX_CACHED_CHARACTERS = 16_000_000;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(6), Thread.ofPlatform().daemon().name("document-worker-",0).factory());
    private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("document-expiry").factory());
    private static class Workspace {
        final Instant expiresAt; final Map<String, Job> jobs = new LinkedHashMap<>();
        Workspace(Instant expiresAt) { this.expiresAt = expiresAt; }
    }
    private static class Job {
        final String id = UUID.randomUUID().toString(), filename, hash; final long bytes;
        volatile String state = "QUEUED", progress = "Waiting to process", error = "";
        volatile Extraction extraction = new Extraction(List.of(),List.of());
        volatile Future<?> task; volatile Instant started;
        int cachedCharacters;
        Job(String filename, String hash, long bytes) { this.filename = filename; this.hash = hash; this.bytes = bytes; }
    }
    @org.springframework.beans.factory.annotation.Autowired
    public DocumentStore(DocumentProcessor extractor, DocumentProperties properties) { this(extractor, properties, Clock.systemUTC()); }
    DocumentStore(DocumentProcessor extractor, DocumentProperties properties, Clock clock) {
        this.extractor = extractor; this.properties = properties; this.clock = clock;
        if (properties.ttl().compareTo(Duration.ofMinutes(5)) < 0 || properties.ttl().compareTo(Duration.ofHours(2)) > 0)
            throw new IllegalArgumentException("Document TTL must be between 5 minutes and 2 hours.");
        cleanup.scheduleAtFixedRate(this::expire, 30, 30, TimeUnit.SECONDS);
    }
    public synchronized Problem create() {
        if (!properties.enabled()) throw new DocumentException(HttpStatus.SERVICE_UNAVAILABLE,"File analysis is disabled on this server.");
        expire();
        if (workspaces.size() >= properties.maxProblems()) throw new DocumentException(HttpStatus.TOO_MANY_REQUESTS,"File analysis is busy. Try again after existing workspaces expire.");
        byte[] secret = new byte[32]; new SecureRandom().nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant expires = clock.instant().plus(properties.ttl());
        workspaces.put(digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII)),new Workspace(expires));
        return new Problem(token,expires,MAX_FILES,DocumentExtractor.MAX_FILE_BYTES);
    }
    private Workspace workspace(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw expired();
        String key = digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        var workspace = workspaces.get(key);
        if (workspace == null) throw expired();
        if (!clock.instant().isBefore(workspace.expiresAt)) { clear(workspace); workspaces.remove(key); throw expired(); }
        return workspace;
    }
    private DocumentException expired() { return new DocumentException(HttpStatus.GONE,"These attachments expired or are unavailable. Start a new problem and attach the files again."); }
    public synchronized Status upload(String token, MultipartFile file) throws IOException {
        var workspace = workspace(token);
        String filename = DocumentExtractor.filename(file.getOriginalFilename());
        if (file.isEmpty() || file.getSize() > DocumentExtractor.MAX_FILE_BYTES)
            throw new DocumentException(HttpStatus.PAYLOAD_TOO_LARGE,"Files must be nonempty and at most 20 MiB.");
        if (workspace.jobs.size() >= MAX_FILES || workspace.jobs.values().stream().mapToLong(j -> j.bytes).sum() + file.getSize() > 60L * 1024 * 1024)
            throw new DocumentException(HttpStatus.PAYLOAD_TOO_LARGE,"Each problem supports 8 files and 60 MiB total. Remove an attachment first.");
        // Copy only after quota checks. Multipart request temporary files are deleted by the container.
        byte[] bytes;
        try (var stream = file.getInputStream()) { bytes = stream.readNBytes((int)DocumentExtractor.MAX_FILE_BYTES + 1); }
        if (bytes.length > DocumentExtractor.MAX_FILE_BYTES) throw new DocumentException(HttpStatus.PAYLOAD_TOO_LARGE,"File exceeds 20 MiB.");
        String hash = digest(bytes);
        for (var job : workspace.jobs.values()) if (job.hash.equals(hash)) return status(job,workspace);
        var job = new Job(filename, hash, bytes.length);
        workspace.jobs.put(job.id,job);
        try { job.task = workers.submit(() -> process(job, bytes)); }
        catch (RejectedExecutionException ex) {
            workspace.jobs.remove(job.id);
            throw new DocumentException(HttpStatus.TOO_MANY_REQUESTS,"Document processors are busy. Retry this file shortly.");
        }
        return status(job,workspace);
    }
    private void process(Job job, byte[] bytes) {
        synchronized (this) {
            if (!job.state.equals("QUEUED")) { Arrays.fill(bytes,(byte)0); return; }
            job.started = clock.instant(); job.state = "PROCESSING";
        }
        try {
            var extracted = extractor.extract(job.filename, bytes, progress -> {
                if (Thread.currentThread().isInterrupted() || clock.instant().isAfter(job.started.plusSeconds(300)))
                    throw DocumentException.invalid("Processing exceeded five minutes. Split the document and retry.");
                job.progress = progress;
            });
            synchronized (this) {
                if (!job.state.equals("PROCESSING")) return;
                int size=tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(extracted).length();
                if(cachedCharacters+size>MAX_CACHED_CHARACTERS) throw DocumentException.invalid("Temporary document cache is full. Remove older attachments or retry later.");
                job.cachedCharacters=size; cachedCharacters+=size;
                job.extraction = extracted; job.state = "READY"; job.progress = "Ready for questions";
            }
        } catch (Exception ex) {
            synchronized (this) {
                if (!job.state.equals("PROCESSING")) return;
                job.state = "ERROR"; job.progress = "Processing failed";
                job.error = ex instanceof DocumentException || ex instanceof AiException ? ex.getMessage() :
                        "The file could not be read safely. Check that it is valid, unencrypted and supported, then retry.";
                job.extraction = new Extraction(List.of(),List.of());
            }
        } finally { Arrays.fill(bytes,(byte)0); }
    }
    public synchronized List<Status> list(String token) {
        var workspace = workspace(token);
        return workspace.jobs.values().stream().map(job -> status(job,workspace)).toList();
    }
    public synchronized List<Extraction> read(String token, List<String> ids) {
        var workspace = workspace(token);
        if (ids == null || ids.size() > MAX_FILES || new HashSet<>(ids).size() != ids.size())
            throw DocumentException.invalid("Select at most eight distinct attachments.");
        var extractions = new ArrayList<Extraction>();
        for (String id : ids) {
            var job = workspace.jobs.get(id);
            if (job == null) throw new DocumentException(HttpStatus.NOT_FOUND,"An attachment is unavailable in this problem.");
            if (!job.state.equals("READY")) throw new DocumentException(HttpStatus.CONFLICT,"Wait for every selected attachment to finish or remove failed files.");
            extractions.add(job.extraction);
        }
        return List.copyOf(extractions);
    }
    public synchronized void remove(String token, String id) {
        var job = workspace(token).jobs.remove(id);
        if (job != null) cancel(job);
    }
    public synchronized void delete(String token) {
        if (token == null) return;
        var workspace = workspaces.remove(digest(token.getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        if (workspace != null) clear(workspace);
    }
    synchronized void expire() {
        workspaces.values().removeIf(workspace -> {
            if (!clock.instant().isBefore(workspace.expiresAt)) { clear(workspace); return true; }
            for (var job : workspace.jobs.values()) if (job.started != null && job.state.equals("PROCESSING") && clock.instant().isAfter(job.started.plusSeconds(300))) {
                cancel(job); job.state = "ERROR"; job.error = "Processing timed out. Split this document and retry.";
            }
            return false;
        });
        workers.purge();
    }
    private void cancel(Job job) {
        job.state = "REMOVED"; if (job.task != null) job.task.cancel(true);
        cachedCharacters-=job.cachedCharacters;job.cachedCharacters=0;
        job.extraction = new Extraction(List.of(),List.of());
    }
    private void clear(Workspace workspace) { workspace.jobs.values().forEach(this::cancel); workspace.jobs.clear(); }
    private Status status(Job job, Workspace workspace) { return new Status(job.id,job.filename,job.bytes,job.state,job.progress,job.error,job.extraction.warnings(),job.extraction.evidence().size(),workspace.expiresAt); }
    private static String digest(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    @PreDestroy public synchronized void close() { workspaces.values().forEach(this::clear); workspaces.clear(); workers.shutdownNow(); cleanup.shutdownNow(); }
}
