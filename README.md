# Doc Image Stripper

Doc Image Stripper is a Java 8 Spring Boot service for removing images from documents while keeping the original text layout, fonts, font sizes, and formatting as intact as possible.

## Goal

- Strip images from PDF and Word documents.
- Preserve non-image content without rebuilding the document layout.
- Return a ZIP archive that can be downloaded directly from a simple web page.

## Current Status

- PDF upload and processing is implemented.
- Modern Word formats `.docx`, `.docm`, `.dotx`, and `.dotm` are processed with the same image placeholder flow.
- Legacy Word formats `.doc`, `.dot`, `.wbk`, and `.docb` are recognized and return guidance to save as `.docx`.
- PDF images are replaced with numbered placeholders such as `image1`, `image2`.
- Placeholder left and right text can be customized with `placeholderPrefix` and `placeholderSuffix`.
- Processed downloads are ZIP archives containing the replaced document and extracted images named `image1.png`, `image2.png`, and so on.
- The PDF processor handles normal image XObjects, nested Form XObjects, and inline images.

## Run Locally

```bash
./start.sh
```

Then open:

```text
http://localhost:8080/
```

To use another port:

```bash
PORT=8081 ./start.sh
```

## Test

```bash
mvn test
```

## Build Windows Package

```bash
./package-windows.sh
```

The package is written to:

```text
dist/doc-image-stripper-windows.zip
```
