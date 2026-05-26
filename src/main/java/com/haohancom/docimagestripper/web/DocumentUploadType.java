package com.haohancom.docimagestripper.web;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

enum DocumentUploadType {
    PDF,
    MODERN_WORD,
    LEGACY_WORD;

    private static final Set<String> MODERN_WORD_EXTENSIONS = new HashSet<String>(
            Arrays.asList(".docx", ".docm", ".dotx", ".dotm"));
    private static final Set<String> LEGACY_WORD_EXTENSIONS = new HashSet<String>(
            Arrays.asList(".doc", ".dot", ".wbk", ".docb"));

    static DocumentUploadType fromFilename(String filename) {
        if (filename == null || filename.trim().isEmpty()) {
            throw new IllegalArgumentException("Please upload a PDF or Word file.");
        }
        String trimmed = filename.trim();
        String simpleName = simpleName(trimmed);
        if (simpleName.startsWith("~$")) {
            throw new IllegalArgumentException("Word temporary lock files cannot be processed.");
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".pdf")) {
            return PDF;
        }
        if (hasExtension(lower, MODERN_WORD_EXTENSIONS)) {
            return MODERN_WORD;
        }
        if (hasExtension(lower, LEGACY_WORD_EXTENSIONS)) {
            return LEGACY_WORD;
        }
        throw new IllegalArgumentException("Only PDF and Word files are supported.");
    }

    private static boolean hasExtension(String filename, Set<String> extensions) {
        for (String extension : extensions) {
            if (filename.endsWith(extension)) {
                return true;
            }
        }
        return false;
    }

    private static String simpleName(String filename) {
        int slash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        return slash >= 0 ? filename.substring(slash + 1) : filename;
    }
}
