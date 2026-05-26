package com.haohancom.docimagestripper.service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.geom.Point2D;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import org.apache.pdfbox.contentstream.PDContentStream;
import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdfwriter.ContentStreamWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageContentStream.AppendMode;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDInlineImage;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.springframework.stereotype.Service;

@Service
public class PdfImagePlaceholderService {

    private static final PDFont PLACEHOLDER_FONT = PDType1Font.HELVETICA_BOLD;
    private static final float PLACEHOLDER_FONT_SIZE = 18f;
    private static final float MAX_BORDER_PADDING = 8f;

    public Result replaceImages(byte[] input) throws IOException {
        if (input == null || input.length == 0) {
            throw new IllegalArgumentException("PDF file must not be empty.");
        }

        try (PDDocument document = PDDocument.load(input)) {
            int nextImageNumber = 1;
            List<PagePlaceholders> pagePlaceholders = new ArrayList<PagePlaceholders>();
            List<ExtractedImage> extractedImages = new ArrayList<ExtractedImage>();
            for (PDPage page : document.getPages()) {
                ImagePositionCollector collector = new ImagePositionCollector(nextImageNumber);
                collector.processPage(page);
                List<ImagePlaceholder> placeholders = collector.getPlaceholders();
                pagePlaceholders.add(new PagePlaceholders(page, placeholders));
                nextImageNumber += placeholders.size();
                extractedImages.addAll(collector.getExtractedImages());
            }

            for (PagePlaceholders placeholders : pagePlaceholders) {
                removeImageDraws(document, placeholders.getPage(), placeholders.getPlaceholders(),
                        new HashSet<COSBase>());
            }
            for (PagePlaceholders placeholders : pagePlaceholders) {
                drawPlaceholders(document, placeholders.getPage(), placeholders.getPlaceholders());
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return new Result(output.toByteArray(), extractedImages);
        }
    }

    private static byte[] toPngBytes(PDImage image) throws IOException {
        BufferedImage bufferedImage = image.getImage();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(bufferedImage, "png", output);
        return output.toByteArray();
    }

    private void removeImageDraws(PDDocument document, PDPage page, List<ImagePlaceholder> placeholders,
            Set<COSBase> visitedForms) throws IOException {
        List<Object> rewrittenTokens = rewriteImageDraws(document, page, placeholders, visitedForms);
        if (rewrittenTokens == null) {
            return;
        }

        PDStream stream = new PDStream(document);
        try (OutputStream output = stream.createOutputStream(COSName.FLATE_DECODE)) {
            new ContentStreamWriter(output).writeTokens(rewrittenTokens);
        }
        page.setContents(stream);
    }

    private void removeImageDraws(PDDocument document, PDFormXObject form, Set<COSBase> visitedForms)
            throws IOException {
        if (!visitedForms.add(form.getCOSObject())) {
            return;
        }

        List<Object> rewrittenTokens = rewriteImageDraws(document, form, collectLocalImagePlaceholders(form),
                visitedForms);
        if (rewrittenTokens == null) {
            return;
        }

        try (OutputStream output = form.getContentStream().createOutputStream(COSName.FLATE_DECODE)) {
            new ContentStreamWriter(output).writeTokens(rewrittenTokens);
        }
    }

    private List<ImagePlaceholder> collectLocalImagePlaceholders(PDFormXObject form) throws IOException {
        if (form.getResources() == null) {
            return Collections.emptyList();
        }

        ImagePositionCollector collector = new ImagePositionCollector(1, false);
        PDPage syntheticPage = new PDPage(form.getBBox());
        syntheticPage.setResources(form.getResources());
        syntheticPage.setContents(form.getContentStream());
        collector.processPage(syntheticPage);
        return collector.getPlaceholders();
    }

    private List<Object> rewriteImageDraws(PDDocument document, PDContentStream contentStream,
            List<ImagePlaceholder> placeholders, Set<COSBase> visitedForms) throws IOException {
        PDResources resources = contentStream.getResources();
        if (resources == null) {
            return null;
        }

        PDFStreamParser parser = new PDFStreamParser(contentStream);
        parser.parse();
        List<Object> rewrittenTokens = new ArrayList<Object>();

        List<Object> tokens = parser.getTokens();
        for (int i = 0; i < tokens.size(); i++) {
            Object token = tokens.get(i);
            if (isInlineImageOperator(token)) {
                continue;
            }
            if (isRemovableImageBorderRectangle(tokens, i, placeholders)) {
                removeLastTokens(rewrittenTokens, 4);
                i++;
                continue;
            }
            if (isRemovableImageBorderLinePath(tokens, i, placeholders)) {
                removeLastTokens(rewrittenTokens, 13);
                continue;
            }
            PDXObject xObject = getDrawnXObject(token, rewrittenTokens, resources);
            if (xObject instanceof PDImageXObject) {
                rewrittenTokens.remove(rewrittenTokens.size() - 1);
                continue;
            }
            if (xObject instanceof PDFormXObject) {
                removeImageDraws(document, (PDFormXObject) xObject, visitedForms);
            }
            rewrittenTokens.add(token);
        }
        return rewrittenTokens;
    }

    private boolean isRemovableImageBorderRectangle(List<Object> tokens, int operatorIndex,
            List<ImagePlaceholder> placeholders) {
        if (placeholders.isEmpty() || operatorIndex < 4 || operatorIndex + 1 >= tokens.size()) {
            return false;
        }
        if (!isOperator(tokens.get(operatorIndex), OperatorName.APPEND_RECT)
                || !isStrokeOperator(tokens.get(operatorIndex + 1))) {
            return false;
        }

        Float x = numberValue(tokens.get(operatorIndex - 4));
        Float y = numberValue(tokens.get(operatorIndex - 3));
        Float width = numberValue(tokens.get(operatorIndex - 2));
        Float height = numberValue(tokens.get(operatorIndex - 1));
        if (x == null || y == null || width == null || height == null) {
            return false;
        }

        return matchesImageBorder(x.floatValue(), y.floatValue(), width.floatValue(), height.floatValue(),
                placeholders);
    }

    private boolean isRemovableImageBorderLinePath(List<Object> tokens, int operatorIndex,
            List<ImagePlaceholder> placeholders) {
        if (placeholders.isEmpty() || operatorIndex < 13 || !isStrokeOperator(tokens.get(operatorIndex))) {
            return false;
        }
        int start = operatorIndex - 13;
        if (!isClosedRectangleLinePath(tokens, start)) {
            return false;
        }

        float minX = numberValue(tokens.get(start)).floatValue();
        float maxX = minX;
        float minY = numberValue(tokens.get(start + 1)).floatValue();
        float maxY = minY;
        int[] coordinateIndexes = new int[] { start, start + 3, start + 6, start + 9 };
        for (int coordinateIndex : coordinateIndexes) {
            float x = numberValue(tokens.get(coordinateIndex)).floatValue();
            float y = numberValue(tokens.get(coordinateIndex + 1)).floatValue();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        return matchesImageBorder(minX, minY, maxX - minX, maxY - minY, placeholders);
    }

    private boolean isClosedRectangleLinePath(List<Object> tokens, int start) {
        Float x1 = numberValue(tokens.get(start));
        Float y1 = numberValue(tokens.get(start + 1));
        Float x2 = numberValue(tokens.get(start + 3));
        Float y2 = numberValue(tokens.get(start + 4));
        Float x3 = numberValue(tokens.get(start + 6));
        Float y3 = numberValue(tokens.get(start + 7));
        Float x4 = numberValue(tokens.get(start + 9));
        Float y4 = numberValue(tokens.get(start + 10));
        if (x1 == null || y1 == null || x2 == null || y2 == null || x3 == null || y3 == null
                || x4 == null || y4 == null) {
            return false;
        }
        if (!isOperator(tokens.get(start + 2), OperatorName.MOVE_TO)
                || !isOperator(tokens.get(start + 5), OperatorName.LINE_TO)
                || !isOperator(tokens.get(start + 8), OperatorName.LINE_TO)
                || !isOperator(tokens.get(start + 11), OperatorName.LINE_TO)
                || !isOperator(tokens.get(start + 12), OperatorName.CLOSE_PATH)) {
            return false;
        }

        Point[] points = new Point[] {
                new Point(x1.floatValue(), y1.floatValue()),
                new Point(x2.floatValue(), y2.floatValue()),
                new Point(x3.floatValue(), y3.floatValue()),
                new Point(x4.floatValue(), y4.floatValue())
        };
        return isAxisAlignedRectangle(points);
    }

    private boolean isAxisAlignedRectangle(Point[] points) {
        float minX = points[0].getX();
        float maxX = minX;
        float minY = points[0].getY();
        float maxY = minY;
        for (Point point : points) {
            minX = Math.min(minX, point.getX());
            maxX = Math.max(maxX, point.getX());
            minY = Math.min(minY, point.getY());
            maxY = Math.max(maxY, point.getY());
        }
        if (sharesCoordinate(minX, maxX) || sharesCoordinate(minY, maxY)) {
            return false;
        }
        for (int i = 0; i < points.length; i++) {
            Point current = points[i];
            Point next = points[(i + 1) % points.length];
            if (!isAxisAlignedSegment(current, next) || !isRectangleCorner(current, minX, minY, maxX, maxY)) {
                return false;
            }
        }
        return hasCorner(points, minX, minY)
                && hasCorner(points, minX, maxY)
                && hasCorner(points, maxX, minY)
                && hasCorner(points, maxX, maxY);
    }

    private boolean isAxisAlignedSegment(Point first, Point second) {
        return sharesCoordinate(first.getX(), second.getX()) || sharesCoordinate(first.getY(), second.getY());
    }

    private boolean isRectangleCorner(Point point, float minX, float minY, float maxX, float maxY) {
        return (sharesCoordinate(point.getX(), minX) || sharesCoordinate(point.getX(), maxX))
                && (sharesCoordinate(point.getY(), minY) || sharesCoordinate(point.getY(), maxY));
    }

    private boolean hasCorner(Point[] points, float x, float y) {
        for (Point point : points) {
            if (sharesCoordinate(point.getX(), x) && sharesCoordinate(point.getY(), y)) {
                return true;
            }
        }
        return false;
    }

    private boolean sharesCoordinate(float first, float second) {
        return Math.abs(first - second) < 0.01f;
    }

    private boolean isStrokeOperator(Object token) {
        if (!(token instanceof Operator)) {
            return false;
        }
        String name = ((Operator) token).getName();
        return OperatorName.STROKE_PATH.equals(name) || OperatorName.CLOSE_AND_STROKE.equals(name);
    }

    private boolean isOperator(Object token, String operatorName) {
        return token instanceof Operator && operatorName.equals(((Operator) token).getName());
    }

    private Float numberValue(Object token) {
        if (!(token instanceof COSNumber)) {
            return null;
        }
        return Float.valueOf(((COSNumber) token).floatValue());
    }

    private boolean matchesImageBorder(float x, float y, float width, float height,
            List<ImagePlaceholder> placeholders) {
        Rectangle border = Rectangle.from(x, y, width, height);
        for (ImagePlaceholder placeholder : placeholders) {
            Rectangle image = Rectangle.from(placeholder.getX(), placeholder.getY(), placeholder.getWidth(),
                    placeholder.getHeight());
            if (border.isCloseTo(image, MAX_BORDER_PADDING)) {
                return true;
            }
        }
        return false;
    }

    private void removeLastTokens(List<Object> tokens, int count) {
        for (int i = 0; i < count && !tokens.isEmpty(); i++) {
            tokens.remove(tokens.size() - 1);
        }
    }

    private boolean isInlineImageOperator(Object token) {
        if (!(token instanceof Operator)) {
            return false;
        }
        Operator operator = (Operator) token;
        return OperatorName.BEGIN_INLINE_IMAGE.equals(operator.getName()) && operator.getImageData() != null
                && operator.getImageData().length > 0;
    }

    private PDXObject getDrawnXObject(Object token, List<Object> tokens, PDResources resources) throws IOException {
        if (!(token instanceof Operator) || !OperatorName.DRAW_OBJECT.equals(((Operator) token).getName())) {
            return null;
        }
        if (tokens.isEmpty() || !(tokens.get(tokens.size() - 1) instanceof COSName)) {
            return null;
        }

        COSName objectName = (COSName) tokens.get(tokens.size() - 1);
        return resources.getXObject(objectName);
    }

    private void drawPlaceholders(PDDocument document, PDPage page, List<ImagePlaceholder> placeholders)
            throws IOException {
        if (placeholders.isEmpty()) {
            return;
        }

        try (PDPageContentStream content = new PDPageContentStream(document, page, AppendMode.APPEND, true, true)) {
            content.setNonStrokingColor(Color.BLACK);
            for (ImagePlaceholder placeholder : placeholders) {
                drawPlaceholder(content, placeholder);
            }
        }
    }

    private void drawPlaceholder(PDPageContentStream content, ImagePlaceholder placeholder) throws IOException {
        float textWidth = textWidth(placeholder.getLabel(), PLACEHOLDER_FONT_SIZE);
        float x = placeholder.getX() + Math.max(0, (placeholder.getWidth() - textWidth) / 2f);
        float y = placeholder.getY() + Math.max(0, (placeholder.getHeight() - PLACEHOLDER_FONT_SIZE) / 2f);

        content.beginText();
        content.setFont(PLACEHOLDER_FONT, PLACEHOLDER_FONT_SIZE);
        content.newLineAtOffset(x, y);
        content.showText(placeholder.getLabel());
        content.endText();
    }

    private float textWidth(String text, float fontSize) throws IOException {
        return PLACEHOLDER_FONT.getStringWidth(text) / 1000f * fontSize;
    }

    private static class ImagePositionCollector extends PDFStreamEngine {
        private final List<ImagePlaceholder> placeholders = new ArrayList<ImagePlaceholder>();
        private final List<ExtractedImage> extractedImages = new ArrayList<ExtractedImage>();
        private final boolean extractImages;
        private int nextImageNumber;

        ImagePositionCollector(int startImageNumber) throws IOException {
            this(startImageNumber, true);
        }

        ImagePositionCollector(int startImageNumber, boolean extractImages) throws IOException {
            this.nextImageNumber = startImageNumber;
            this.extractImages = extractImages;
            addOperator(new Concatenate());
            addOperator(new DrawObject());
            addOperator(new SetGraphicsStateParameters());
            addOperator(new Save());
            addOperator(new Restore());
            addOperator(new SetMatrix());
        }

        List<ImagePlaceholder> getPlaceholders() {
            return placeholders;
        }

        List<ExtractedImage> getExtractedImages() {
            return extractedImages;
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if (OperatorName.BEGIN_INLINE_IMAGE.equals(operator.getName()) && operator.getImageData() != null
                    && operator.getImageData().length > 0) {
                PDInlineImage image = new PDInlineImage(operator.getImageParameters(), operator.getImageData(),
                        getResources());
                addImage(image, getGraphicsState().getCurrentTransformationMatrix());
                return;
            }
            if (OperatorName.DRAW_OBJECT.equals(operator.getName()) && !operands.isEmpty()
                    && operands.get(0) instanceof COSName && getResources() != null) {
                COSName objectName = (COSName) operands.get(0);
                PDXObject xObject = getResources().getXObject(objectName);
                if (xObject instanceof PDImageXObject) {
                    addImage((PDImageXObject) xObject, getGraphicsState().getCurrentTransformationMatrix());
                    return;
                }
                if (xObject instanceof PDFormXObject) {
                    showForm((PDFormXObject) xObject);
                    return;
                }
            }
            super.processOperator(operator, operands);
        }

        private void addImage(PDImage image, Matrix matrix) throws IOException {
            int imageNumber = nextImageNumber;
            placeholders.add(toPlaceholder(matrix, imageNumber));
            if (extractImages) {
                extractedImages.add(new ExtractedImage("image" + imageNumber + ".png", "image/png",
                        toPngBytes(image)));
            }
            nextImageNumber++;
        }

        private ImagePlaceholder toPlaceholder(Matrix matrix, int imageNumber) {
            List<Point2D.Float> points = Arrays.asList(
                    matrix.transformPoint(0, 0),
                    matrix.transformPoint(1, 0),
                    matrix.transformPoint(0, 1),
                    matrix.transformPoint(1, 1));

            float minX = points.get(0).x;
            float maxX = points.get(0).x;
            float minY = points.get(0).y;
            float maxY = points.get(0).y;
            for (Point2D.Float point : points) {
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y);
                maxY = Math.max(maxY, point.y);
            }

            ImagePlaceholder placeholder = new ImagePlaceholder("[image" + imageNumber + "]",
                    minX,
                    minY,
                    Math.max(1f, maxX - minX),
                    Math.max(1f, maxY - minY));
            return placeholder;
        }
    }

    public static class Result {
        private final byte[] pdfBytes;
        private final List<ExtractedImage> extractedImages;

        public Result(byte[] pdfBytes, List<ExtractedImage> extractedImages) {
            this.pdfBytes = Arrays.copyOf(pdfBytes, pdfBytes.length);
            this.extractedImages = Collections.unmodifiableList(new ArrayList<ExtractedImage>(extractedImages));
        }

        public byte[] getPdfBytes() {
            return Arrays.copyOf(pdfBytes, pdfBytes.length);
        }

        public List<ExtractedImage> getExtractedImages() {
            return extractedImages;
        }
    }

    public static class ExtractedImage {
        private final String filename;
        private final String contentType;
        private final byte[] bytes;

        public ExtractedImage(String filename, String contentType, byte[] bytes) {
            this.filename = filename;
            this.contentType = contentType;
            this.bytes = Arrays.copyOf(bytes, bytes.length);
        }

        public String getFilename() {
            return filename;
        }

        public String getContentType() {
            return contentType;
        }

        public byte[] getBytes() {
            return Arrays.copyOf(bytes, bytes.length);
        }
    }

    private static class PagePlaceholders {
        private final PDPage page;
        private final List<ImagePlaceholder> placeholders;

        PagePlaceholders(PDPage page, List<ImagePlaceholder> placeholders) {
            this.page = page;
            this.placeholders = placeholders;
        }

        PDPage getPage() {
            return page;
        }

        List<ImagePlaceholder> getPlaceholders() {
            return placeholders;
        }
    }

    private static class Rectangle {
        private final float left;
        private final float bottom;
        private final float right;
        private final float top;

        static Rectangle from(float x, float y, float width, float height) {
            return new Rectangle(Math.min(x, x + width),
                    Math.min(y, y + height),
                    Math.max(x, x + width),
                    Math.max(y, y + height));
        }

        Rectangle(float left, float bottom, float right, float top) {
            this.left = left;
            this.bottom = bottom;
            this.right = right;
            this.top = top;
        }

        boolean isCloseTo(Rectangle other, float tolerance) {
            return Math.abs(left - other.left) <= tolerance
                    && Math.abs(bottom - other.bottom) <= tolerance
                    && Math.abs(right - other.right) <= tolerance
                    && Math.abs(top - other.top) <= tolerance;
        }
    }

    private static class Point {
        private final float x;
        private final float y;

        Point(float x, float y) {
            this.x = x;
            this.y = y;
        }

        float getX() {
            return x;
        }

        float getY() {
            return y;
        }
    }

    private static class ImagePlaceholder {
        private final String label;
        private final float x;
        private final float y;
        private final float width;
        private final float height;

        ImagePlaceholder(String label, float x, float y, float width, float height) {
            this.label = label;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        String getLabel() {
            return label;
        }

        float getX() {
            return x;
        }

        float getY() {
            return y;
        }

        float getWidth() {
            return width;
        }

        float getHeight() {
            return height;
        }
    }
}
