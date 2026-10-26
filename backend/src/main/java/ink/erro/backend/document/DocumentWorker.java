package ink.erro.backend.document;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.time.Duration;
import java.util.Map;
import ink.erro.backend.ai.*;
import tools.jackson.databind.json.JsonMapper;

/** Child JVM entry point. Credentials arrive through a private pipe, never arguments or files. */
public final class DocumentWorker {
    private DocumentWorker() {}
    public static void main(String[] args) throws Exception {
        var watchdog=new java.util.Timer("document-hard-deadline",true);
        watchdog.schedule(new java.util.TimerTask() { public void run() { Runtime.getRuntime().halt(3); } },240000);
        var json=JsonMapper.builder().build();
        var input=new DataInputStream(System.in);
        var config=json.readTree(input.readUTF());
        int length=input.readInt();
        if(length<1 || length>DocumentExtractor.MAX_FILE_BYTES) System.exit(2);
        byte[] bytes=input.readNBytes(length);
        if(bytes.length!=length) System.exit(2);
        var ai=new AiService(new AiProperties(URI.create(config.path("endpoint").asText()),config.path("apiKey").asText(),
            config.path("model").asText(),config.path("effort").asText(),2048,Duration.parse(config.path("timeout").asText())));
        var properties=new DocumentProperties(true,config.path("visionModel").asText(),Duration.ofMinutes(30),1);
        var extractor=new DocumentExtractor(new VisionExtractor(ai,properties));
        Object result;
        try {
            var extraction=extractor.extract(config.path("filename").asText(),bytes,progress->{
                try { Files.writeString(Path.of("progress"),progress); } catch(IOException ignored) { /* Progress is best effort. */ }
            });
            result=Map.of("extraction",extraction);
        } catch(Exception failure) {
            result=Map.of("error",failure instanceof DocumentException || failure instanceof AiException ? failure.getMessage() :
                "The document could not be parsed safely. Check that it is valid, unencrypted and supported.");
        } finally { java.util.Arrays.fill(bytes,(byte)0); }
        Files.writeString(Path.of("result.json"),json.writeValueAsString(result));
    }
}
