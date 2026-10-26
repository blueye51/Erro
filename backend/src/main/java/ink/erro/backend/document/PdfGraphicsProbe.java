package ink.erro.backend.document;

import java.awt.geom.Point2D;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.contentstream.PDFGraphicsStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;

/** Inspect graphical content without decoding images; reject excessive stream work. */
final class PdfGraphicsProbe extends PDFGraphicsStreamEngine {
    int paths, operators; boolean largeImage; private Point2D point = new Point2D.Float();
    PdfGraphicsProbe(PDPage page) { super(page); }
    @Override protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
        if (++operators > 150000 || Thread.currentThread().isInterrupted()) throw DocumentException.invalid("PDF page is too complex. Export a simpler PDF or image.");
        super.processOperator(operator, operands);
    }
    public void drawImage(PDImage image) {
        if ((long)image.getWidth() * image.getHeight() > 40_000_000L) throw DocumentException.invalid("An embedded PDF image exceeds 40 megapixels.");
        var m = getGraphicsState().getCurrentTransformationMatrix();
        double area = Math.abs(m.getScalingFactorX() * m.getScalingFactorY());
        var box = getPage().getCropBox();
        if (area > box.getWidth() * box.getHeight() * .03) largeImage = true;
    }
    public void appendRectangle(Point2D a, Point2D b, Point2D c, Point2D d) { paths++; point = a; }
    public void clip(int rule) {} public void moveTo(float x,float y) { point = new Point2D.Float(x,y); }
    public void lineTo(float x,float y) { point = new Point2D.Float(x,y); paths++; }
    public void curveTo(float a,float b,float c,float d,float x,float y) { point = new Point2D.Float(x,y); paths++; }
    public Point2D getCurrentPoint() { return point; }
    public void closePath() {} public void endPath() {} public void strokePath() {}
    public void fillPath(int rule) {} public void fillAndStrokePath(int rule) {} public void shadingFill(org.apache.pdfbox.cos.COSName name) {}
}
