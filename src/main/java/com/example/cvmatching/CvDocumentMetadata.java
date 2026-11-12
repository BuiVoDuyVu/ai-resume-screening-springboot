package com.example.cvmatching;

public record CvDocumentMetadata(String fileName, String contentType, long sizeBytes, String sha256) { }
