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

        byte[] output = service.replaceImages(input);

        try (PDDocument document = PDDocument.load(output)) {
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

        byte[] output = service.replaceImages(input);

        try (PDDocument document = PDDocument.load(output)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Form wrapped image");
            assertThat(text).contains("[image1]");
            assertThat(countImageDraws(document.getPage(0))).isZero();
        }
    }

    @Test
    void replacesEveryUseOfAReusedFormImage() throws Exception {
        byte[] input = createPdfWithSharedFormOnTwoPages();

        byte[] output = service.replaceImages(input);

        try (PDDocument document = PDDocument.load(output)) {
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

        byte[] output = service.replaceImages(input);

        try (PDDocument document = PDDocument.load(output)) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Inline image");
            assertThat(text).contains("[image1]");
            assertThat(countImageDraws(document.getPage(0))).isZero();
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
}
