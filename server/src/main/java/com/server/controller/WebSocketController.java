package com.server.controller;

import com.server.dto.CrdtOperation;
import com.server.dto.CursorUpdateRequest;
import com.server.dto.LeaveDocumentRequest;
import com.server.exception.InvalidDocumentCodeException;
import com.server.model.Document;
import com.server.model.User;
import com.server.service.DocumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.util.*;

/**
 * Controller that handles WebSocket communications for real-time document collaboration.
 */
@Controller
public class WebSocketController {

    private static final Logger log = LoggerFactory.getLogger(WebSocketController.class);

    private final DocumentService documentService;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketController(DocumentService documentService, SimpMessagingTemplate messagingTemplate) {
        this.documentService = documentService;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Handles a user joining a document using a document code.
     * 
     * @param code The document code used for authentication (editor or viewer code)
     * @return Map containing document ID and user information, or error if code is invalid
     */
    @MessageMapping("/join")
    @SendToUser("/queue/join")
    public Map<String, Object> joinDocument(@Payload String code) {
        try {
            // Find document by code and determine if user is editor or viewer
            Document document = documentService.findDocumentByCode(code);
            boolean isEditor = document.isEditor(code);
            
            // Create a new user and add to document
            User user = document.createNewUser(isEditor);
            
            // Prepare response with documentId and user info
            Map<String, Object> response = new HashMap<>();
            response.put("documentId", document.getId());
            response.put("documentName", document.getName());

            response.put("userId", user.getId());
            response.put("userColor", user.getColor());
            response.put("isEditor", isEditor);
            
            log.info("User {} joined document {} as {}", user.getId(), document.getId(),
                    isEditor ? "editor" : "viewer");

            // Notify other users that someone joined
            notifyUserListUpdate(document);
            
            return response;
        } catch (InvalidDocumentCodeException e) {
            // Deliberately not logging the code itself, valid or not
            log.warn("Rejected a join attempt with an unrecognised document code");
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return errorResponse;
        }
    }

    /**
     * Retrieves the list of users currently in a document.
     *
     * @param documentId The unique identifier of the document
     * @return The users currently in the document, or null if no such document exists
     */
    @MessageMapping("/document/{documentId}/users")
    @SendToUser("/queue/users")
    public Set<User> getDocumentUsers(@DestinationVariable String documentId) {
        Optional<Document> documentOpt = documentService.getDocumentById(documentId);

        if (documentOpt.isPresent()) {
            Document document = documentOpt.get();
            return document.getUsers();
        } else {
            log.warn("User list requested for unknown document {}", documentId);
            return null;
        }
    }

    /**
     * Handles a CRDT operation (insert, delete or undoDelete) and broadcasts it to all users.
     *
     * @param documentId The unique identifier of the document
     * @param operation  The CRDT operation containing type, userId, and other operation-specific data
     */
    @MessageMapping("/document/{documentId}/operation")
    public void handleCrdtOperation(
            @DestinationVariable String documentId,
            @Payload CrdtOperation operation
    ) {

        Optional<Document> documentOpt = documentService.getDocumentById(documentId);

        if (documentOpt.isPresent()) {
            Document document = documentOpt.get();

            // Check if user is an editor before allowing modifications
            boolean isEditor = document.getUsers().stream()
                    .filter(user -> user.getId().equals(operation.userId()))
                    .findFirst()
                    .map(User::isEditor)
                    .orElse(false);

            if (!isEditor) {
                log.warn("Rejected {} operation from non-editor user {} on document {}",
                        operation.type(), operation.userId(), documentId);
                return;
            }

            boolean success = false;

            // Apply the operation based on its type
            if ("insert".equals(operation.type())) {
                    success = document.getCrdt().insertCharacter(
                            operation.userId(),
                            operation.clock(),
                            operation.value(),
                            operation.parentId());
            } else if ("delete".equals(operation.type())) {
                for (String nodeId : operation.nodeId()){
                    success |= document.getCrdt().deleteCharacterById(nodeId);
                }
            }
            else if ("undoDelete".equals(operation.type())) {
                for(String nodeId : operation.nodeId()){
                    success |= document.getCrdt().getNodes().get(nodeId).setDeleted(false);
                }
            }

            if (success) {
                // Content is never logged, only which kind of operation was applied
                log.debug("Applied {} operation from user {} on document {}",
                        operation.type(), operation.userId(), documentId);

                // Broadcast the operation to all users in the document
                messagingTemplate.convertAndSend(
                        "/topic/document/" + documentId + "/operation",
                        operation
                );
            }
        }
    }
    
    /**
     * Handles cursor position updates and broadcasts them to all users.
     * 
     * @param documentId The unique identifier of the document
     * @param request The cursor update request containing userId and position data
     */
    @MessageMapping("/document/{documentId}/cursor")
    public void handleCursorUpdate(
            @DestinationVariable String documentId,
            @Payload CursorUpdateRequest request) {
        
        Optional<Document> documentOpt = documentService.getDocumentById(documentId);

        if (documentOpt.isPresent()) {
            Document document = documentOpt.get();
            
            // Find the user and update their cursor position
            document.getUsers().stream()
                    .filter(user -> user.getId().equals(request.userId()))
                    .findFirst()
                    .ifPresent(user -> {
                        log.trace("Cursor update from user {} on document {}", request.userId(), documentId);
                        user.setCursorPosition(request.position());
                        
                        // Broadcast the cursor update to all users in the document
                        messagingTemplate.convertAndSend(
                                "/topic/document/" + documentId + "/cursor",
                                request
                        );
                    });
        }
    }

    /**
     * Handles a user leaving a document and notifies other users.
     * 
     * @param request The leave document request containing documentId and userId
     */
    @MessageMapping("/leave")
    public void leaveDocument(@Payload LeaveDocumentRequest request) {
        Optional<Document> documentOpt = documentService.getDocumentById(request.documentId());

        documentOpt.ifPresent(document -> {
            if (document.removeUser(request.userId())) {
                log.info("User {} left document {}", request.userId(), request.documentId());
                // Notify other users that someone left
                notifyUserListUpdate(document);
            }
        });
    }

    /**
     * Notifies all connected clients about a user list update in a document.
     * 
     * @param document The document whose user list has been updated
     */
    private void notifyUserListUpdate(Document document) {
        messagingTemplate.convertAndSend(
                "/topic/document/" + document.getId() + "/users",
                document.getUsers()
        );
    }
}