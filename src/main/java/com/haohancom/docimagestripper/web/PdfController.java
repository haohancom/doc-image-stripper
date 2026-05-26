package com.haohancom.docimagestripper.web;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.haohancom.docimagestripper.service.PdfImagePlaceholderService;
import com.haohancom.docimagestripper.service.PdfImagePlaceholderService.ExtractedImage;
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

    public PdfController(PdfImagePlaceholderService service) {
        this.service = service;
    }

    @PostMapping(value = "/replace-images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = "application/zip")
    public ResponseEntity<byte[]> replaceImages(@RequestParam("file") MultipartFile file,
            @RequestParam(value = "placeholderPrefix", required = false) String placeholderPrefix,
            @RequestParam(value = "placeholderSuffix", required = false) String placeholderSuffix) throws IOException {
        validatePdf(file);

        PdfImagePlaceholderService.Result result = service.replaceImages(file.getBytes(),
                placeholderPart(placeholderPrefix), placeholderPart(placeholderSuffix));
        String pdfFileName = pdfOutputFileName(file.getOriginalFilename());
        String zipFileName = zipOutputFileName(file.getOriginalFilename());
        byte[] zipBytes = toZip(result, pdfFileName);

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
    public ResponseEntity<String> pdfProcessingFailed(IOException exception) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .contentType(MediaType.TEXT_PLAIN)
                .body("PDF processing failed: " + exception.getMessage());
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Please upload a PDF file.");
        }
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType();
        boolean hasPdfExtension = filename != null && filename.toLowerCase().endsWith(".pdf");
        boolean hasPdfContentType = MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(contentType);
        if (!hasPdfExtension && !hasPdfContentType) {
            throw new IllegalArgumentException("Only PDF files are supported.");
        }
    }

    private String placeholderPart(String value) {
        return value == null ? "" : value;
    }

    private byte[] toZip(PdfImagePlaceholderService.Result result, String pdfFileName) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(pdfFileName));
            zip.write(result.getPdfBytes());
            zip.closeEntry();

            for (ExtractedImage image : result.getExtractedImages()) {
                zip.putNextEntry(new ZipEntry(image.getFilename()));
                zip.write(image.getBytes());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    private String pdfOutputFileName(String originalFilename) {
        String filename = originalFilename == null || originalFilename.trim().isEmpty()
                ? "converted.pdf"
                : originalFilename.trim();
        int extensionIndex = filename.toLowerCase().lastIndexOf(".pdf");
        if (extensionIndex > 0) {
            return filename.substring(0, extensionIndex) + "-replaced.pdf";
        }
        return filename + "-replaced.pdf";
    }

    private String zipOutputFileName(String originalFilename) {
        String filename = originalFilename == null || originalFilename.trim().isEmpty()
                ? "converted.pdf"
                : originalFilename.trim();
        int extensionIndex = filename.toLowerCase().lastIndexOf(".pdf");
        if (extensionIndex > 0) {
            return filename.substring(0, extensionIndex) + "-replaced.zip";
        }
        return filename + "-replaced.zip";
    }
}
