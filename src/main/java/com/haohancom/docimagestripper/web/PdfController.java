package com.haohancom.docimagestripper.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.haohancom.docimagestripper.service.PdfImagePlaceholderService;
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
            produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> replaceImages(@RequestParam("file") MultipartFile file) throws IOException {
        validatePdf(file);

        byte[] processedPdf = service.replaceImages(file.getBytes());
        String outputFileName = outputFileName(file.getOriginalFilename());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(outputFileName, StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(processedPdf);
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

    private String outputFileName(String originalFilename) {
        String filename = originalFilename == null || originalFilename.trim().isEmpty()
                ? "converted.pdf"
                : originalFilename.trim();
        int extensionIndex = filename.toLowerCase().lastIndexOf(".pdf");
        if (extensionIndex > 0) {
            return filename.substring(0, extensionIndex) + "-replaced.pdf";
        }
        return filename + "-replaced.pdf";
    }
}
