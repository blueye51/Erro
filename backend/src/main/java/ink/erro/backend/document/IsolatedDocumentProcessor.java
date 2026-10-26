package ink.erro.backend.document;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import ink.erro.backend.ai.AiProperties;
import tools.jackson.databind.json.JsonMapper;

/** Resource containment for untrusted parsers. At most two child JVMs run through DocumentStore. */
@Primary
@Service
public class IsolatedDocumentProcessor implements DocumentProcessor {
    private final AiProperties ai;
    private final DocumentProperties properties;
    private final JsonMapper json=JsonMapper.builder().build();
    private final java.util.concurrent.ScheduledExecutorService janitor=java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().daemon().name("document-temp-cleanup").factory());
    public IsolatedDocumentProcessor(AiProperties ai,DocumentProperties properties) {
        this.ai=ai; this.properties=properties;
        janitor.scheduleAtFixedRate(IsolatedDocumentProcessor::removeAbandoned,0,30,TimeUnit.SECONDS);
    }
    @jakarta.annotation.PreDestroy public void close() { janitor.shutdownNow(); }
    public DocumentModels.Extraction extract(String filename,byte[] bytes,Consumer<String> progress) throws IOException {
        Path directory=Files.createTempDirectory("erro-document-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Process process=null;
        try {
            var command=new ArrayList<>(List.of(Path.of(System.getProperty("java.home"),"bin","java").toString(),
                "-Xmx256m","-XX:MaxDirectMemorySize=64m","-XX:ActiveProcessorCount=2","-XX:+ExitOnOutOfMemoryError",
                "-Djava.awt.headless=true","-Djava.io.tmpdir="+directory,"-Duser.home="+directory,
                "-Dloader.main="+DocumentWorker.class.getName(),"-cp",absoluteClasspath()));
            boolean boot=ClassLoader.getSystemClassLoader().getResource("org/springframework/boot/loader/launch/PropertiesLauncher.class")!=null;
            command.add(boot?"org.springframework.boot.loader.launch.PropertiesLauncher":DocumentWorker.class.getName());
            var builder=new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
            builder.environment().clear(); // No database, AWS, admin or other inherited credentials.
            process=builder.start();
            try(var input=new DataOutputStream(process.getOutputStream())) {
                input.writeUTF(json.writeValueAsString(Map.of("filename",filename,"endpoint",ai.endpoint().toString(),
                    "apiKey",Objects.toString(ai.apiKey(),""),"model",Objects.toString(ai.model(),""),
                    "effort",Objects.toString(ai.reasoningEffort(),""),"timeout",ai.timeout().toString(),"visionModel",properties.visionModel())));
                input.writeInt(bytes.length); input.write(bytes);
            }
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(240);
            while(!process.waitFor(1,TimeUnit.SECONDS)) {
                if(System.nanoTime()>deadline) throw DocumentException.invalid("Document processing exceeded four minutes. Split this document and retry.");
                Path status=directory.resolve("progress");
                if(Files.isRegularFile(status) && Files.size(status)<1000) progress.accept(Files.readString(status));
            }
            Path output=directory.resolve("result.json");
            if(process.exitValue()!=0 || !Files.isRegularFile(output) || Files.size(output)>4*1024*1024)
                throw DocumentException.invalid("The document exceeded safe processing resources or could not be read. Split or simplify it and retry.");
            var result=json.readTree(Files.readString(output));
            if(result.has("error")) throw DocumentException.invalid(result.path("error").asText());
            return json.treeToValue(result.path("extraction"),DocumentModels.Extraction.class);
        } catch(InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new IOException("Document processing cancelled.");
        } finally {
            if(process!=null && process.isAlive()) {
                process.destroyForcibly();
                boolean interrupted=Thread.interrupted();
                try { process.waitFor(5,TimeUnit.SECONDS); } catch(InterruptedException ignored) { interrupted=true; }
                finally { if(interrupted) Thread.currentThread().interrupt(); }
            }
            try(var paths=Files.walk(directory)) {
                for(var path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }
    private static void removeAbandoned() {
        // Child JVMs have a four-minute hard deadline. Only private directories older than five minutes are abandoned.
        Path root=Path.of(System.getProperty("java.io.tmpdir"));
        try(var dirs=Files.newDirectoryStream(root,"erro-document-*")) {
            for(var dir:dirs) {
                if(!Files.isDirectory(dir,LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(dir) ||
                    Files.readAttributes(dir,java.nio.file.attribute.BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS).creationTime().toMillis()>System.currentTimeMillis()-300000) continue;
                try(var paths=Files.walk(dir)) { for(var path:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path); }
                catch(IOException ignored) { /* Another process may be finishing cleanup. */ }
            }
        } catch(IOException ignored) { /* Retry on the next sweep; never prevent backend startup. */ }
    }
    private static String absoluteClasspath() {
        return Arrays.stream(System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))
                .map(path->Path.of(path).toAbsolutePath().toString()).collect(java.util.stream.Collectors.joining(File.pathSeparator));
    }
}
