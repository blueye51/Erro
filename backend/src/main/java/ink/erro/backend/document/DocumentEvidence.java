package ink.erro.backend.document;

import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import static ink.erro.backend.document.DocumentModels.*;

/** Lossless prompt projection: structured items are sent once, with unknown fields omitted. */
public final class DocumentEvidence {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private DocumentEvidence() {}

    public static Evidence compact(Evidence e) {
        if(e.items().isEmpty() || !e.method().equals("VISION")) return e;
        // Older saved extractions duplicated the same item JSON inside text. Also handle a
        // first text chunk cut midway through that JSON: its complete items already exist.
        String items=JSON.writeValueAsString(e.items());
        int start=e.text().indexOf("\n[");
        String text=e.text();
        if(start>=0 && items.startsWith(text.substring(start+1))) text=text.substring(0,start);
        else if(text.equals(items)) text="";
        return new Evidence(e.id(),e.filename(),e.location(),e.method(),text,e.items(),e.incomplete());
    }
    public static Map<String,Object> prompt(Evidence evidence) {
        var e=compact(evidence);
        var result=new LinkedHashMap<String,Object>();
        result.put("id",e.id());result.put("filename",e.filename());result.put("location",e.location());result.put("method",e.method());
        if(!e.text().isBlank()) result.put("text",e.text());
        if(e.incomplete()) result.put("incomplete",true);
        if(!e.items().isEmpty()) result.put("items",e.items().stream().map(DocumentEvidence::item).toList());
        return result;
    }
    private static Map<String,Object> item(Item item) {
        var result=new LinkedHashMap<String,Object>();
        if(!item.reference().isBlank()) result.put("reference",item.reference());
        result.put("description",item.description());result.put("basis",item.basis());
        if(item.quantity()!=null) result.put("quantity",item.quantity());
        if(!item.unit().isBlank()) result.put("unit",item.unit());
        if(item.modulesEach()!=null) result.put("modulesEach",item.modulesEach());
        if(!item.partNumber().isBlank()) result.put("partNumber",item.partNumber());
        return result;
    }
    public static String searchable(Evidence e) {
        return e.text()+"\n"+e.items().stream().map(i->i.reference()+" "+i.description()+" "+i.partNumber())
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
