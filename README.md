# Doc Image Stripper

Doc Image Stripper is a Java 8 Spring Boot service for removing images from documents while keeping the original text layout, fonts, font sizes, and formatting as intact as possible.

## Goal

- Strip images from PDF and Word documents.
- Preserve non-image content without rebuilding the document layout.
- Return a processed document that can be downloaded directly from a simple web page.

## Current Status

- PDF upload and processing is implemented.
- PDF images are replaced with numbered placeholders such as `[image1]`, `[image2]`.
- The PDF processor handles normal image XObjects, nested Form XObjects, and inline images.
- Word document support is part of the project goal and can be added next.

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
