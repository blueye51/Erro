package ink.erro.backend.document;

import java.io.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.*;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import ink.erro.backend.ai.*;
import ink.erro.backend.chat.CitationMapper;
import ink.erro.backend.electrical.*;
import ink.erro.backend.product.ProductService;
import static ink.erro.backend.document.DocumentModels.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class DocumentTest {
    private final AiService ai=mock(AiService.class);
    private final DocumentProperties properties=new DocumentProperties(true,"deepseek-flash",Duration.ofMinutes(30),8);
    private final VisionExtractor vision=new VisionExtractor(ai,properties);
    private final DocumentExtractor extractor=new DocumentExtractor(vision);
    private static final String VISION="""
      {"items":[{"reference":"Panel A/Q1","description":"C16 breaker, terminal 1 to X1, uncertain line continuation","quantity":1,"unit":"tk","basis":"SYMBOL_COUNT","modulesEach":null,"partNumber":"","x":500,"y":500}],"notes":"Check original drawing"}
      """;
    @BeforeEach void configure() { when(ai.analyzeImage(anyString(),anyString(),anyString(),any())).thenReturn(VISION); }

    @Test void readableSpecificationPdfUsesTextWithoutAi() throws Exception {
        var result=extractor.extract("Materjalide-loetelu.pdf",pdf(true,false,false),s->{});
        assertThat(result.evidence()).anySatisfy(e->{ assertThat(e.filename()).isEqualTo("Materjalide-loetelu.pdf"); assertThat(e.location()).contains("Page 1"); assertThat(e.text()).contains("C16"); });
        verifyNoInteractions(ai);
    }
    @Test void readablePdfTablePreservesColumnQuantities() throws Exception {
        try(var doc=new PDDocument();var bytes=new ByteArrayOutputStream()) {
            var page=new PDPage(new PDRectangle(400,300));doc.addPage(page);
            try(var stream=new PDPageContentStream(doc,page)) {
                var rows=List.of(List.of("Description","Quantity","Unit"),List.of("C16 breaker","3","pcs"),List.of("B16 breaker","2","pcs"));
                for(int row=0;row<rows.size();row++) for(int col=0;col<3;col++) {
                    stream.beginText(); stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),10);
                    stream.newLineAtOffset(20+col*120,250-row*20);stream.showText(rows.get(row).get(col));stream.endText();
                }
            }
            doc.save(bytes);var result=extractor.extract("materials.pdf",bytes.toByteArray(),s->{});
            assertThat(result.evidence().stream().flatMap(e->e.items().stream()).toList()).hasSize(2);
            assertThat(result.evidence().stream().flatMap(e->e.items().stream())).anyMatch(i->i.quantity().intValue()==3);
            verifyNoInteractions(ai);
        }
    }
    @Test void isolatedWorkerParsesWithoutCredentialsAndCleansTemporaryFiles() throws Exception {
        var processor=new IsolatedDocumentProcessor(new AiProperties(java.net.URI.create("http://127.0.0.1:1/responses"),"","deepseek-flash","none",2048,Duration.ofSeconds(1)),properties);
        var result=processor.extract("spec.pdf",pdf(true,false,false),s->{});
        assertThat(result.evidence()).anyMatch(e->e.text().contains("C16"));
        assertThatThrownBy(()->processor.extract("bad.pdf","invalid".getBytes(),s->{})).hasMessageContaining("does not match");
        processor.close();
    }
    @Test void vectorCadWithoutSelectableTextAndScansUseVision() throws Exception {
        for(byte[] bytes:List.of(pdf(false,true,false),pdf(false,false,true))) {
            var result=extractor.extract("kilbiskeem.pdf",bytes,s->{});
            assertThat(result.evidence()).anyMatch(e->e.method().equals("VISION"));
            assertThat(result.warnings()).anyMatch(w->w.contains("unverified"));
            assertThat(result.warnings()).noneMatch(w->w.startsWith("PDF text"));
            assertThat(result.evidence().stream().flatMap(e->e.items().stream())).allMatch(i->i.basis().equals("VISION_SYMBOL_COUNT"));
        }
        verify(ai,atLeast(2)).analyzeImage(eq("deepseek-flash"),contains("untrusted DATA"),anyString(),any());
    }
    @Test void pdfVisionCropsPreserveWhiteBackgroundAndMatchRotatedCroppedPages() throws Exception {
        var references = new ArrayList<BufferedImage>();
        try (var pdf = new PDDocument(); var bytes = new ByteArrayOutputStream()) {
            for (int rotation : List.of(0, 90, 180, 270)) {
                var page = new PDPage(new PDRectangle(640, 720));
                page.setCropBox(new PDRectangle(17, 23, 540, 600));
                page.setRotation(rotation);
                pdf.addPage(page);
                try (var stream = new PDPageContentStream(pdf, page)) {
                    // Black vector symbols across every crop, without an explicit PDF background.
                    for (int y = 55; y < 620; y += 70) for (int x = 45; x < 550; x += 80) {
                        stream.addRect(x, y, 15, 22); stream.stroke();
                    }
                    stream.moveTo(80, 120); stream.lineTo(400, 500); stream.stroke();
                }
            }
            pdf.save(bytes);
            var renderer = new org.apache.pdfbox.rendering.PDFRenderer(pdf);
            for (int page = 0; page < pdf.getNumberOfPages(); page++)
                references.add(renderer.renderImageWithDPI(page, 200, org.apache.pdfbox.rendering.ImageType.RGB));
            var json = tools.jackson.databind.json.JsonMapper.builder().build();
            var inspected = new ArrayList<String>();
            when(ai.analyzeImage(anyString(), anyString(), anyString(), any())).thenAnswer(call -> {
                String input = call.getArgument(2);
                var prompt = json.readTree(input.substring(input.indexOf('{')));
                String location = prompt.path("location").asText();
                var reference = references.get(Integer.parseInt(location.substring("Page ".length())) - 1);
                assertThat(prompt.has("crop")).isFalse();
                int cropIndex = inspected.size() % 4;
                int offsetX = (cropIndex % 2) * 1240, offsetY = (cropIndex / 2) * 1240;
                var image = ImageIO.read(new ByteArrayInputStream((byte[]) call.getArgument(3)));
                try {
                    assertThat(image.getWidth()).isEqualTo(prompt.path("imageWidth").asInt());
                    assertThat(image.getHeight()).isEqualTo(prompt.path("imageHeight").asInt());
                    int white = 0, ink = 0;
                    for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
                        int expected = offsetX + x < reference.getWidth() && offsetY + y < reference.getHeight()
                                ? reference.getRGB(offsetX + x, offsetY + y) : Color.WHITE.getRGB();
                        int actual = image.getRGB(x, y);
                        if (actual == Color.WHITE.getRGB()) white++; else ink++;
                        if (actual != expected) throw new AssertionError(location + " crop " + offsetX + "," + offsetY
                                + " differs from the reference at " + x + "," + y);
                    }
                    assertThat(white).isGreaterThan(image.getWidth() * image.getHeight() / 2);
                    assertThat(ink).isPositive();
                    inspected.add(location + ":" + offsetX + "," + offsetY);
                } finally { image.flush(); }
                return VISION;
            });
            extractor.extract("vector-panel.pdf", bytes.toByteArray(), status -> {});
            assertThat(inspected).hasSize(16).doesNotHaveDuplicates();
        } finally { references.forEach(BufferedImage::flush); }
    }
    @Test void mixedPdfPreservesTextAndVisualEvidence() throws Exception {
        var result=extractor.extract("mixed.pdf",pdf(true,false,true),s->{});
        assertThat(result.evidence()).anyMatch(e->e.method().equals("PDF_TEXT")).anyMatch(e->e.method().equals("VISION"));
        assertThat(result.warnings()).anyMatch(w->w.startsWith("PDF text"));
    }
    @Test void imageDecodingAndCropOwnershipPreventOverlapCounts() throws Exception {
        when(ai.analyzeImage(anyString(),anyString(),anyString(),any())).thenReturn(
            VISION.replace("\"x\":500","\"x\":929"), VISION.replace("\"x\":500","\"x\":167"));
        var result=extractor.extract("floor-plan.png",png(2500,300),s->{});
        assertThat(result.evidence().stream().flatMap(e->e.items().stream())).hasSize(1);
        verify(ai,times(2)).analyzeImage(anyString(),anyString(),anyString(),any());
        assertThat(result.evidence().getLast().location()).contains("x=1400");
    }
    @Test void malformedVisionFailsInsteadOfPublishingUnlocatedCounts() {
        when(ai.analyzeImage(anyString(),anyString(),anyString(),any())).thenReturn("{\"items\":[{\"description\":\"C16\",\"quantity\":99}]}");
        assertThatThrownBy(()->extractor.extract("scan.png",png(100,100),s->{}))
                .hasMessageContaining("after one automatic correction").hasMessageContaining("Image 1, crop 1/1");
        verify(ai,times(2)).analyzeImage(anyString(),anyString(),anyString(),any());
    }
    @Test void outOfRangeVisionCoordinatesAreCorrectedWithoutClampingOrChangingTheImage() throws Exception {
        when(ai.analyzeImage(anyString(),anyString(),anyString(),any()))
                .thenReturn(VISION.replace("\"x\":500", "\"x\":1500"), VISION);
        var progress = new ArrayList<String>();
        var result = extractor.extract("scan.png", png(100,100), progress::add);
        assertThat(result.evidence().stream().flatMap(e -> e.items().stream())).hasSize(1);
        assertThat(progress).anyMatch(s -> s.contains("Correcting vision evidence"));
        var prompts = org.mockito.ArgumentCaptor.forClass(String.class);
        var images = org.mockito.ArgumentCaptor.forClass(byte[].class);
        verify(ai,times(2)).analyzeImage(anyString(),anyString(),prompts.capture(),images.capture());
        assertThat(prompts.getAllValues().getLast()).contains("outside the declared image coordinate range", "0..1000", "full corrected JSON");
        assertThat(images.getAllValues().getFirst()).isEqualTo(images.getAllValues().getLast());
    }
    @Test void explicitlyDeclaredPixelCoordinatesKeepCropOwnershipWithNumericStrings() throws Exception {
        String first = VISION.replace("{\"items\"", "{\"coordinateSpace\":\"PIXELS\",\"items\"")
                .replace("\"x\":500", "\"x\":\"1450\"").replace("\"y\":500", "\"y\":\"150\"");
        when(ai.analyzeImage(anyString(),anyString(),anyString(),any()))
                .thenReturn(first, first.replace("\"1450\"", "\"210\""));
        var result = extractor.extract("panel.png", png(2500,300), status -> {});
        assertThat(result.evidence().stream().flatMap(e -> e.items().stream())).hasSize(1);
        assertThat(result.evidence().getLast().location()).contains("x=1400");
        verify(ai,times(2)).analyzeImage(anyString(),anyString(),anyString(),any());
    }
    @Test void unusableCoordinateSpacesAndPersistentOutliersStopAfterOneCorrection() throws Exception {
        for (String response : List.of(
                VISION.replace("\"x\":500", "\"x\":1500"),
                VISION.replace("\"x\":500", "\"x\":-1"),
                VISION.replace("\"x\":500", "\"x\":\"NaN\""),
                VISION.replace("{\"items\"", "{\"coordinateSpace\":\"PAGE\",\"items\""),
                VISION.replace("{\"items\"", "{\"coordinateSpace\":\"PIXELS\",\"items\""))) {
            reset(ai);
            when(ai.analyzeImage(anyString(),anyString(),anyString(),any())).thenReturn(response);
            assertThatThrownBy(() -> extractor.extract("scan.png",png(100,100), status -> {}))
                    .hasMessageContaining("after one automatic correction");
            verify(ai,times(2)).analyzeImage(anyString(),anyString(),anyString(),any());
        }
    }
    @Test void malformedJsonReceivesOneCorrectionButProviderFailuresAreNotRetried() throws Exception {
        when(ai.analyzeImage(anyString(),anyString(),anyString(),any())).thenReturn("not JSON", VISION);
        assertThat(extractor.extract("scan.png",png(100,100), status -> {}).evidence()).isNotEmpty();
        verify(ai,times(2)).analyzeImage(anyString(),anyString(),anyString(),any());
        reset(ai);
        var failure = new AiException(org.springframework.http.HttpStatus.BAD_GATEWAY,"Provider unavailable");
        when(ai.analyzeImage(anyString(),anyString(),anyString(),any())).thenThrow(failure);
        assertThatThrownBy(() -> extractor.extract("scan.png",png(100,100), status -> {})).isSameAs(failure);
        verify(ai,times(1)).analyzeImage(anyString(),anyString(),anyString(),any());
    }
    @Test void csvWordAndExcelPreserveStructuredQuantitiesAndUnits() throws Exception {
        var csv=extractor.extract("kaablid.csv",("Nimetus;Kogus;Ühik;modules each\nC16 kaitselüliti;3;tk;1\nCable 3x2,5;12,5;m;\n").getBytes(StandardCharsets.UTF_8),s->{});
        assertThat(csv.evidence().stream().flatMap(e->e.items().stream()).toList()).hasSize(2);
        assertThat(csv.evidence().get(2).items().getFirst().quantity()).isEqualByComparingTo("12.5");
        try(var book=new XSSFWorkbook(); var bytes=new ByteArrayOutputStream()) {
            var sheet=book.createSheet("Kilp A"); var header=sheet.createRow(0); header.createCell(0).setCellValue("Nimetus"); header.createCell(1).setCellValue("Kogus"); header.createCell(2).setCellValue("Ühik");
            var row=sheet.createRow(1); row.createCell(0).setCellValue("B16"); row.createCell(1).setCellValue(4); row.createCell(2).setCellValue("tk"); book.write(bytes);
            var result=extractor.extract("schedule.xlsx",bytes.toByteArray(),s->{});
            assertThat(result.evidence().getLast().location()).contains("Sheet Kilp A, row 2");
            assertThat(result.evidence().getLast().items().getFirst().quantity()).isEqualByComparingTo("4");
        }
        try(var doc=new XWPFDocument(); var bytes=new ByteArrayOutputStream()) {
            var table=doc.createTable(2,3);
            for(int i=0;i<3;i++) { table.getRow(0).getCell(i).setText(List.of("Description","Quantity","Unit").get(i)); table.getRow(1).getCell(i).setText(List.of("C25 breaker","2","pcs").get(i)); }
            doc.write(bytes); var result=extractor.extract("schedule.docx",bytes.toByteArray(),s->{});
            assertThat(result.evidence().getLast().items().getFirst().quantity()).isEqualByComparingTo("2");
            assertThat(result.evidence().getLast().location()).contains("Table 1, row 2");
        }
        verifyNoInteractions(ai);
    }
    @Test void rejectsWrongMagicMalformedFilesSizeAndArchiveBombs() throws Exception {
        assertThatThrownBy(()->extractor.extract("x.pdf","not PDF".getBytes(),s->{})).hasMessageContaining("does not match");
        assertThatThrownBy(()->extractor.extract("x.png","not image".getBytes(),s->{})).hasMessageContaining("unreadable");
        assertThatThrownBy(()->DocumentExtractor.filename("attack.html")).hasMessageContaining("Supported files");
        assertThat(DocumentExtractor.filename("../../safe.csv")).isEqualTo("safe.csv");
        assertThatThrownBy(()->extractor.extract("x.txt",new byte[(int)DocumentExtractor.MAX_FILE_BYTES+1],s->{})).hasMessageContaining("20 MiB");
        var bytes=new ByteArrayOutputStream();
        try(var zip=new java.util.zip.ZipOutputStream(bytes)) { zip.putNextEntry(new java.util.zip.ZipEntry("word/document.xml")); zip.write(new byte[2*1024*1024]); zip.closeEntry(); }
        assertThatThrownBy(()->extractor.extract("bomb.docx",bytes.toByteArray(),s->{})).hasMessageContaining("expands");
        verifyNoInteractions(ai);
    }
    @Test void sessionsAreIsolatedDeduplicatedRemovableAndExpire() throws Exception {
        class MutableClock extends Clock { Instant now=Instant.now(); public java.time.ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(java.time.ZoneId zone){return this;} public Instant instant(){return now;} }
        var clock=new MutableClock();
        try(var store=new CloseableStore(new DocumentStore(extractor,properties,clock))) {
            var first=store.value.create(); var second=store.value.create();
            var file=new MockMultipartFile("file","test.csv","text/csv","Description,Quantity,Unit\nC16 breaker,3,pcs".getBytes());
            var uploaded=store.value.upload(first.token(),file); awaitReady(store.value,first.token());
            assertThat(store.value.upload(first.token(),file).id()).isEqualTo(uploaded.id());
            assertThatThrownBy(()->store.value.read(second.token(),List.of(uploaded.id()))).hasMessageContaining("unavailable in this problem");
            assertThat(store.value.read(first.token(),List.of(uploaded.id()))).hasSize(1);
            store.value.remove(first.token(),uploaded.id()); assertThat(store.value.list(first.token())).isEmpty();
            clock.now=clock.now.plusSeconds(1801); store.value.expire();
            assertThatThrownBy(()->store.value.list(second.token())).hasMessageContaining("expired");
        }
    }
    @Test void totalsArePerFileAndDuplicateReferencesWithholdTotals() throws Exception {
        try(var scoped=new CloseableStore(new DocumentStore(extractor,properties))) {
            var store=scoped.value; var token=store.create().token();
            var a=store.upload(token,new MockMultipartFile("file","panel.csv","text/csv","Ref,Description,Quantity,Unit,modules each\nQ1,C16 breaker,3,pcs,1\nQ2,B16 breaker,2,pcs,2".getBytes()));
            var b=store.upload(token,new MockMultipartFile("file","revision.csv","text/csv","Ref,Description,Quantity,Unit\nQ1,C16 breaker,3,pcs\nQ1,C16 breaker,4,pcs".getBytes()));
            awaitReady(store,token);
            var arithmetic=new ElectricalCalculationService(); var products=mock(ProductService.class);
            var assessment=new DocumentAssessmentService(new ElectricalParameterExtractor(),new ElectricalIntentService(),arithmetic,new ElectricalValidationService(),products);
            var context=new DocumentContextService(store,arithmetic,assessment).retrieve(token,List.of(a.id(),b.id()),"How many DIN modules and C16 breakers?");
            assertThat(context.calculations()).anySatisfy(c->{assertThat(c.name()).contains("DIN");assertThat(c.value()).isEqualByComparingTo("7");});
            assertThat(context.calculations()).noneMatch(c->c.name().startsWith("revision.csv"));
            assertThat(context.warnings()).anyMatch(w->w.contains("repeated references"));
            assertThat(context.assessments()).isNotEmpty();
        }
    }
    @Test void documentCitationsOnlyAcceptRetrievedIds() {
        var evidence=new Evidence("D-"+UUID.randomUUID(),"panel.pdf","Page 4","PDF_TEXT","C16",List.of());
        var result=new CitationMapper().documents("C16 ["+evidence.id()+"] [D-invented]",List.of(evidence));
        assertThat(result.sources()).hasSize(1); assertThat(result.reply()).contains("[Document 1]").doesNotContain("invented");
        assertThat(result.warnings()).isNotEmpty();
    }
    @Test void missingOrUnreadableTableQuantitiesWithholdTotals() throws Exception {
        for (String quantity : List.of("", "unknown", "2-3")) {
            try (var scoped = new CloseableStore(new DocumentStore(extractor, properties))) {
                var store = scoped.value;
                String token = store.create().token();
                String csv = "Ref,Description,Quantity,Unit,modules each\nQ1,C16 breaker,3,pcs,1\nQ2,B16 breaker," + quantity + ",pcs,2\n";
                var file = store.upload(token, new MockMultipartFile("file", "incomplete.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)));
                awaitReady(store, token);
                var evidence = store.read(token, List.of(file.id())).getFirst().evidence();
                assertThat(evidence.stream().flatMap(e -> e.items().stream())).hasSize(2)
                        .anySatisfy(item -> { assertThat(item.basis()).isEqualTo("UNKNOWN"); assertThat(item.quantity()).isNull(); });
                var assessment = mock(DocumentAssessmentService.class);
                when(assessment.assess(anyList())).thenReturn(List.of());
                var result = new DocumentContextService(store, new ElectricalCalculationService(), assessment)
                        .retrieve(token, List.of(file.id()), "C16 quantities and DIN modules?");
                assertThat(result.calculations()).isEmpty();
                assertThat(result.warnings()).anyMatch(w -> w.contains("missing or unreadable table quantities"));
            }
        }
    }
    @Test void largeDocumentRetrievalIsBoundedAndPrioritizesLatestQuestion() {
        var store=mock(DocumentStore.class);var assessment=mock(DocumentAssessmentService.class);
        when(assessment.assess(anyList())).thenReturn(List.of());
        var evidence=new ArrayList<Evidence>();
        for(int i=0;i<100;i++) evidence.add(new Evidence("D-"+UUID.randomUUID(),"large.txt","Section "+i,"TEXT","Older cable information "+"x".repeat(2000),List.of()));
        var target=new Evidence("D-"+UUID.randomUUID(),"large.txt","Section 101","TEXT","B16 breaker information",List.of());evidence.add(target);
        when(store.read("token",List.of("id"))).thenReturn(List.of(new Extraction(evidence,List.of())));
        var result=new DocumentContextService(store,new ElectricalCalculationService(),assessment).retrieve("token",List.of("id"),"Older cable information\nFollow-up: B16");
        assertThat(result.excerpts().getFirst().id()).isEqualTo(target.id());
        assertThat(result.excerpts()).hasSizeLessThanOrEqualTo(24);
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        assertThat(result.excerpts().stream().mapToInt(e->json.writeValueAsString(e).length()).sum()).isLessThanOrEqualTo(24000);
        assertThat(result.complete()).isFalse();assertThat(result.warnings()).anyMatch(w->w.contains("partial retrieval"));
    }
    @Test void exactCatalogPartSuppliesDinWidthAndReusesValidation() throws Exception {
        try(var scoped=new CloseableStore(new DocumentStore(extractor,properties))) {
            var store=scoped.value;var token=store.create().token();
            var file=store.upload(token,new MockMultipartFile("file","schedule.csv","text/csv","Ref,Description,Quantity,Unit,Part number\nQ1,C16 breaker,3,pcs,PART-123".getBytes()));
            awaitReady(store,token);
            var products=mock(ProductService.class);
            var candidate=new ink.erro.backend.product.ProductModels.Candidate(UUID.randomUUID(),Map.of("part_number","PART-123","additional_attributes","{\"dinModules\":\"2\"}","source_document_id",UUID.randomUUID()),List.of(),List.of(),List.of(),List.of());
            when(products.retrieve(anyString(),anySet(),argThat(p->p!=null && p.attributes().getOrDefault("partNumber","").equals("PART-123")))).thenReturn(List.of(candidate));
            var arithmetic=new ElectricalCalculationService();
            var assessment=new DocumentAssessmentService(new ElectricalParameterExtractor(),new ElectricalIntentService(),arithmetic,new ElectricalValidationService(),products);
            var result=new DocumentContextService(store,arithmetic,assessment).retrieve(token,List.of(file.id()),"DIN modules?");
            assertThat(result.calculations()).anySatisfy(c->{assertThat(c.name()).contains("DIN");assertThat(c.value()).isEqualByComparingTo("6");});
            assertThat(result.assessments()).anyMatch(a->a.products().contains(candidate));
            var other=new ink.erro.backend.product.ProductModels.Candidate(UUID.randomUUID(),candidate.specifications(),List.of(),List.of(),List.of(),List.of());
            when(products.retrieve(anyString(),anySet(),any())).thenReturn(List.of(candidate,other));
            var ambiguous=new DocumentContextService(store,arithmetic,assessment).retrieve(token,List.of(file.id()),"DIN modules?");
            assertThat(ambiguous.calculations()).noneMatch(c->c.name().contains("DIN"));
            assertThat(ambiguous.warnings()).anyMatch(w->w.contains("DIN total requires explicit modules"));
        }
    }
    static void awaitReady(DocumentStore store,String token) throws Exception {
        long end=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        while(System.nanoTime()<end) { if(store.list(token).stream().noneMatch(s->s.status().equals("QUEUED")||s.status().equals("PROCESSING"))) return; Thread.sleep(20); }
        throw new AssertionError("Document processing did not finish");
    }
    record CloseableStore(DocumentStore value) implements AutoCloseable { public void close(){value.close();} }
    static byte[] png(int width,int height) throws Exception {
        var image=new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB);var graphics=image.createGraphics();
        graphics.setColor(Color.WHITE);graphics.fillRect(0,0,width,height);graphics.setColor(Color.BLACK);graphics.drawRect(10,10,60,40);graphics.drawString("C16 Q1",12,35);graphics.dispose();
        var bytes=new ByteArrayOutputStream(); ImageIO.write(image,"png",bytes);image.flush();return bytes.toByteArray();
    }
    static byte[] pdf(boolean text,boolean vector,boolean scan) throws Exception {
        try(var doc=new PDDocument();var bytes=new ByteArrayOutputStream()) {
            var page=new PDPage(new PDRectangle(300,300));doc.addPage(page);
            try(var content=new PDPageContentStream(doc,page)) {
                if(text) {
                    content.beginText();content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),10);content.newLineAtOffset(20,270);
                    content.showText("Materjalide loetelu - Project A electrical equipment");content.newLineAtOffset(0,-20);
                    content.showText("C16 breaker quantity 3 pcs; B16 breaker quantity 2 pcs");content.endText();
                }
                if(vector) for(int i=0;i<15;i++) { content.addRect(20+i*12,100,8,20);content.stroke(); }
                if(scan) { var image=ImageIO.read(new ByteArrayInputStream(png(300,120)));content.drawImage(LosslessFactory.createFromImage(doc,image),10,30,280,120);image.flush(); }
            }
            doc.save(bytes);return bytes.toByteArray();
        }
    }
}
