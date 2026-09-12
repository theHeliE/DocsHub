package com.docshub.client;

import com.docshub.client.model.CRDT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.docshub.client.model.CrdtOperation;
import com.docshub.client.model.User;
import javafx.application.Platform;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.client.standard.WebSocketContainerFactoryBean;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;

import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/**
 * JavaFX WebSocket client controller for real-time document collaboration.
 * Works with the server's WebSocketController.
 */
public class SocketController {

    private static final Logger log = LoggerFactory.getLogger(SocketController.class);
    private String serverUrl;
    private WebSocketStompClient stompClient;
    private StompSession stompSession;
    private String documentId;
    private String documentName;
    private User user;
    Set<User> users = new HashSet<>();
    private boolean isConnected = false;

    // Callback handlers for various message types
    private Consumer<Set<User>> userListUpdateHandler;
    private Consumer<CrdtOperation> operationHandler;
    private Consumer<Map<String, Object>> cursorUpdateHandler;
    private Consumer<String> connectionErrorHandler;

    /**
     * Creates a new WebSocket client controller.
     *
     * @param serverUrl The URL of the WebSocket server (e.g., "ws://localhost:8080/ws")
     */
    public SocketController(String serverUrl) {
        this.serverUrl = serverUrl;
        this.setupStompClient();
    }

    /**
     * Sets up the STOMP client over WebSocket.
     */
    private void setupStompClient() {
        // Create WebSocket client with SockJS support
        WebSocketClient webSocketClient = new SockJsClient(
                List.of(new WebSocketTransport(new StandardWebSocketClient()))
        );
        stompClient = new WebSocketStompClient(webSocketClient);
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }


    /**
     * Connects to the WebSocket server.
     *
     * @return A CompletableFuture that completes when connection is established
     */
    public CompletableFuture<Boolean> connect() {
        CompletableFuture<Boolean> connectionFuture = new CompletableFuture<>();

        if (stompClient == null) {
            setupStompClient();
        }

        StompSessionHandler sessionHandler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                stompSession = session;
                isConnected = true;
                connectionFuture.complete(true);
            }

            @Override
            public void handleException(StompSession session, StompCommand command,
                                        StompHeaders headers, byte[] payload, Throwable exception) {
                isConnected = false;
                if (connectionErrorHandler != null) {
                    Platform.runLater(() -> connectionErrorHandler.accept(exception.getMessage()));
                }
                connectionFuture.completeExceptionally(exception);
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                isConnected = false;
                if (connectionErrorHandler != null) {
                    Platform.runLater(() -> connectionErrorHandler.accept(exception.getMessage()));
                }
                connectionFuture.completeExceptionally(exception);
            }
        };

        stompClient.connect(serverUrl, sessionHandler);
        return connectionFuture;
    }

    /**
     * Joins a document using a document code.
     *
     * @param documentCode The editor or viewer code for the document
     * @return A CompletableFuture that completes with the join response
     */
    public CompletableFuture<Map<String, Object>> joinDocument(String documentCode) {
        if (!isConnected) {
            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Not connected to server"));
            return future;
        }

        CompletableFuture<Map<String, Object>> joinFuture = new CompletableFuture<>();

        // Subscribe to receive the join response
        stompSession.subscribe("/user/queue/join", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @SuppressWarnings("unchecked")
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                Map<String, Object> response = (Map<String, Object>) payload;

                if (response.containsKey("error")) {
                    joinFuture.completeExceptionally(
                            new RuntimeException((String) response.get("error")));
                    return;
                }

                documentId = (String) response.get("documentId");
                documentName = (String) response.get("documentName");
                user = new User((String) response.get("userId"), (String) response.get("userColor"), (Boolean) response.get("isEditor"));
                // Subscribe to document updates after successful join
                subscribeToDocumentUpdates();


                joinFuture.complete(response);
            }
        });

        // Send join request with document code
        stompSession.send("/app/join", documentCode);

        return joinFuture;
    }

    /**
     * Subscribes to all relevant document update topics.
     */
    public void subscribeToDocumentUpdates() {
        // Subscribe to user list updates
        stompSession.subscribe("/topic/document/" + documentId + "/users", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return List.class;
            }

            @SuppressWarnings("unchecked")
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {

                if (userListUpdateHandler != null) {
                    try {
                        // The payload is a List of Maps rather than List of Users
                        List<Map<String, Object>> userMaps = (List<Map<String, Object>>) payload;

                        // Create a new Set to hold the updated users
                        Set<User> updatedUsers = new HashSet<>();  // Use a new temporary collection

                        // Convert Maps to User objects manually
                        for (Map<String, Object> userMap : userMaps) {
                            String id = (String) userMap.get("id");
                            String color = (String) userMap.get("color");
                            Boolean editor = (Boolean) userMap.get("editor");

                            // Use the regular constructor
                            User user = new User(id, color, editor);

                            // Set cursor position if it exists
                            if (userMap.containsKey("cursorPosition")) {
                                Object cursorPos = userMap.get("cursorPosition");
                                if (cursorPos instanceof Number) {
                                    user.setCursorPosition(((Number) cursorPos).intValue());
                                }
                            }

                            updatedUsers.add(user);  // Add to the temporary collection
                        }

                        Platform.runLater(() -> {
                            // Pass the NEW collection to the handler
                            userListUpdateHandler.accept(updatedUsers);
                        });
                    } catch (Exception e) {
                        log.error("Could not process the user list", e);
                    }
                }
            }
        });

        // Subscribe to CRDT operations
        stompSession.subscribe("/topic/document/" + documentId + "/operation", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                // Change to receive as a Map instead of CrdtOperation
                return Map.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {

                if (operationHandler != null && payload instanceof Map) {
                    try {
                        // Manually convert the Map to CrdtOperation
                        Map<String, Object> map = (Map<String, Object>) payload;

                        // Extract fields
                        String type = (String) map.get("type");
                        String userId = (String) map.get("userId");
                        String clock = (String) map.get("clock");

                        List<String> nodeIdList = (List<String>) map.get("nodeId");
                        String[] nodeId = nodeIdList.toArray(new String[0]);

                        String parentId = (String) map.get("parentId");
                        String value = (String) map.get("value");


                        // Handle the value field (might be null or need conversion)

                        // Create the CrdtOperation
                        CrdtOperation operation = new CrdtOperation(
                                type, userId, clock, nodeId, parentId, value
                        );

                        // Call the handler with the created operation
                        operationHandler.accept(operation);
                    } catch (Exception e) {
                        log.error("Could not deserialise an incoming operation", e);
                    }
                }
            }
        });


        // Subscribe to cursor updates
        stompSession.subscribe("/topic/document/" + documentId + "/cursor", new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @SuppressWarnings("unchecked")
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                if (cursorUpdateHandler != null) {
                    Map<String, Object> cursorUpdate = (Map<String, Object>) payload;
                    Platform.runLater(() -> cursorUpdateHandler.accept(cursorUpdate));
                }
            }
        });
    }


    /**
     * Requests the list of users in the document.
     *
     * @return A CompletableFuture that completes with the user list
     */
    public CompletableFuture<Set<User>> getDocumentUsers() {
        if (!isConnected || documentId == null) {
            CompletableFuture<Set<User>> future = new CompletableFuture<>();
            future.completeExceptionally(new IllegalStateException("Not connected or no document joined"));
            return future;
        }

        CompletableFuture<Set<User>> usersFuture = new CompletableFuture<>();

        // Clear the existing users set to avoid duplicates
        users.clear();

        // Subscribe to receive the users response
        stompSession.subscribe("/user/queue/users", new StompFrameHandler() {
            public Type getPayloadType(StompHeaders headers) {
                return List.class;
            }

            @SuppressWarnings("unchecked")
            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    List<Map<String, Object>> userMaps = (List<Map<String, Object>>) payload;

                    for (Map<String, Object> userMap : userMaps) {
                        String id = (String) userMap.get("id");
                        String color = (String) userMap.get("color");
                        Boolean editor = (Boolean) userMap.get("editor");

                        User user = new User(id, color, editor);

                        if (userMap.containsKey("cursorPosition")) {
                            Object cursorPos = userMap.get("cursorPosition");
                            if (cursorPos instanceof Number) {
                                user.setCursorPosition(((Number) cursorPos).intValue());
                            }
                        }

                        users.add(user);
                    }

                    // Complete the future with the users set
                    usersFuture.complete(new HashSet<>(users));

                    // Call the update handler if it exists
                    if (userListUpdateHandler != null) {
                        Platform.runLater(() -> userListUpdateHandler.accept(users));
                    }
                } catch (Exception e) {
                    log.error("Could not process the user list", e);
                    usersFuture.completeExceptionally(e);
                }
            }
        });

        // Send request for document users
        stompSession.send("/app/document/" + documentId + "/users", null);

        return usersFuture;
    }

    /**
     * Sends a CRDT operation (insert, delete or undoDelete) to the server.
     */
    public void sendOperation(CrdtOperation operation) {
        if (!isConnected || documentId == null || !user.isEditor()) {
            return; // Can't send operations if not connected, not in a document, or not an editor
        }

        // Convert to Map before sending
        Map<String, Object> operationMap = new HashMap<>();
        operationMap.put("type", operation.type());
        operationMap.put("userId", operation.userId());
        operationMap.put("clock", operation.clock());

        // Include the delete/undoDelete targets, or the ids an insert created
        if (operation.nodeId() != null) {
            operationMap.put("nodeId", operation.nodeId());
        }

        // Only include parentId and value for insert operations
        if (operation.parentId() != null) {
            operationMap.put("parentId", operation.parentId());
        }
        if (operation.value() != null) {
            operationMap.put("value", operation.value());
        }

        log.debug("Sent {} operation to document {}", operation.type(), documentId);
        stompSession.send("/app/document/" + documentId + "/operation", operationMap);
    }

    /**
     * Sends a cursor position update to the server.
     *
     * @param position The cursor position (can be any object structure as needed)
     */
    public void sendCursorUpdate(Object position) {
        if (!isConnected || documentId == null) {
            return; // Can't send updates if not connected or not in a document
        }

        Map<String, Object> cursorUpdate = new HashMap<>();
        cursorUpdate.put("userId", user.getId());
        cursorUpdate.put("position", position);

        log.trace("Sent cursor update for document {}", documentId);
        stompSession.send("/app/document/" + documentId + "/cursor", cursorUpdate);
    }

    /**
     * Leaves the current document.
     */
    public void leaveDocument() {
        if (!isConnected || documentId == null) {
            return; // Not connected or not in a document
        }

        Map<String, Object> leaveRequest = new HashMap<>();
        leaveRequest.put("documentId", documentId);
        leaveRequest.put("userId", user.getId());

        stompSession.send("/app/leave", leaveRequest);

        // Reset document state
    }

    /**
     * Disconnects from the WebSocket server.
     */
    public void disconnect() {
        if (isConnected) {
            if (documentId != null) {
                leaveDocument();
            }

            stompSession.disconnect();
            stompSession = null;
            isConnected = false;
        }
    }

    // Setter methods for callback handlers

    /**
     * Sets a handler for user list updates.
     *
     * @param handler Consumer that receives the updated user list
     */
    public void setUserListUpdateHandler(Consumer<Set<User>> handler) {
        this.userListUpdateHandler = handler;
    }

    /**
     * Sets a handler for CRDT operations.
     *
     * @param handler Consumer that receives operation details
     */
    public void setOperationHandler(Consumer<CrdtOperation> handler) {
        this.operationHandler = handler;
    }


    /**
     * Sets a handler for cursor updates.
     *
     * @param handler Consumer that receives cursor update details
     */
    public void setCursorUpdateHandler(Consumer<Map<String, Object>> handler) {
        this.cursorUpdateHandler = handler;
    }

    /**
     * Sets a handler for connection errors.
     *
     * @param handler Consumer that receives error messages
     */
    public void setConnectionErrorHandler(Consumer<String> handler) {
        this.connectionErrorHandler = handler;
    }

    // Getter methods for client state

    public boolean isConnected() {
        return isConnected;
    }

    public String getDocumentId() {
        return documentId;
    }
    public String getDocumentName(){
        return documentName;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public String getUserId() {
        return user.getId();
    }

    public boolean isEditor() {
        return user.isEditor();
    }

    public String getUserColor() {
        return user.getColor();
    }

    public User getUser() {
        return user;
    }
    public void setUser(User user) {
        this.user = user;
    }

    public Set<User> getUsers() {
        return users;
    }
}