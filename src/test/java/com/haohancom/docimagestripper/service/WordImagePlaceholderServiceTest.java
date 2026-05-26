package com.haohancom.docimagestripper.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import javax.imageio.ImageIO;

import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class WordImagePlaceholderServiceTest {

    private final WordImagePlaceholderService service = new WordImagePlaceholderService();

    @Test
    void replacesDocumentBodyImageWithPlaceholderAndExtractsImage() throws Exception {
        byte[] input = createDocxWithBodyImage();

        DocumentProcessingResult result = service.replaceImages(input, "", "");

        assertThat(result.getExtractedImages()).hasSize(1);
        assertThat(result.getExtractedImages().get(0).getFilename()).isEqualTo("image1.png");
        assertThat(result.getExtractedImages().get(0).getContentType()).isEqualTo("image/png");
        assertThat(result.getExtractedImages().get(0).getBytes()).startsWith(
                (byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result.getDocumentBytes()))) {
            assertThat(document.getParagraphArray(0).getText()).contains("Before image");
            assertThat(document.getParagraphArray(1).getText()).contains("image1");
            assertThat(document.getAllPictures()).isEmpty();
        }
    }

    @Test
    void wrapsWordPlaceholdersWithCustomPrefixAndSuffix() throws Exception {
        byte[] input = createDocxWithBodyImage();

        DocumentProcessingResult result = service.replaceImages(input, "-", "!");

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result.getDocumentBytes()))) {
            assertThat(document.getParagraphArray(1).getText()).contains("-image1!");
        }
    }

    @Test
    void replacesImagesInTablesHeadersAndFooters() throws Exception {
        byte[] input = createDocxWithTableHeaderAndFooterImages();

        DocumentProcessingResult result = service.replaceImages(input, "", "");

        assertThat(result.getExtractedImages()).extracting(ExtractedImage::getFilename)
                .containsExactly("image1.png", "image2.png", "image3.png");

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(result.getDocumentBytes()))) {
            assertThat(document.getTables().get(0).getRow(0).getCell(0).getText()).contains("image1");
            assertThat(document.getHeaderArray(0).getText()).contains("image2");
            assertThat(document.getFooterArray(0).getText()).contains("image3");
            assertThat(document.getAllPictures()).isEmpty();
            assertThat(document.getHeaderArray(0).getAllPictures()).isEmpty();
            assertThat(document.getFooterArray(0).getAllPictures()).isEmpty();
        }
    }

    private byte[] createDocxWithBodyImage() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph textParagraph = document.createParagraph();
            textParagraph.createRun().setText("Before image");

            XWPFParagraph imageParagraph = document.createParagraph();
            addImageRun(imageParagraph.createRun(), Color.RED);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] createDocxWithTableHeaderAndFooterImages() throws Exception {
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFTable table = document.createTable(1, 1);
            addImageRun(table.getRow(0).getCell(0).getParagraphArray(0).createRun(), Color.BLUE);

            org.apache.poi.xwpf.usermodel.XWPFHeader header = document.createHeader(
                    org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
            addImageRun(header.createParagraph().createRun(), Color.GREEN);

            org.apache.poi.xwpf.usermodel.XWPFFooter footer = document.createFooter(
                    org.apache.poi.wp.usermodel.HeaderFooterType.DEFAULT);
            addImageRun(footer.createParagraph().createRun(), Color.YELLOW);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.write(output);
            return output.toByteArray();
        }
    }

    private void addImageRun(XWPFRun run, Color color) throws Exception {
        run.addPicture(new ByteArrayInputStream(pngBytes(color)), Document.PICTURE_TYPE_PNG,
                "sample.png", Units.toEMU(80), Units.toEMU(50));
    }

    private byte[] pngBytes(Color color) throws Exception {
        BufferedImage image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, color.getRGB());
            }
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
