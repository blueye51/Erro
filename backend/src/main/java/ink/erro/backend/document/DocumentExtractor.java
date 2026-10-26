package ink.erro.backend.document;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipInputStream;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.stereotype.Service;
import static ink.erro.backend.document.DocumentModels.*;

@Service
public class DocumentExtractor implements DocumentProcessor {
    public static final long MAX_FILE_BYTES = 20 * 1024 * 1024;
    static final int MAX_PAGES = 40, MAX_CHARS = 400000, MAX_EXCERPTS = 500;
    private static final Set<String> TYPES = Set.of("pdf", "png", "jpg", "jpeg", "webp", "docx", "doc", "xlsx", "xls", "csv", "tsv", "txt", "md");
    private final VisionExtractor vision;
    public DocumentExtractor(VisionExtractor vision) {
        this.vision = vision;
        ZipSecureFile.setMinInflateRatio(.02);
        ZipSecureFile.setMaxEntrySize(32 * 1024 * 1024);
        ZipSecureFile.setMaxTextSize(MAX_CHARS);
    }
    public static String filename(String name) {
        if (name == null) throw DocumentException.invalid("A filename is required.");
        String clean = name.replace('\\', '/'); clean = clean.substring(clean.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}\\p{Cf}]", "").strip();
        if (clean.isEmpty() || clean.length() > 160) throw DocumentException.invalid("Use a filename between 1 and 160 characters.");
        if (!TYPES.contains(extension(clean))) throw DocumentException.invalid("Supported files: PDF, PNG, JPG, WebP, DOCX, DOC, XLSX, XLS, CSV, TSV, TXT and Markdown.");
        return clean;
    }
    private static String extension(String name) { return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT); }
    public Extraction extract(String filename, byte[] bytes, Consumer<String> progress) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_FILE_BYTES) throw DocumentException.invalid("Files must be nonempty and at most 20 MiB.");
        String ext = extension(filename);
        var out = new Collector(filename);
        int[] budget = {VisionExtractor.MAX_TILES};
        switch (ext) {
            case "pdf" -> {
                magic(bytes, "%PDF-".getBytes(StandardCharsets.US_ASCII));
                pdf(filename, bytes, out, budget, progress);
            }
            case "png", "jpg", "jpeg", "webp" -> raster(filename, ext, bytes, out, budget, progress);
            case "docx", "xlsx" -> {
                validateZip(bytes);
                if (ext.equals("docx")) word(bytes, out); else workbook(bytes, out);
            }
            case "doc", "xls" -> {
                magic(bytes, new byte[]{(byte)0xd0,(byte)0xcf,0x11,(byte)0xe0,(byte)0xa1,(byte)0xb1,0x1a,(byte)0xe1});
                if (ext.equals("doc")) {
                    try (var doc = new HWPFDocument(new ByteArrayInputStream(bytes)); var extractor = new WordExtractor(doc)) {
                        out.add("Document paragraphs (pagination unavailable)", "TEXT", extractor.getText(), List.of());
                        out.warnings.add("Legacy Word drawings and embedded objects are not analyzed. Export them as PDF or images.");
                    }
                } else workbook(bytes, out);
            }
            default -> {
                String text;
                try { text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString(); }
                catch (CharacterCodingException ex) { throw DocumentException.invalid("Text/CSV files must use UTF-8 encoding."); }
                if (text.indexOf('\0') >= 0) throw DocumentException.invalid("Binary content does not match this text file type.");
                if (ext.equals("csv") || ext.equals("tsv")) csv(text, ext, out);
                else out.add("Text lines", "TEXT", text, List.of());
            }
        }
        if (out.evidence.isEmpty() || out.evidence.stream().allMatch(e -> e.text().isBlank()))
            throw DocumentException.invalid("No readable content was found. Try a clearer PDF or image.");
        return new Extraction(List.copyOf(out.evidence), List.copyOf(out.warnings));
    }
    private void pdf(String filename, byte[] bytes, Collector out, int[] budget, Consumer<String> progress) throws IOException {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            if (pdf.isEncrypted() || !pdf.getCurrentAccessPermission().canExtractContent())
                throw DocumentException.invalid("Encrypted or extraction-restricted PDFs are not supported.");
            if (pdf.getNumberOfPages() > MAX_PAGES) throw DocumentException.invalid("PDFs may contain at most 40 pages. Split this document.");
            var renderer = new PDFRenderer(pdf); renderer.setSubsamplingAllowed(true);
            for (int p = 0; p < pdf.getNumberOfPages(); p++) {
                progress.accept("Extracting page " + (p + 1) + "/" + pdf.getNumberOfPages());
                var stripper = new LayoutStripper(); stripper.setSortByPosition(true);
                stripper.setStartPage(p + 1); stripper.setEndPage(p + 1);
                var writer = new BoundedWriter(); stripper.writeText(pdf, writer); String text = writer.toString();
                String location = "Page " + (p + 1);
                if (!text.isBlank()) {
                    // PDF whitespace is layout evidence, not a guarantee of semantic table columns.
                    out.add(location, "PDF_TEXT", text, List.of());
                    List<String> header = List.of(); int lineNumber = 0;
                    for (String line : text.split("\\R")) {
                        lineNumber++;
                        var cells = Arrays.asList(line.strip().split("\\t+"));
                        if (cells.size() > 1 && quantityColumn(cells) >= 0) header = cells;
                        else if (!header.isEmpty() && cells.size() == header.size())
                            out.row(location + ", table line " + lineNumber, header, cells);
                    }
                }
                var page = pdf.getPage(p);
                var probe = new PdfGraphicsProbe(page); probe.processPage(page);
                long letters = text.codePoints().filter(Character::isLetterOrDigit).count();
                boolean drawing = (filename + " " + text).toLowerCase(Locale.ROOT)
                        .matches("(?s).*(skeem|diagram|plaan|floor.plan|kilbi|layout|lightning|piksekaitse).*" ) && probe.paths > 8;
                boolean readable = letters >= 40 && text.chars().filter(c -> c == 0xfffd).count() < Math.max(1, text.length() / 100);
                if ((!readable && (letters > 0 || probe.paths > 0 || probe.largeImage)) || probe.largeImage || drawing || (probe.paths > 80 && letters < 1500)) {
                    int pageIndex = p;
                    float scale = 200f / 72f;
                    var box = page.getCropBox();
                    boolean rotate = page.getRotation() % 180 != 0;
                    int width = (int)Math.ceil((rotate ? box.getHeight() : box.getWidth()) * scale);
                    int height = (int)Math.ceil((rotate ? box.getWidth() : box.getHeight()) * scale);
                    dimensions(width, height, 160_000_000L);
                    out.addVision(vision.extract(filename, location, width, height, (x, y, w, h) -> {
                        var image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                        var graphics = image.createGraphics();
                        try {
                            // PDFRenderer clears the page using the Graphics2D background, independently of fill color.
                            graphics.setBackground(Color.WHITE);
                            graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, w, h);
                            graphics.translate(-x, -y); renderer.renderPageToGraphics(pageIndex, graphics, scale, scale);
                        } finally { graphics.dispose(); }
                        return image;
                    }, budget, progress));
                }
            }
            if (out.evidence.stream().anyMatch(e -> e.method().equals("PDF_TEXT")))
                out.warnings.add("PDF text preserves reading order and spacing; verify ambiguous table columns against the original page.");
        }
    }
    private void raster(String filename, String ext, byte[] bytes, Collector out, int[] budget, Consumer<String> progress) throws IOException {
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw DocumentException.invalid("The image is unreadable or its type is unsupported.");
            var reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!(format.equals(ext) || (format.equals("jpeg") && ext.equals("jpg")))) throw DocumentException.invalid("Image content does not match its extension.");
                int width = reader.getWidth(0), height = reader.getHeight(0);
                dimensions(width, height, 40_000_000L);
                var image = reader.read(0);
                try {
                    out.addVision(vision.extract(filename, "Image 1", width, height, (x,y,w,h) -> {
                        var tile = new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB); var g = tile.createGraphics();
                        try { g.setColor(Color.WHITE); g.fillRect(0,0,w,h); g.drawImage(image,-x,-y,null); } finally { g.dispose(); }
                        return tile;
                    }, budget, progress));
                } finally { image.flush(); }
            } finally { reader.dispose(); }
        }
    }
    private void word(byte[] bytes, Collector out) throws IOException {
        try (var doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            int paragraph = 0, table = 0;
            for (var element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph p) out.add("Paragraph " + (++paragraph), "TEXT", p.getText(), List.of());
                else if (element instanceof XWPFTable t) {
                    String name = "Table " + (++table); List<String> header = List.of(); int row = 0;
                    for (var r : t.getRows()) {
                        var values = r.getTableCells().stream().map(XWPFTableCell::getText).toList();
                        if (row++ == 0) header = values;
                        out.row(name + ", row " + row, header, values);
                    }
                }
            }
            for (var h : doc.getHeaderList()) out.add("Header", "TEXT", h.getText(), List.of());
            for (var f : doc.getFooterList()) out.add("Footer", "TEXT", f.getText(), List.of());
            if (!doc.getAllPictures().isEmpty()) out.warnings.add("Word contains embedded images. Export drawings as PDF or upload images separately for visual analysis.");
            out.warnings.add("Word locations use paragraph/table numbers; page numbering requires the original Word layout.");
        }
    }
    private void workbook(byte[] bytes, Collector out) throws IOException {
        try (var book = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            if (book.getNumberOfSheets() > 30) throw DocumentException.invalid("Spreadsheets may contain at most 30 sheets.");
            var formatter = new DataFormatter(Locale.ROOT); formatter.setUseCachedValuesForFormulaCells(true);
            int count = 0;
            for (var sheet : book) {
                List<String> header = List.of();
                for (var row : sheet) {
                    if (++count > 10000 || row.getLastCellNum() > 100) throw DocumentException.invalid("Spreadsheet is too large (10,000 rows / 100 columns maximum).");
                    var cells = new ArrayList<String>();
                    for (int c = 0; c < row.getLastCellNum(); c++) cells.add(formatter.formatCellValue(row.getCell(c)));
                    if (header.isEmpty() || quantityColumn(cells) >= 0) header = cells;
                    out.row("Sheet " + sheet.getSheetName() + ", row " + (row.getRowNum() + 1), header, cells);
                }
            }
            out.warnings.add("Spreadsheet formulas use stored cached values only; formulas, macros and external links are never executed. Hidden rows/sheets are included; verify revisions and subtotals.");
        }
    }
    private void csv(String text, String ext, Collector out) throws IOException {
        String first = text.lines().findFirst().orElse("");
        char delimiter = ext.equals("tsv") ? '\t' : first.chars().filter(c -> c == ';').count() > first.chars().filter(c -> c == ',').count() ? ';' : ',';
        try (var parser = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setIgnoreEmptyLines(true).get().parse(new StringReader(text.replaceFirst("^\\ufeff", "")))) {
            List<String> header = List.of();
            for (var row : parser) {
                if (row.getRecordNumber() > 10000 || row.size() > 100) throw DocumentException.invalid("CSV is too large (10,000 rows / 100 columns maximum).");
                if (header.isEmpty()) header = row.toList();
                out.row("Record " + row.getRecordNumber(), header, row.toList());
            }
        }
    }
    private static int column(List<String> header, String pattern) {
        for (int i = 0; i < header.size(); i++) if (header.get(i).strip().toLowerCase(Locale.ROOT).matches(pattern)) return i;
        return -1;
    }
    private static int quantityColumn(List<String> header) { return column(header, "kogus|quantity|qty|arv|hulk"); }
    private static String at(List<String> values, int i) { return i >= 0 && i < values.size() ? values.get(i).strip() : ""; }
    private static BigDecimal decimal(String value) {
        if (!value.matches("\\d{1,6}(?:[.,]\\d{1,4})?")) return null;
        return new BigDecimal(value.replace(',', '.'));
    }
    private static void dimensions(int width, int height, long maximum) {
        if (width < 1 || height < 1 || width > 30000 || height > 30000 || (long)width * height > maximum)
            throw DocumentException.invalid("Image/page dimensions exceed safe processing limits. Split or resize this document.");
    }
    private static void magic(byte[] bytes, byte[] expected) {
        if (bytes.length < expected.length || !Arrays.equals(Arrays.copyOf(bytes, expected.length), expected))
            throw DocumentException.invalid("File content does not match its extension.");
    }
    private static void validateZip(byte[] bytes) throws IOException {
        magic(bytes, new byte[]{'P','K',3,4});
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            long total = 0; int count = 0; byte[] buffer = new byte[8192];
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++count > 2000 || entry.getName().toLowerCase(Locale.ROOT).contains("vbaproject")) throw DocumentException.invalid("Macros or excessive archive entries are not supported.");
                int n; while ((n = zip.read(buffer)) != -1) {
                    total += n;
                    if (total > 64 * 1024 * 1024 || total > Math.max(1024 * 1024, bytes.length * 100L))
                        throw DocumentException.invalid("Compressed document expands beyond safe limits.");
                }
            }
        }
    }
    private static class BoundedWriter extends Writer {
        final StringBuilder text = new StringBuilder();
        public void write(char[] c, int offset, int length) {
            if (text.length() + length > MAX_CHARS) throw DocumentException.invalid("PDF page text exceeds the extraction limit.");
            text.append(c,offset,length);
        }
        public void flush() {} public void close() {} public String toString() { return text.toString(); }
    }
    private static class LayoutStripper extends PDFTextStripper {
        int operators;
        float previousEnd=-1,previousY=-1;
        LayoutStripper() throws IOException {}
        @Override protected void processOperator(org.apache.pdfbox.contentstream.operator.Operator operator,
                List<org.apache.pdfbox.cos.COSBase> operands) throws IOException {
            if (++operators > 150000 || Thread.currentThread().isInterrupted()) throw DocumentException.invalid("PDF text stream is too complex. Split or simplify the page.");
            super.processOperator(operator,operands);
        }
        @Override protected void writeString(String text,List<org.apache.pdfbox.text.TextPosition> positions) throws IOException {
            var line = new StringBuilder(); org.apache.pdfbox.text.TextPosition previous = null;
            if(!positions.isEmpty()) {
                var first=positions.getFirst();
                if(Math.abs(first.getYDirAdj()-previousY)<2 && first.getXDirAdj()-previousEnd>Math.max(4,first.getWidthOfSpace()*1.8)) line.append('\t');
                var last=positions.getLast(); previousEnd=last.getXDirAdj()+last.getWidthDirAdj();previousY=last.getYDirAdj();
            }
            for (var position : positions) {
                if (previous != null && position.getXDirAdj() - (previous.getXDirAdj()+previous.getWidthDirAdj()) > Math.max(4,previous.getWidthOfSpace()*1.8)) line.append('\t');
                line.append(position.getUnicode()); previous = position;
            }
            super.writeString(line.toString());
        }
    }
    private static class Collector {
        final String filename; final List<Evidence> evidence = new ArrayList<>(); final Set<String> warnings = new LinkedHashSet<>(); int chars;
        Collector(String filename) { this.filename = filename; }
        void add(String location, String method, String text, List<Item> items) {
            if (text.isBlank()) return;
            if ((chars += text.length()) > MAX_CHARS) throw DocumentException.invalid("Extracted content exceeds 400,000 characters. Split this document.");
            int offset = 0, part = 0;
            while (offset < text.length()) {
                int end = Math.min(text.length(), offset + 2200);
                if (end < text.length()) { int line = text.lastIndexOf('\n', end); if (line > offset) end = line + 1; }
                if (evidence.size() >= MAX_EXCERPTS) throw DocumentException.invalid("Too many extracted sections. Split this document.");
                evidence.add(new Evidence("D-" + UUID.randomUUID(), filename, location + ", part " + (++part), method, text.substring(offset,end), offset == 0 ? items : List.of()));
                offset = end;
            }
        }
        void row(String location, List<String> header, List<String> values) {
            if (values.stream().allMatch(String::isBlank)) return;
            var parts = new ArrayList<String>();
            for (int i = 0; i < values.size(); i++) parts.add((i < header.size() ? header.get(i) : "Column " + (i + 1)) + ": " + values.get(i));
            BigDecimal quantity = decimal(at(values, quantityColumn(header)));
            var items = quantityColumn(header) < 0 || values.equals(header) ? List.<Item>of() : List.of(new Item(at(values,column(header,"ref|reference|tähis|tahis|pos|pos\\.")),
                    String.join(" | ", values), quantity, at(values,column(header,"unit|ühik|yhik")), quantity == null ? "UNKNOWN" : "LISTED",
                    decimal(at(values,column(header,"modules each|din modules each|mooduleid seadme kohta"))),
                    at(values,column(header,"part number|tootekood|artikli kood|article"))));
            add(location, "TABLE", String.join(" | ", parts), items);
        }
        void addVision(List<Evidence> values) {
            warnings.add("Visual extraction is unverified: symbol counts are estimates, printed quantities are OCR readings. Verify against originals. Overlapping crops use center ownership; never add diagram quantities to specification quantities for the same equipment.");
            for (var value : values) {
                if(value.items().isEmpty()) { add(value.location(),"VISION",value.text(),List.of()); continue; }
                // Smaller groups keep symbol evidence retrievable even on dense pages.
                var json=tools.jackson.databind.json.JsonMapper.builder().build();
                String notes=value.text().substring(0,value.text().lastIndexOf('\n'));
                for(int start=0;start<value.items().size();start+=4) {
                    var group=value.items().subList(start,Math.min(start+4,value.items().size()));
                    add(value.location()+", items "+(start+1)+"–"+(start+group.size()),"VISION",notes+"\n"+json.writeValueAsString(group),List.copyOf(group));
                }
            }
        }
    }
}
