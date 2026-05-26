package com.haohancom.docimagestripper.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.poi.openxml4j.opc.PackagePartName;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.springframework.stereotype.Service;

@Service
public class WordImagePlaceholderService {

    public DocumentProcessingResult replaceImages(byte[] input, String placeholderPrefix, String placeholderSuffix)
            throws IOException {
        if (input == null || input.length == 0) {
            throw new IllegalArgumentException("Word file must not be empty.");
        }

        List<ExtractedImage> extractedImages = new ArrayList<ExtractedImage>();
        Set<PackagePartName> removedPictureParts = new LinkedHashSet<PackagePartName>();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(input))) {
            String prefix = placeholderPart(placeholderPrefix);
            String suffix = placeholderPart(placeholderSuffix);
            int nextImageNumber = replaceBodyElements(document.getBodyElements(), extractedImages, 1,
                    prefix, suffix, removedPictureParts);
            for (XWPFHeader header : document.getHeaderList()) {
                nextImageNumber = replaceBodyElements(header.getBodyElements(), extractedImages, nextImageNumber,
                        prefix, suffix, removedPictureParts);
            }
            for (XWPFFooter footer : document.getFooterList()) {
                nextImageNumber = replaceBodyElements(footer.getBodyElements(), extractedImages, nextImageNumber,
                        prefix, suffix, removedPictureParts);
            }
            removePictureParts(document, removedPictureParts);

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.write(output);
            return new DocumentProcessingResult(output.toByteArray(), extractedImages);
        }
    }

    private int replaceBodyElements(List<IBodyElement> bodyElements, List<ExtractedImage> extractedImages,
            int nextImageNumber, String prefix, String suffix, Set<PackagePartName> removedPictureParts) {
        for (IBodyElement bodyElement : bodyElements) {
            if (bodyElement instanceof XWPFParagraph) {
                nextImageNumber = replaceParagraphImages(
                        java.util.Collections.singletonList((XWPFParagraph) bodyElement),
                        extractedImages, nextImageNumber, prefix, suffix, removedPictureParts);
            } else if (bodyElement instanceof XWPFTable) {
                nextImageNumber = replaceTableImages((XWPFTable) bodyElement, extractedImages,
                        nextImageNumber, prefix, suffix, removedPictureParts);
            }
        }
        return nextImageNumber;
    }

    private int replaceTableImages(XWPFTable table, List<ExtractedImage> extractedImages,
            int nextImageNumber, String prefix, String suffix, Set<PackagePartName> removedPictureParts) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                nextImageNumber = replaceBodyElements(cell.getBodyElements(), extractedImages,
                        nextImageNumber, prefix, suffix, removedPictureParts);
            }
        }
        return nextImageNumber;
    }

    private int replaceParagraphImages(List<XWPFParagraph> paragraphs, List<ExtractedImage> extractedImages,
            int nextImageNumber, String prefix, String suffix, Set<PackagePartName> removedPictureParts) {
        for (XWPFParagraph paragraph : paragraphs) {
            for (XWPFRun run : paragraph.getRuns()) {
                List<XWPFPicture> pictures = run.getEmbeddedPictures();
                if (pictures.isEmpty()) {
                    continue;
                }

                StringBuilder placeholders = new StringBuilder();
                for (XWPFPicture picture : pictures) {
                    XWPFPictureData data = picture.getPictureData();
                    if (data == null) {
                        continue;
                    }
                    String extension = imageExtension(data);
                    extractedImages.add(new ExtractedImage("image" + nextImageNumber + "." + extension,
                            contentType(extension), data.getData()));
                    if (placeholders.length() > 0) {
                        placeholders.append(" ");
                    }
                    placeholders.append(prefix).append("image").append(nextImageNumber).append(suffix);
                    removePictureRelationship(run, picture, data, removedPictureParts);
                    nextImageNumber++;
                }

                removePictureXml(run);
                if (placeholders.length() > 0) {
                    run.setText(placeholders.toString());
                }
            }
        }
        return nextImageNumber;
    }

    private String placeholderPart(String value) {
        return value == null ? "" : value;
    }

    private void removePictureRelationship(XWPFRun run, XWPFPicture picture, XWPFPictureData data,
            Set<PackagePartName> removedPictureParts) {
        String relationshipId = picture.getCTPicture().getBlipFill().getBlip().getEmbed();
        if (relationshipId != null && !relationshipId.trim().isEmpty()) {
            ((XWPFParagraph) run.getParent()).getPart().getPackagePart().removeRelationship(relationshipId);
        }
        removedPictureParts.add(data.getPackagePart().getPartName());
    }

    private void removePictureParts(XWPFDocument document, Set<PackagePartName> removedPictureParts) {
        for (PackagePartName partName : removedPictureParts) {
            if (document.getPackagePart().getPackage().containPart(partName)) {
                document.getPackagePart().getPackage().removePart(partName);
            }
        }
    }

    private void removePictureXml(XWPFRun run) {
        CTR ctr = run.getCTR();
        for (int i = ctr.sizeOfDrawingArray() - 1; i >= 0; i--) {
            ctr.removeDrawing(i);
        }
        for (int i = ctr.sizeOfPictArray() - 1; i >= 0; i--) {
            ctr.removePict(i);
        }
    }

    private String imageExtension(XWPFPictureData data) {
        String extension = data.suggestFileExtension();
        return extension == null || extension.trim().isEmpty() ? "bin" : extension;
    }

    private String contentType(String extension) {
        if ("png".equalsIgnoreCase(extension)) {
            return "image/png";
        }
        if ("jpg".equalsIgnoreCase(extension) || "jpeg".equalsIgnoreCase(extension)) {
            return "image/jpeg";
        }
        if ("gif".equalsIgnoreCase(extension)) {
            return "image/gif";
        }
        if ("bmp".equalsIgnoreCase(extension)) {
            return "image/bmp";
        }
        if ("tif".equalsIgnoreCase(extension) || "tiff".equalsIgnoreCase(extension)) {
            return "image/tiff";
        }
        return "application/octet-stream";
    }
}
