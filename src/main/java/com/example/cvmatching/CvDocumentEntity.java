package com.example.cvmatching;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "cv_documents")
public class CvDocumentEntity {
    @Id
    @Column(name = "cv_document_id", nullable = false)
    private UUID id;

    @Column(name = "file_name", columnDefinition = "text")
    private String fileName;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    protected CvDocumentEntity() { }

    CvDocumentEntity(CvDocumentMetadata metadata) {
        this.id = UUID.randomUUID();
        this.fileName = metadata.fileName();
        this.contentType = metadata.contentType();
        this.sizeBytes = metadata.sizeBytes();
        this.sha256 = metadata.sha256();
    }

    String fileName() {
        return fileName;
    }
}
