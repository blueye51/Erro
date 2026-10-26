package ink.erro.backend.guest;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.json.JsonMapper;
import ink.erro.backend.chat.ChatService;
import ink.erro.backend.document.*;
import static ink.erro.backend.guest.GuestModels.*;

@Repository
public class GuestRepository {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final JsonMapper json=JsonMapper.builder().build();
    public GuestRepository(JdbcTemplate jdbc,PlatformTransactionManager manager) { this.jdbc=jdbc;tx=new TransactionTemplate(manager); }
    public UUID findGuest(String hash,Duration ttl) {
        var ids=jdbc.queryForList("SELECT id FROM erro_chat.guest WHERE token_hash=? AND expires_at>now()",UUID.class,hash);
        if(ids.isEmpty()) return null;
        UUID id=ids.getFirst();
        jdbc.update("UPDATE erro_chat.guest SET last_seen_at=now(),expires_at=? WHERE id=? AND last_seen_at<now()-interval '1 hour'",
                Timestamp.from(Instant.now().plus(ttl)),id);
        return id;
    }
    public UUID createGuest(String hash,Duration ttl) {
        return tx.execute(s->{
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO erro_chat.guest(id,token_hash,expires_at) VALUES (?,?,?)",id,hash,Timestamp.from(Instant.now().plus(ttl)));
            createChat(id); return id;
        });
    }
    public void deleteGuest(UUID guest) { jdbc.update("DELETE FROM erro_chat.guest WHERE id=?",guest); }
    public UUID replaceGuest(UUID guest,String hash,Duration ttl) {
        return tx.execute(s->{ deleteGuest(guest);return createGuest(hash,ttl); });
    }
    public Chat createChat(UUID guest) {
        return tx.execute(s->{
            if(jdbc.queryForList("SELECT id FROM erro_chat.guest WHERE id=? FOR UPDATE",guest).isEmpty()) throw GuestException.missing();
            if(jdbc.queryForObject("SELECT count(*) FROM erro_chat.conversation WHERE guest_id=?",Integer.class,guest)>=1000)
                throw new GuestException(HttpStatus.TOO_MANY_REQUESTS,"This browser has 1,000 saved chats. Delete an old chat before creating another.");
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO erro_chat.conversation(id,guest_id) VALUES (?,?)",id,guest);
            return owned(guest,id,false);
        });
    }
    public ChatList chats(UUID guest,int offset) {
        if(offset<0 || offset>1000) throw new IllegalArgumentException("Invalid chat page.");
        var rows=jdbc.queryForList("SELECT * FROM erro_chat.conversation WHERE guest_id=? ORDER BY updated_at DESC,id LIMIT 51 OFFSET ?",guest,offset);
        return new ChatList(rows.stream().limit(50).map(this::chat).toList(),rows.size()>50);
    }
    public Chat owned(UUID guest,UUID id,boolean lock) {
        var rows=jdbc.queryForList("SELECT * FROM erro_chat.conversation WHERE guest_id=? AND id=?"+(lock?" FOR UPDATE":""),guest,id);
        if(rows.isEmpty()) throw GuestException.missing();
        return chat(rows.getFirst());
    }
    public Detail detail(UUID guest,UUID id,Long before) {
        Chat chat=owned(guest,id,false);
        var rows=jdbc.queryForList("SELECT * FROM erro_chat.message WHERE conversation_id=? AND id<? ORDER BY id DESC LIMIT 101",id,before==null?Long.MAX_VALUE:before);
        var messages=new ArrayList<>(rows.stream().limit(100).map(this::message).toList());Collections.reverse(messages);
        var pending=jdbc.queryForList("SELECT * FROM erro_chat.chat_request WHERE conversation_id=? AND status IN ('QUEUED','PROCESSING')",id);
        return new Detail(chat,messages,rows.size()>100,attachments(id),pending.isEmpty()?null:request(pending.getFirst()));
    }
    public void deleteChat(UUID guest,UUID chat) { owned(guest,chat,false);jdbc.update("DELETE FROM erro_chat.conversation WHERE id=? AND guest_id=?",chat,guest); }
    public Request enqueue(UUID guest,UUID chat,UUID requestId,String text,List<UUID> selected) {
        if(text==null || text.isBlank() || text.length()>8000) throw new IllegalArgumentException("Enter a message between 1 and 8,000 characters.");
        if(text.codePoints().anyMatch(c->Character.isISOControl(c) && c!='\n' && c!='\r' && c!='\t')) throw new IllegalArgumentException("Unsupported control characters in message.");
        List<UUID> ids=selected==null?List.of():List.copyOf(selected);
        if(ids.size()>8 || new HashSet<>(ids).size()!=ids.size()) throw new IllegalArgumentException("Select at most eight distinct files.");
        return tx.execute(s->{
            owned(guest,chat,true);
            var prior=jdbc.queryForList("SELECT * FROM erro_chat.chat_request WHERE conversation_id=? AND id=?",chat,requestId);
            if(!prior.isEmpty()) {
                var found=request(prior.getFirst());
                if(!found.message().equals(text.strip()) || !found.attachmentIds().equals(ids)) throw GuestException.busy("This request identifier was already used for another message.");
                return found;
            }
            if(jdbc.queryForObject("SELECT count(*) FROM erro_chat.chat_request WHERE conversation_id=? AND status IN ('QUEUED','PROCESSING')",Integer.class,chat)>0)
                throw GuestException.busy("A reply is still being prepared. Wait or cancel it before sending again.");
            jdbc.queryForList("SELECT pg_advisory_xact_lock(941410)");
            int queued=jdbc.queryForObject("SELECT count(*) FROM erro_chat.chat_request WHERE status IN ('QUEUED','PROCESSING')",Integer.class);
            int own=jdbc.queryForObject("SELECT count(*) FROM erro_chat.chat_request r JOIN erro_chat.conversation c ON c.id=r.conversation_id WHERE c.guest_id=? AND r.status IN ('QUEUED','PROCESSING')",Integer.class,guest);
            if(queued>=64 || own>=3) throw new GuestException(HttpStatus.TOO_MANY_REQUESTS,"Chat is busy. Wait for an existing reply and retry.");
            extractions(chat,ids);
            jdbc.update("INSERT INTO erro_chat.chat_request(id,conversation_id,message,attachment_ids,status) VALUES (?,?,?,CAST(? AS jsonb),'QUEUED')",requestId,chat,text.strip(),json.writeValueAsString(ids));
            return request(guest,chat,requestId);
        });
    }
    public Request request(UUID guest,UUID chat,UUID id) {
        owned(guest,chat,false);
        var rows=jdbc.queryForList("SELECT * FROM erro_chat.chat_request WHERE conversation_id=? AND id=?",chat,id);
        if(rows.isEmpty()) throw GuestException.missing();return request(rows.getFirst());
    }
    public void cancel(UUID guest,UUID chat,UUID id) {
        owned(guest,chat,false);
        jdbc.update("UPDATE erro_chat.chat_request SET status='FAILED',error='Reply cancelled.',lease=NULL,lease_until=NULL,finished_at=now() WHERE conversation_id=? AND id=? AND status IN ('QUEUED','PROCESSING')",chat,id);
    }
    public Work claimChat() {
        UUID lease=UUID.randomUUID();
        var rows=jdbc.queryForList("""
            UPDATE erro_chat.chat_request r SET status='PROCESSING',lease=?,lease_until=now()+interval '150 seconds'
            WHERE (r.conversation_id,r.id)=(SELECT q.conversation_id,q.id FROM erro_chat.chat_request q
                JOIN erro_chat.conversation c ON c.id=q.conversation_id JOIN erro_chat.guest g ON g.id=c.guest_id
                WHERE q.status='QUEUED' AND g.expires_at>now() ORDER BY q.created_at FOR UPDATE OF q SKIP LOCKED LIMIT 1)
            RETURNING r.*
            """,lease);
        if(rows.isEmpty()) return null;var row=rows.getFirst();
        return new Work((UUID)row.get("conversation_id"),(UUID)row.get("id"),lease,(String)row.get("message"),ids(row.get("attachment_ids")));
    }
    public List<Message> history(UUID chat) {
        var rows=jdbc.queryForList("SELECT m.* FROM erro_chat.message m JOIN erro_chat.conversation c ON c.id=m.conversation_id WHERE c.id=? AND m.id>=c.context_start ORDER BY m.id DESC LIMIT 64",chat);
        var result=new ArrayList<>(rows.stream().map(this::message).toList());Collections.reverse(result);return result;
    }
    public List<Message> relatedHistory(UUID chat,String question) {
        var ignored=Set.of("what","how","the","and","this","that","with","about","have","does","can","you","your","please","mis","kas","see","seda");
        var terms=Arrays.stream(question.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(t->t.length()>=3 && !ignored.contains(t)).distinct().limit(12).toList();
        if(terms.isEmpty()) return List.of();String query=String.join(" | ",terms);
        return jdbc.queryForList("""
            SELECT m.* FROM erro_chat.message m JOIN erro_chat.conversation c ON c.id=m.conversation_id
            WHERE c.id=? AND m.id>=c.context_start AND m.role='user'
                AND to_tsvector('simple',m.content) @@ to_tsquery('simple',?)
            ORDER BY ts_rank_cd(to_tsvector('simple',m.content),to_tsquery('simple',?)) DESC,m.id DESC LIMIT 8
            """,chat,query,query).stream().map(this::message).toList();
    }
    public boolean complete(Work work,ChatService.ChatReply answer) {
        return Boolean.TRUE.equals(tx.execute(s->{
            if(jdbc.queryForList("SELECT id FROM erro_chat.conversation WHERE id=? FOR UPDATE",work.chatId()).isEmpty()) return false;
            var rows=jdbc.queryForList("SELECT * FROM erro_chat.chat_request WHERE conversation_id=? AND id=? AND status='PROCESSING' AND lease=? FOR UPDATE",work.chatId(),work.id(),work.lease());
            if(rows.isEmpty()) return false;
            List<String> names=new ArrayList<>();
            for(UUID id:work.attachmentIds()) names.addAll(jdbc.queryForList("SELECT filename FROM erro_chat.attachment WHERE conversation_id=? AND id=?",String.class,work.chatId(),id));
            Long userId=jdbc.queryForObject("INSERT INTO erro_chat.message(conversation_id,role,content,filenames) VALUES (?,'user',?,CAST(? AS jsonb)) RETURNING id",Long.class,work.chatId(),work.message(),json.writeValueAsString(names));
            Long assistantId=jdbc.queryForObject("INSERT INTO erro_chat.message(conversation_id,role,content,answer) VALUES (?,'assistant',?,CAST(? AS jsonb)) RETURNING id",Long.class,work.chatId(),answer.reply(),json.writeValueAsString(answer));
            jdbc.update("UPDATE erro_chat.chat_request SET status='COMPLETE',assistant_message_id=?,finished_at=now(),lease=NULL,lease_until=NULL WHERE conversation_id=? AND id=?",assistantId,work.chatId(),work.id());
            String title=work.message().replaceAll("\\s+"," ");title=title.substring(0,Math.min(80,title.length()));
            jdbc.update("UPDATE erro_chat.conversation SET title=CASE WHEN title='New chat' THEN ? ELSE title END,context_start=CASE WHEN ? THEN ? ELSE context_start END,updated_at=now() WHERE id=?",title,answer.contextReset(),userId,work.chatId());
            return true;
        }));
    }
    public void fail(Work work,String error) { jdbc.update("UPDATE erro_chat.chat_request SET status='FAILED',error=?,finished_at=now(),lease=NULL,lease_until=NULL WHERE conversation_id=? AND id=? AND lease=? AND status='PROCESSING'",safe(error),work.chatId(),work.id(),work.lease()); }
    public synchronized Attachment upload(UUID guest,UUID chat,MultipartFile file) throws IOException {
        String filename=DocumentExtractor.filename(file.getOriginalFilename());
        if(file.isEmpty() || file.getSize()>DocumentExtractor.MAX_FILE_BYTES) throw new GuestException(HttpStatus.PAYLOAD_TOO_LARGE,"Files must be nonempty and at most 20 MiB.");
        byte[] bytes;
        try(var input=file.getInputStream()) { bytes=input.readNBytes((int)DocumentExtractor.MAX_FILE_BYTES+1); }
        if(bytes.length==0 || bytes.length>DocumentExtractor.MAX_FILE_BYTES) throw new GuestException(HttpStatus.PAYLOAD_TOO_LARGE,"File exceeds the upload limit.");
        try {
            return tx.execute(s->{
                owned(guest,chat,true);
                String hash=GuestAuth.digest(bytes);
                var duplicate=jdbc.queryForList("SELECT id FROM erro_chat.attachment WHERE conversation_id=? AND sha256=?",UUID.class,chat,hash);
                if(!duplicate.isEmpty()) return attachment(duplicate.getFirst());
                var quota=jdbc.queryForMap("SELECT count(*) AS files,COALESCE(sum(file_bytes),0) AS bytes FROM erro_chat.attachment WHERE conversation_id=?",chat);
                if(((Number)quota.get("files")).intValue()>=8 || ((Number)quota.get("bytes")).longValue()+bytes.length>60L*1024*1024)
                    throw new GuestException(HttpStatus.PAYLOAD_TOO_LARGE,"Each chat supports eight files and 60 MiB total. Remove a file first.");
                jdbc.queryForList("SELECT pg_advisory_xact_lock(941411)");
                var waiting=jdbc.queryForMap("SELECT count(*) AS files,COALESCE(sum(file_bytes),0) AS bytes FROM erro_chat.attachment WHERE status IN ('QUEUED','PROCESSING')");
                if(((Number)waiting.get("files")).intValue()>=8 || ((Number)waiting.get("bytes")).longValue()+bytes.length>128L*1024*1024)
                    throw new GuestException(HttpStatus.TOO_MANY_REQUESTS,"Document processors are busy. Retry shortly.");
                UUID id=UUID.randomUUID();
                jdbc.update("INSERT INTO erro_chat.attachment(id,conversation_id,filename,file_bytes,sha256,status,original) VALUES (?,?,?,?,?,'QUEUED',?)",id,chat,filename,bytes.length,hash,bytes);
                return attachment(id);
            });
        } finally { Arrays.fill(bytes,(byte)0); }
    }
    public List<Attachment> attachments(UUID chat) {
        return jdbc.queryForList("SELECT id FROM erro_chat.attachment WHERE conversation_id=? ORDER BY created_at,id",UUID.class,chat).stream().map(this::attachment).toList();
    }
    private Attachment attachment(UUID id) {
        var r=jdbc.queryForMap("SELECT id,filename,file_bytes,status,progress,error,extraction FROM erro_chat.attachment WHERE id=?",id);
        var e=r.get("extraction")==null?null:json.readValue(r.get("extraction").toString(),DocumentModels.Extraction.class);
        return new Attachment(id,(String)r.get("filename"),((Number)r.get("file_bytes")).longValue(),(String)r.get("status"),(String)r.get("progress"),(String)r.get("error"),e==null?List.of():e.warnings(),e==null?0:e.evidence().size());
    }
    public void removeAttachment(UUID guest,UUID chat,UUID file) { owned(guest,chat,false);jdbc.update("DELETE FROM erro_chat.attachment WHERE conversation_id=? AND id=?",chat,file); }
    public List<DocumentModels.Extraction> extractions(UUID chat,List<UUID> ids) {
        var result=new ArrayList<DocumentModels.Extraction>();
        for(UUID id:ids) {
            var rows=jdbc.queryForList("SELECT status,extraction FROM erro_chat.attachment WHERE conversation_id=? AND id=?",chat,id);
            if(rows.isEmpty()) throw GuestException.missing();var row=rows.getFirst();
            if(!row.get("status").equals("READY")) throw GuestException.busy("Wait for selected files to finish, or deselect failed files.");
            result.add(json.readValue(row.get("extraction").toString(),DocumentModels.Extraction.class));
        }
        return result;
    }
    public FileWork claimFile() {
        UUID lease=UUID.randomUUID();
        var rows=jdbc.queryForList("""
            UPDATE erro_chat.attachment a SET status='PROCESSING',lease=?,lease_until=now()+interval '5 minutes'
            WHERE a.id=(SELECT q.id FROM erro_chat.attachment q JOIN erro_chat.conversation c ON c.id=q.conversation_id
                JOIN erro_chat.guest g ON g.id=c.guest_id WHERE q.status='QUEUED' AND g.expires_at>now()
                ORDER BY q.created_at FOR UPDATE OF q SKIP LOCKED LIMIT 1) RETURNING a.*
            """,lease);
        if(rows.isEmpty()) return null;var row=rows.getFirst();
        return new FileWork((UUID)row.get("conversation_id"),(UUID)row.get("id"),lease,(String)row.get("filename"),(byte[])row.get("original"));
    }
    public boolean progress(FileWork work,String message) {
        return jdbc.update("UPDATE erro_chat.attachment SET progress=? WHERE id=? AND lease=? AND status='PROCESSING'",safe(message),work.id(),work.lease())==1;
    }
    public void completeFile(FileWork work,DocumentModels.Extraction e) {
        String progress=e.evidence().stream().anyMatch(DocumentModels.Evidence::incomplete)?"Ready for questions; some readings need review":"Ready for questions";
        jdbc.update("UPDATE erro_chat.attachment SET status='READY',progress=?,extraction=CAST(? AS jsonb),original=NULL,lease=NULL,lease_until=NULL WHERE id=? AND lease=? AND status='PROCESSING'",progress,json.writeValueAsString(e),work.id(),work.lease());
    }
    public void failFile(FileWork work,String error) { jdbc.update("UPDATE erro_chat.attachment SET status='ERROR',error=?,progress='Processing failed',original=NULL,lease=NULL,lease_until=NULL WHERE id=? AND lease=? AND status='PROCESSING'",safe(error),work.id(),work.lease()); }
    public void cleanup() {
        jdbc.update("UPDATE erro_chat.chat_request SET status='FAILED',error='Reply processing was interrupted. Please retry.',lease=NULL,lease_until=NULL,finished_at=now() WHERE status='PROCESSING' AND lease_until<now()");
        jdbc.update("UPDATE erro_chat.attachment SET status='ERROR',error='File processing was interrupted. Remove and reattach the file.',original=NULL,lease=NULL,lease_until=NULL WHERE status='PROCESSING' AND lease_until<now()");
        jdbc.update("DELETE FROM erro_chat.guest WHERE expires_at<now()");
    }
    private Chat chat(Map<String,Object> r) { return new Chat((UUID)r.get("id"),(String)r.get("title"),instant(r.get("created_at")),instant(r.get("updated_at"))); }
    private Message message(Map<String,Object> r) { return new Message(((Number)r.get("id")).longValue(),(String)r.get("role"),(String)r.get("content"),r.get("answer")==null?null:json.readValue(r.get("answer").toString(),ChatService.ChatReply.class),Arrays.asList(json.readValue(r.get("filenames").toString(),String[].class)),instant(r.get("created_at"))); }
    private Request request(Map<String,Object> r) { return new Request((UUID)r.get("id"),(String)r.get("status"),(String)r.get("message"),ids(r.get("attachment_ids")),(String)r.get("error"),instant(r.get("created_at"))); }
    private List<UUID> ids(Object value) { return Arrays.asList(json.readValue(value.toString(),UUID[].class)); }
    private Instant instant(Object value) { return ((Timestamp)value).toInstant(); }
    private String safe(String message) { String s=Objects.toString(message,"Request failed.");return s.substring(0,Math.min(300,s.length())); }
}
