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
    private static final float MAX_FONT_SIZE = 14f;
    private static final float MIN_FONT_SIZE = 4f;

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
                removeImageDraws(document, placeholders.getPage(), new HashSet<COSBase>());
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

    private void removeImageDraws(PDDocument document, PDPage page, Set<COSBase> visitedForms) throws IOException {
        List<Object> rewrittenTokens = rewriteImageDraws(document, page, visitedForms);
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

        List<Object> rewrittenTokens = rewriteImageDraws(document, form, visitedForms);
        if (rewrittenTokens == null) {
            return;
        }

        try (OutputStream output = form.getContentStream().createOutputStream(COSName.FLATE_DECODE)) {
            new ContentStreamWriter(output).writeTokens(rewrittenTokens);
        }
    }

    private List<Object> rewriteImageDraws(PDDocument document, PDContentStream contentStream, Set<COSBase> visitedForms)
            throws IOException {
        PDResources resources = contentStream.getResources();
        if (resources == null) {
            return null;
        }

        PDFStreamParser parser = new PDFStreamParser(contentStream);
        parser.parse();
        List<Object> rewrittenTokens = new ArrayList<Object>();

        for (Object token : parser.getTokens()) {
            if (isInlineImageOperator(token)) {
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
        float fontSize = chooseFontSize(placeholder);
        float textWidth = textWidth(placeholder.getLabel(), fontSize);
        float x = placeholder.getX() + Math.max(0, (placeholder.getWidth() - textWidth) / 2f);
        float y = placeholder.getY() + Math.max(0, (placeholder.getHeight() - fontSize) / 2f);

        content.beginText();
        content.setFont(PLACEHOLDER_FONT, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(placeholder.getLabel());
        content.endText();
    }

    private float chooseFontSize(ImagePlaceholder placeholder) throws IOException {
        float size = Math.min(MAX_FONT_SIZE, Math.max(MIN_FONT_SIZE, placeholder.getHeight() * 0.35f));
        while (size > MIN_FONT_SIZE && textWidth(placeholder.getLabel(), size) > placeholder.getWidth() * 0.9f) {
            size -= 0.5f;
        }
        return Math.max(MIN_FONT_SIZE, size);
    }

    private float textWidth(String text, float fontSize) throws IOException {
        return PLACEHOLDER_FONT.getStringWidth(text) / 1000f * fontSize;
    }

    private static class ImagePositionCollector extends PDFStreamEngine {
        private final List<ImagePlaceholder> placeholders = new ArrayList<ImagePlaceholder>();
        private final List<ExtractedImage> extractedImages = new ArrayList<ExtractedImage>();
        private int nextImageNumber;

        ImagePositionCollector(int startImageNumber) throws IOException {
            this.nextImageNumber = startImageNumber;
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
            extractedImages.add(new ExtractedImage("image" + imageNumber + ".png", "image/png", toPngBytes(image)));
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
