package ink.erro.backend.document;

import java.util.*;
import tools.jackson.databind.json.JsonMapper;
import static ink.erro.backend.document.DocumentModels.*;

/** Lossless prompt projection: item column names are declared once; unknown values remain null. */
public final class DocumentEvidence {
    private static final JsonMapper JSON=JsonMapper.builder().build();
    private static final java.util.regex.Pattern GROUP=java.util.regex.Pattern.compile(", items \\d+–\\d+, part \\d+$");
    public static final List<String> ITEM_COLUMNS=List.of("reference","description","quantity","unit","basis","modulesEach","partNumber");
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
        return prompt(evidence,evidence.id());
    }
    public static Map<String,Object> prompt(Evidence evidence,String citationId) {
        var e=compact(evidence);
        var result=new LinkedHashMap<String,Object>();
        result.put("id",citationId);result.put("filename",e.filename());result.put("location",e.location());result.put("method",e.method());
        if(!e.text().isBlank()) result.put("text",e.text());
        if(e.incomplete()) result.put("incomplete",true);
        if(!e.items().isEmpty()) result.put("items",e.items().stream().map(DocumentEvidence::item).toList());
        return result;
    }
    /** Repeated file/crop metadata is shared without dropping any reading or source location. */
    public static Map<String,Object> promptBatch(List<Evidence> evidence) {
        var sources=new LinkedHashMap<String,Map<String,Object>>();
        var sourceIds=new LinkedHashMap<Map<String,Object>,String>();
        var excerpts=new ArrayList<Map<String,Object>>();
        for(int i=0;i<evidence.size();i++) {
            var e=evidence.get(i);
            String location=e.location(),suffix="";
            // Only split the suffix produced by the vision collector. Other location formats stay intact.
            var group=GROUP.matcher(location);
            if(e.method().equals("VISION") && group.find()) {
                suffix=location.substring(group.start());location=location.substring(0,group.start());
            }
            var source=Map.<String,Object>of("filename",e.filename(),"location",location,"method",e.method());
            String sourceId=sourceIds.computeIfAbsent(source,key->"S"+(sourceIds.size()+1));
            sources.putIfAbsent(sourceId,source);
            var excerpt=prompt(e,"D"+(i+1));
            excerpt.remove("filename");excerpt.remove("location");excerpt.remove("method");
            excerpt.put("source",sourceId);
            if(!suffix.isEmpty()) excerpt.put("locationSuffix",suffix);
            if("Visual device readings.".equals(excerpt.get("text"))) excerpt.remove("text");
            excerpts.add(excerpt);
        }
        return Map.of("sources",sources,"excerpts",excerpts,"itemColumns",ITEM_COLUMNS);
    }
    private static List<Object> item(Item item) {
        return Arrays.asList(item.reference(),item.description(),item.quantity(),item.unit(),item.basis(),item.modulesEach(),item.partNumber());
    }
    public static String searchable(Evidence e) {
        return e.text()+"\n"+e.items().stream().map(i->i.reference()+" "+i.description()+" "+i.partNumber())
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
