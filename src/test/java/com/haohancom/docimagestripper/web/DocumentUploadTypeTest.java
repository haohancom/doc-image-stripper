package com.haohancom.docimagestripper.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DocumentUploadTypeTest {

    @Test
    void classifiesPdfAndModernWordExtensionsCaseInsensitively() {
        assertThat(DocumentUploadType.fromFilename("sample.PDF")).isEqualTo(DocumentUploadType.PDF);
        assertThat(DocumentUploadType.fromFilename("sample.docx")).isEqualTo(DocumentUploadType.MODERN_WORD);
        assertThat(DocumentUploadType.fromFilename("sample.docm")).isEqualTo(DocumentUploadType.MODERN_WORD);
        assertThat(DocumentUploadType.fromFilename("sample.dotx")).isEqualTo(DocumentUploadType.MODERN_WORD);
        assertThat(DocumentUploadType.fromFilename("sample.dotm")).isEqualTo(DocumentUploadType.MODERN_WORD);
    }

    @Test
    void recognizesLegacyWordExtensionsWithoutMarkingThemModern() {
        assertThat(DocumentUploadType.fromFilename("sample.doc")).isEqualTo(DocumentUploadType.LEGACY_WORD);
        assertThat(DocumentUploadType.fromFilename("sample.dot")).isEqualTo(DocumentUploadType.LEGACY_WORD);
        assertThat(DocumentUploadType.fromFilename("sample.wbk")).isEqualTo(DocumentUploadType.LEGACY_WORD);
        assertThat(DocumentUploadType.fromFilename("sample.docb")).isEqualTo(DocumentUploadType.LEGACY_WORD);
    }

    @Test
    void rejectsMissingUnsupportedAndTemporaryFilenames() {
        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                DocumentUploadType.fromFilename(null);
            }
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Please upload a PDF or Word file.");

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                DocumentUploadType.fromFilename("sample.txt");
            }
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Only PDF and Word files are supported.");

        assertThatThrownBy(new org.assertj.core.api.ThrowableAssert.ThrowingCallable() {
            @Override
            public void call() {
                DocumentUploadType.fromFilename("~$sample.docx");
            }
        }).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Word temporary lock files cannot be processed.");
    }
}
