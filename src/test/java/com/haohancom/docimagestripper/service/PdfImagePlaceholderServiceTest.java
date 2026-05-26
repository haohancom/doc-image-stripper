package com.haohancom.docimagestripper.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.List;

import org.apache.pdfbox.contentstream.PDContentStream;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.OperatorName;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSNumber;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.graphics.color.PDDeviceRGB;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDInlineImage;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.util.Matrix;
import org.junit.jupiter.api.Test;

class PdfImagePlaceholderServiceTest {

    private final PdfImagePlaceholderService service = new PdfImagePlaceholderService();

    @Test
    void replacesImageWithNumberedPlaceholderWhileKeepingText() throws Exception {
        byte[] input = createPdfWithOneImage();

        PdfImagePlaceholderService.Result result = service.replaceImages(input);

        assertThat(result.getExtractedImages()).hasSize(1);
        assertThat(result.getExtractedImages().get(0).getFilename()).isEqualTo("image1.png");
        assertThat(result.getExtractedImages().get(0).getContentType()).isEqualTo("image/png");
        assertThat(result.getExtractedImages().get(0).getBytes()).startsWith(
                (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');

        try (PDDocument document = PDDocument.load(result.getPdfBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Original Title");
            assertThat(text).contains("Body text stays here.");
            assertThat(text).contains("[image1]");
            assertThat(countImageDraws(document.getPage(0))).isZero();
        }
    }

    @Test
    void replacesImageNestedInsideFormXObject() throws Exception {
        byte[] input = createPdfWithImageInsideForm();

        PdfImagePlaceholderService.Result result = service.replaceImages(input);

        assertThat(result.getExtractedImages()).extracting(PdfImagePlaceholderService.ExtractedImage::getFilename)
                .containsExactly("image1.png");

        try (PDDocument document = PDDocument.load(result.getPdfBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Form wrapped image");
            assertThat(text).contains("[image1]");
            assertThat(countImageDraws(document.getPage(0))).isZero();
        }
    }

    @Test
    void replacesEveryUseOfAReusedFormImage() throws Exception {
        byte[] input = createPdfWithSharedFormOnTwoPages();

        PdfImagePlaceholderService.Result result = service.replaceImages(input);

        assertThat(result.getExtractedImages()).extracting(PdfImagePlaceholderService.ExtractedImage::getFilename)
                .containsExactly("image1.png", "image2.png");

        try (PDDocument document = PDDocument.load(result.getPdfBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("[image1]");
            assertThat(text).contains("[image2]");
            assertThat(countImageDraws(document.getPage(0))).isZero();
            assertThat(countImageDraws(document.getPage(1))).isZero();
        }
    }

    @Test
    void replacesInlineImages() throws Exception {
        byte[] input = createPdfWithInlineImage();

        PdfImagePlaceholderService.Result result = service.replaceImages(input);

        assertThat(result.getExtractedImages()).extracting(PdfImagePlaceholderService.ExtractedImage::getFilename)
                .containsExactly("image1.png");

        try (PDDocument document = PDDocument.load(result.getPdfBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Inline image");
            assertThat(text).contains("[image1]");
            assertThat(countImageDraws(document.getPage(0))).isZero();
        }
    }

    @Test
    void removesSimpleBorderAroundReplacedImageSoPlaceholderStaysAsText() throws Exception {
        byte[] input = createPdfWithImageAndMatchingBorder();

        PdfImagePlaceholderService.Result result = service.replaceImages(input);

        try (PDDocument document = PDDocument.load(result.getPdfBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("[image1]");
            assertThat(countImageDraws(document.getPage(0))).isZero();
            assertThat(countStrokedRectangles(document.getPage(0), 72, 620, 80, 50)).isZero();
        }
    }

    @Test
    void removesLinePathBorderAroundReplacedImageSoPlaceholderStaysAsText() throws Exception {
        byte[] input = createPdfWithImageAndLinePathBorder();

        PdfImagePlaceholderService.Result result = service.replaceImages(input);

        try (PDDocument document = PDDocument.load(result.getPdfBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("[image1]");
            assertThat(countImageDraws(document.getPage(0))).isZero();
            assertThat(countLinePathRectangles(document.getPage(0), 72, 620, 80, 50)).isZero();
        }
    }

    @Test
    void removesBorderInsideFormXObjectAroundReplacedImage() throws Exception {
        byte[] input = createPdfWithBorderedImageInsideForm();

        PdfImagePlaceholderService.Result result = service.replaceImages(input);

        try (PDDocument document = PDDocument.load(result.getPdfBytes())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("[image1]");
            assertThat(countImageDraws(document.getPage(0))).isZero();
            assertThat(countStrokedRectangles(document.getPage(0), 0, 0, 80, 50)).isZero();
        }
    }

    private byte[] createPdfWithOneImage() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            BufferedImage bufferedImage = new BufferedImage(80, 50, BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < bufferedImage.getWidth(); x++) {
                for (int y = 0; y < bufferedImage.getHeight(); y++) {
                    bufferedImage.setRGB(x, y, Color.RED.getRGB());
                }
            }
            PDImageXObject image = LosslessFactory.createFromImage(document, bufferedImage);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA_BOLD, 18);
                content.newLineAtOffset(72, 720);
                content.showText("Original Title");
                content.endText();

                content.drawImage(image, 72, 620, 80, 50);

                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(72, 580);
                content.showText("Body text stays here.");
                content.endText();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createPdfWithImageInsideForm() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            PDImageXObject image = LosslessFactory.createFromImage(document, solidImage(80, 50, Color.BLUE));
            PDFormXObject form = new PDFormXObject(document);
            form.setResources(new PDResources());
            form.setBBox(new PDRectangle(0, 0, 80, 50));
            try (OutputStream output = form.getContentStream().createOutputStream();
                    PDPageContentStream formContent = new PDPageContentStream(document, form, output)) {
                formContent.drawImage(image, 0, 0, 80, 50);
            }

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(72, 720);
                content.showText("Form wrapped image");
                content.endText();

                content.saveGraphicsState();
                content.transform(Matrix.getTranslateInstance(72, 620));
                content.drawForm(form);
                content.restoreGraphicsState();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createPdfWithSharedFormOnTwoPages() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDFormXObject form = createImageForm(document, Color.GREEN);
            for (int i = 0; i < 2; i++) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(PDType1Font.HELVETICA, 12);
                    content.newLineAtOffset(72, 720);
                    content.showText("Page " + (i + 1));
                    content.endText();

                    content.saveGraphicsState();
                    content.transform(Matrix.getTranslateInstance(72, 620));
                    content.drawForm(form);
                    content.restoreGraphicsState();
                }
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createPdfWithInlineImage() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            COSDictionary parameters = new COSDictionary();
            byte[] data = new byte[] {
                    (byte) 255, 0, 0, 0, (byte) 255, 0,
                    0, 0, (byte) 255, (byte) 255, (byte) 255, 0
            };
            PDInlineImage image = new PDInlineImage(parameters, data, new PDResources());
            image.setWidth(2);
            image.setHeight(2);
            image.setBitsPerComponent(8);
            image.setColorSpace(PDDeviceRGB.INSTANCE);

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(72, 720);
                content.showText("Inline image");
                content.endText();

                content.drawImage(image, 72, 620, 80, 50);
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createPdfWithImageAndMatchingBorder() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            PDImageXObject image = LosslessFactory.createFromImage(document, solidImage(80, 50, Color.RED));

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(72, 720);
                content.showText("Image with border");
                content.endText();

                content.drawImage(image, 72, 620, 80, 50);
                content.addRect(72, 620, 80, 50);
                content.stroke();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createPdfWithImageAndLinePathBorder() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            PDImageXObject image = LosslessFactory.createFromImage(document, solidImage(80, 50, Color.RED));

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(72, 720);
                content.showText("Image with line border");
                content.endText();

                content.drawImage(image, 72, 620, 80, 50);
                content.moveTo(72, 620);
                content.lineTo(152, 620);
                content.lineTo(152, 670);
                content.lineTo(72, 670);
                content.closePath();
                content.stroke();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] createPdfWithBorderedImageInsideForm() throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            PDImageXObject image = LosslessFactory.createFromImage(document, solidImage(80, 50, Color.RED));
            PDFormXObject form = new PDFormXObject(document);
            form.setResources(new PDResources());
            form.setBBox(new PDRectangle(0, 0, 80, 50));
            try (OutputStream output = form.getContentStream().createOutputStream();
                    PDPageContentStream formContent = new PDPageContentStream(document, form, output)) {
                formContent.drawImage(image, 0, 0, 80, 50);
                formContent.addRect(0, 0, 80, 50);
                formContent.stroke();
            }

            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 12);
                content.newLineAtOffset(72, 720);
                content.showText("Form image with border");
                content.endText();

                content.saveGraphicsState();
                content.transform(Matrix.getTranslateInstance(72, 620));
                content.drawForm(form);
                content.restoreGraphicsState();
            }

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return output.toByteArray();
        }
    }

    private PDFormXObject createImageForm(PDDocument document, Color color) throws Exception {
        PDImageXObject image = LosslessFactory.createFromImage(document, solidImage(80, 50, color));
        PDFormXObject form = new PDFormXObject(document);
        form.setResources(new PDResources());
        form.setBBox(new PDRectangle(0, 0, 80, 50));
        try (OutputStream output = form.getContentStream().createOutputStream();
                PDPageContentStream formContent = new PDPageContentStream(document, form, output)) {
            formContent.drawImage(image, 0, 0, 80, 50);
        }
        return form;
    }

    private BufferedImage solidImage(int width, int height, Color color) {
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < bufferedImage.getWidth(); x++) {
            for (int y = 0; y < bufferedImage.getHeight(); y++) {
                bufferedImage.setRGB(x, y, color.getRGB());
            }
        }
        return bufferedImage;
    }

    private int countImageDraws(PDContentStream contentStream) throws Exception {
        PDResources resources = contentStream.getResources();
        if (resources == null) {
            return 0;
        }

        PDFStreamParser parser = new PDFStreamParser(contentStream);
        parser.parse();
        List<Object> tokens = parser.getTokens();
        int imageDraws = 0;
        for (int i = 0; i < tokens.size(); i++) {
            Object token = tokens.get(i);
            if (token instanceof Operator && OperatorName.BEGIN_INLINE_IMAGE.equals(((Operator) token).getName())) {
                imageDraws++;
            }
            if (token instanceof Operator && OperatorName.DRAW_OBJECT.equals(((Operator) token).getName())
                    && i > 0 && tokens.get(i - 1) instanceof COSName) {
                PDXObject xObject = resources.getXObject((COSName) tokens.get(i - 1));
                if (xObject instanceof PDImageXObject) {
                    imageDraws++;
                } else if (xObject instanceof PDFormXObject) {
                    imageDraws += countImageDraws((PDFormXObject) xObject);
                }
            }
        }
        return imageDraws;
    }

    private int countStrokedRectangles(PDContentStream contentStream, float x, float y, float width, float height)
            throws Exception {
        PDFStreamParser parser = new PDFStreamParser(contentStream);
        parser.parse();
        List<Object> tokens = parser.getTokens();
        int count = 0;
        for (int i = 4; i + 1 < tokens.size(); i++) {
            Object token = tokens.get(i);
            Object paintToken = tokens.get(i + 1);
            if (!(token instanceof Operator) || !"re".equals(((Operator) token).getName())
                    || !(paintToken instanceof Operator) || !"S".equals(((Operator) paintToken).getName())) {
                continue;
            }
            if (numberEquals(tokens.get(i - 4), x) && numberEquals(tokens.get(i - 3), y)
                    && numberEquals(tokens.get(i - 2), width) && numberEquals(tokens.get(i - 1), height)) {
                count++;
            }
        }
        PDResources resources = contentStream.getResources();
        if (resources != null) {
            for (int i = 1; i < tokens.size(); i++) {
                Object token = tokens.get(i);
                if (token instanceof Operator && OperatorName.DRAW_OBJECT.equals(((Operator) token).getName())
                        && tokens.get(i - 1) instanceof COSName) {
                    PDXObject xObject = resources.getXObject((COSName) tokens.get(i - 1));
                    if (xObject instanceof PDFormXObject) {
                        count += countStrokedRectangles((PDFormXObject) xObject, x, y, width, height);
                    }
                }
            }
        }
        return count;
    }

    private boolean numberEquals(Object token, float expected) {
        return token instanceof COSNumber && Math.abs(((COSNumber) token).floatValue() - expected) < 0.01f;
    }

    private int countLinePathRectangles(PDContentStream contentStream, float x, float y, float width, float height)
            throws Exception {
        PDFStreamParser parser = new PDFStreamParser(contentStream);
        parser.parse();
        List<Object> tokens = parser.getTokens();
        int count = 0;
        for (int i = 13; i < tokens.size(); i++) {
            Object token = tokens.get(i);
            if (!(token instanceof Operator) || !"S".equals(((Operator) token).getName())) {
                continue;
            }
            if (isClosedLinePath(tokens, i - 13, x, y, width, height)) {
                count++;
            }
        }
        return count;
    }

    private boolean isClosedLinePath(List<Object> tokens, int start, float x, float y, float width, float height) {
        return numberEquals(tokens.get(start), x)
                && numberEquals(tokens.get(start + 1), y)
                && isOperator(tokens.get(start + 2), "m")
                && numberEquals(tokens.get(start + 3), x + width)
                && numberEquals(tokens.get(start + 4), y)
                && isOperator(tokens.get(start + 5), "l")
                && numberEquals(tokens.get(start + 6), x + width)
                && numberEquals(tokens.get(start + 7), y + height)
                && isOperator(tokens.get(start + 8), "l")
                && numberEquals(tokens.get(start + 9), x)
                && numberEquals(tokens.get(start + 10), y + height)
                && isOperator(tokens.get(start + 11), "l")
                && isOperator(tokens.get(start + 12), "h");
    }

    private boolean isOperator(Object token, String name) {
        return token instanceof Operator && name.equals(((Operator) token).getName());
    }
}
