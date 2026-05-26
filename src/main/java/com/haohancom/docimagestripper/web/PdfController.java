package com.haohancom.docimagestripper.web;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.haohancom.docimagestripper.service.DocumentProcessingResult;
import com.haohancom.docimagestripper.service.ExtractedImage;
import com.haohancom.docimagestripper.service.PdfImagePlaceholderService;
import com.haohancom.docimagestripper.service.WordImagePlaceholderService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pdf")
@CrossOrigin(origins = "*", exposedHeaders = HttpHeaders.CONTENT_DISPOSITION)
public class PdfController {

    private final PdfImagePlaceholderService service;
    private final WordImagePlaceholderService wordService;

    public PdfController(PdfImagePlaceholderService service, WordImagePlaceholderService wordService) {
        this.service = service;
        this.wordService = wordService;
    }

    @PostMapping(value = "/replace-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "application/zip")
    public ResponseEntity<byte[]> replaceImages(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "placeholderPrefix", required = false) String placeholderPrefix,
            @RequestParam(value = "placeholderSuffix", required = false) String placeholderSuffix) throws IOException {
        DocumentUploadType uploadType = validateDocument(file);

        DocumentProcessingResult result;
        if (uploadType == DocumentUploadType.PDF) {
            result = service.replaceImages(file.getBytes(),
                    placeholderPart(placeholderPrefix), placeholderPart(placeholderSuffix));
        } else if (uploadType == DocumentUploadType.MODERN_WORD) {
            result = wordService.replaceImages(file.getBytes(),
                    placeholderPart(placeholderPrefix), placeholderPart(placeholderSuffix));
        } else {
            throw new UnsupportedWordFormatException("Please save this Word file as .docx and upload it again.");
        }
        String documentFileName = documentOutputFileName(file.getOriginalFilename());
        String zipFileName = zipOutputFileName(file.getOriginalFilename());
        byte[] zipBytes = toZip(result, documentFileName);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(zipFileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(zipBytes);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.TEXT_PLAIN)
                .body(exception.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<String> documentProcessingFailed(IOException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.TEXT_PLAIN)
                .body("Document processing failed: " + exception.getMessage());
    }

    @ExceptionHandler(UnsupportedWordFormatException.class)
    public ResponseEntity<String> unsupportedWordFormat(UnsupportedWordFormatException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.TEXT_PLAIN)
                .body(exception.getMessage());
    }

    private DocumentUploadType validateDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please upload a PDF or Word file.");
        }
        return DocumentUploadType.fromFilename(file.getOriginalFilename());
    }

    private String placeholderPart(String value) {
        return value == null ? "" : value;
    }

    private byte[] toZip(DocumentProcessingResult result, String documentFileName) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(documentFileName));
            zip.write(result.getDocumentBytes());
            zip.closeEntry();

            for (ExtractedImage image : result.getExtractedImages()) {
                zip.putNextEntry(new ZipEntry(image.getFilename()));
                zip.write(image.getBytes());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private String documentOutputFileName(String originalFilename) {
        String filename = normalizedFilename(originalFilename);
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex > 0) {
            return filename.substring(0, extensionIndex) + "-replaced" + filename.substring(extensionIndex);
        }
        return filename + "-replaced";
    }

    private String zipOutputFileName(String originalFilename) {
        String filename = normalizedFilename(originalFilename);
        int extensionIndex = filename.lastIndexOf('.');
        if (extensionIndex > 0) {
            return filename.substring(0, extensionIndex) + "-replaced.zip";
        }
        return filename + "-replaced.zip";
    }

    private String normalizedFilename(String originalFilename) {
        return originalFilename == null || originalFilename.trim().isEmpty()
                ? "converted"
                : originalFilename.trim();
    }

    private static class UnsupportedWordFormatException extends RuntimeException {
        UnsupportedWordFormatException(String message) {
            super(message);
        }
    }
}
