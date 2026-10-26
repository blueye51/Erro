package ink.erro.backend.guest;

import java.util.*;
import java.util.concurrent.*;
import jakarta.annotation.*;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ink.erro.backend.ai.AiException;
import ink.erro.backend.chat.ChatService;
import ink.erro.backend.document.*;

/** PostgreSQL jobs survive restarts; SKIP LOCKED and leases coordinate multiple backend instances. */
@Service
public class GuestJobs {
    private final GuestRepository repository;
    private final ChatService chat;
    private final DocumentProcessor documents;
    private final ScheduledExecutorService workers=Executors.newScheduledThreadPool(5,Thread.ofPlatform().daemon().name("saved-chat-job-",0).factory());
    public GuestJobs(GuestRepository repository,ChatService chat,DocumentProcessor documents) { this.repository=repository;this.chat=chat;this.documents=documents; }
    @PostConstruct public void start() {
        for(int i=0;i<2;i++) {
            workers.scheduleWithFixedDelay(()->guard(this::runChat),0,500,TimeUnit.MILLISECONDS);
            workers.scheduleWithFixedDelay(()->guard(this::runFile),0,500,TimeUnit.MILLISECONDS);
        }
        workers.scheduleWithFixedDelay(()->guard(repository::cleanup),0,30,TimeUnit.SECONDS);
    }
    public void runChat() {
        var work=repository.claimChat();if(work==null) return;
        try {
            var history=repository.history(work.chatId());
            var selected=new LinkedHashMap<Long,GuestModels.Message>();int length=0;
            var latest=history.reversed().stream().filter(m->m.role().equals("user")).findFirst();
            if(latest.isPresent()) { selected.put(latest.get().id(),latest.get());length=latest.get().text().length(); }
            int relatedLength=0;
            for(var related:repository.relatedHistory(work.chatId(),work.message())) {
                if(!selected.containsKey(related.id()) && relatedLength+related.text().length()<=4000 && length+related.text().length()<=8000) {
                    selected.put(related.id(),related);length+=related.text().length();relatedLength+=related.text().length();
                }
            }
            for(var message:history.reversed()) if(message.role().equals("user") && length+message.text().length()<=8000) {
                if(!selected.containsKey(message.id())) { selected.put(message.id(),message);length+=message.text().length(); }
            }
            var users=selected.values().stream().sorted(Comparator.comparingLong(GuestModels.Message::id)).map(GuestModels.Message::text).toList();
            var dialogue=new ArrayList<ChatService.Turn>();length=0;
            for(var message:history.reversed()) {
                String text=message.text().substring(0,Math.min(8000,message.text().length()));
                if(length+text.length()>8000) break;
                dialogue.add(new ChatService.Turn(message.role(),text));length+=text.length();
            }
            Collections.reverse(dialogue);
            var answer=chat.replySaved(work.message(),users,dialogue,repository.extractions(work.chatId(),work.attachmentIds()));
            repository.complete(work,answer);
        } catch(Exception e) {
            repository.fail(work,e instanceof AiException || e instanceof GuestException || e instanceof DocumentException?e.getMessage():"The reply could not be prepared. Please retry.");
            LoggerFactory.getLogger(getClass()).warn("saved chat job failed type={}",e.getClass().getSimpleName());
        }
    }
    public void runFile() {
        var work=repository.claimFile();if(work==null) return;
        try {
            var evidence=documents.extract(work.filename(),work.bytes(),progress->{
                if(Thread.currentThread().isInterrupted() || !repository.progress(work,progress)) throw new CancellationException();
            });
            repository.completeFile(work,evidence);
        } catch(Exception e) {
            repository.failFile(work,e instanceof DocumentException || e instanceof AiException?e.getMessage():"The file could not be processed. Check its format and retry.");
        } finally { Arrays.fill(work.bytes(),(byte)0); }
    }
    private void guard(Runnable action) {
        try { action.run(); } catch(RuntimeException e) { LoggerFactory.getLogger(getClass()).warn("saved chat worker unavailable type={}",e.getClass().getSimpleName()); }
    }
    @PreDestroy public void close() { workers.shutdownNow(); }
}
