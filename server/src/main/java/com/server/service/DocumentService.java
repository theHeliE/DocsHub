package com.server.service;

import com.server.exception.InvalidDocumentCodeException;
import com.server.model.Document;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DocumentService {
    private final Map<String, Document> documents = new ConcurrentHashMap<>(); // documentId -> document

    /**
     * Create a new document with the given name
     * @param name the name of the document
     * @return the created document
     */
    public Document createDocument(String name) {
        Document document = new Document(name);
        documents.put(document.getId(), document);
        return document;
    }

    /**
     * Get a document by its ID
     * @param id the document ID
     * @return an Optional containing the document if found
     */
    public Optional<Document> getDocumentById(String id) {
        return Optional.ofNullable(documents.get(id));
    }

    /**
     * Get a document by its editor code
     * @param editorCode the editor access code
     * @return an Optional containing the document if found
     */
    public Optional<Document> getDocumentByEditorCode(String editorCode) {
        return documents.values().stream()
                .filter(doc -> doc.getEditorCode().equals(editorCode))
                .findFirst();
    }

    /**
     * Get a document by its viewer code
     * @param viewerCode the viewer access code
     * @return an Optional containing the document if found
     */
    public Optional<Document> getDocumentByViewerCode(String viewerCode) {
        return documents.values().stream()
                .filter(doc -> doc.getViewerCode().equals(viewerCode))
                .findFirst();
    }

    /**
     * Find a document by code (either editor or viewer code)
     * @param code the document code to search for
     * @return the document if found
     * @throws InvalidDocumentCodeException if no document is found with the given code
     */
    public Document findDocumentByCode(String code) {
        // First try to find by editor code
        Optional<Document> document = getDocumentByEditorCode(code);
        if (document.isPresent()) {
            return document.get();
        }

        // If not found, try by viewer code
        document = getDocumentByViewerCode(code);
        if (document.isPresent()) {
            return document.get();
        }

        throw new InvalidDocumentCodeException("No document found with code '" + code + "'");
    }
}