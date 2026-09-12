package com.docshub.client;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListCell;
import com.jfoenix.controls.JFXListView;
import com.jfoenix.controls.JFXTextArea;
import com.docshub.client.model.CRDT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.docshub.client.model.CharacterNode;
import com.docshub.client.model.User;
import com.docshub.client.model.CrdtOperation;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.control.Tooltip;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

public class DocumentController {

    private static final Logger log = LoggerFactory.getLogger(DocumentController.class);
    @FXML private TextArea textArea;
    @FXML private JFXListView<User> userList;
    @FXML private AnchorPane rootPane;
    @FXML private JFXTextArea editorCode;
    @FXML private JFXTextArea viewerCode;
    @FXML private JFXButton copyEditorCode;
    @FXML private JFXButton copyViewerCode;
    @FXML private AnchorPane creator;

    private boolean isCreator = false;


    private double xOffset = 0;
    private double yOffset = 0;

    private String documentCode;
    private String documentName;
    private User me;
    private Set<User> users = new HashSet<>();
    private CRDT crdt;

    SocketController wsClient = new SocketController(Config.webSocketUrl());

    private final String documentApiUrl = Config.documentApiUrl();

    Stack<CrdtOperation> undoStack = new Stack<>();
    Stack<CrdtOperation> redoStack = new Stack<>();

    private String editorCodeText;
    private String viewerCodeText;

    public void initialize() {

        rootPane.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        rootPane.setOnMouseDragged(event -> {
            rootPane.getScene().getWindow().setX(event.getScreenX() - xOffset);
            rootPane.getScene().getWindow().setY(event.getScreenY() - yOffset);
        });
    }


    public void joinDocument(String documentCode) {
        this.documentCode = documentCode;

        wsClient.setConnectionErrorHandler(error -> {
            Platform.runLater(() -> {
                log.error("WebSocket connection error: {}", error);

                // Add reconnection logic here
                if (error.contains("Connection closed")) {
                    Alert alert = new Alert(Alert.AlertType.WARNING);
                    alert.setTitle("Connection Issue");
                    alert.setHeaderText("Connection Lost");
                    alert.setContentText("Connection to the document server was lost. " +
                            "This may happen if too many users are connected simultaneously. " +
                            "The application will attempt to reconnect.");
                    alert.show();

                    // Optional: Implement automatic reconnection attempts
                    // with exponential backoff
                }
            });
        });

        wsClient.connect().thenAccept(connected -> {
            if (connected) {

                handleOperation(wsClient);
                handleCursorChange(wsClient);

                UpdateUserList();
                cursorUpdate();

                wsClient.joinDocument(documentCode).thenAccept(response -> {
                    if (response.containsKey("error")) {
                        return;
                    }

                    fetchDocument(wsClient.getDocumentId());
                    me = wsClient.getUser();
                    creatorPanel();
                    documentName = (String) wsClient.getDocumentName();

                    if (!me.isEditor()){
                        textArea.setEditable(false);
                    }
                    editorPanel();

                    getDocumentUsers();

                }).exceptionally(ex -> {
                    Platform.runLater(() -> {
                        log.error("Could not join the document", ex);
                        showErrorAndClose("Error", "Document Not Found",
                                "No document found with code '" + documentCode + "'", (Stage) rootPane.getScene().getWindow());
                    });
                    return null;
                });

            }
        }).exceptionally(ex -> {
            Platform.runLater(() -> {
                log.error("Could not connect to the server", ex);
            });
            return null;
        });
    }

    public void createDocument(String name) {
        createDocument(name, "");
    }

    public void createDocument(String name, String content) {
        RestTemplate restTemplate = new RestTemplate();

        // Create a Map instead of ArrayList to properly serialize to a JSON object
        Map<String, String> requestData = new HashMap<>();
        requestData.put("name", name);
        // Only add content if it's not null
        if (content != null) {
            requestData.put("content", content);
        }

        // Configure RestTemplate to handle JSON properly
        restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());

        try {
            Map<String, Object> response = restTemplate.postForObject(documentApiUrl + "create", requestData, Map.class);
            if (response!=null) {
                wsClient.setConnectionErrorHandler(error -> {
                    Platform.runLater(() -> {
                        log.error("WebSocket connection error: {}", error);

                        // Add reconnection logic here
                        if (error.contains("Connection closed")) {
                            Alert alert = new Alert(Alert.AlertType.WARNING);
                            alert.setTitle("Connection Issue");
                            alert.setHeaderText("Connection Lost");
                            alert.setContentText("Connection to the document server was lost. " +
                                    "This may happen if too many users are connected simultaneously. " +
                                    "The application will attempt to reconnect.");
                            alert.show();

                            // Optional: Implement automatic reconnection attempts
                            // with exponential backoff
                        }
                    });
                });

                wsClient.connect().thenAccept(connected -> {
                    if (connected) {

                        wsClient.setDocumentId((String) response.get("documentId"));
                        editorCodeText = (String) response.get("editorCode");
                        editorCode.setText(editorCodeText);

                        viewerCodeText = (String) response.get("viewerCode");
                        viewerCode.setText(viewerCodeText);

                        me = new User( (String) response.get("userId"), (String) response.get("userColor"), true);

                        crdt = CRDT.fromSerialized((List<Map<String,Object>>) response.get("crdt"));
                        Platform.runLater(() -> {setFileContent(crdt.getText());});

                        documentName = (String) response.get("documentName");

                        wsClient.setUser(me);
                        creatorPanel();
                        wsClient.subscribeToDocumentUpdates();

                        handleOperation(wsClient);
                        handleCursorChange(wsClient);
                        cursorUpdate();

                        UpdateUserList();
                        getDocumentUsers();

                    }
                }).exceptionally(ex -> {
                    Platform.runLater(() -> {
                        log.error("Could not connect to the server", ex);
                    });
                    return null;
                });

            } else {
                Platform.runLater(() -> showErrorAndClose("Error", "Document Creation Failed",
                        "Could not create document: " + response, (Stage) rootPane.getScene().getWindow()));
            }
        } catch (Exception e) {
            Platform.runLater(() -> showErrorAndClose("Error", "Document Creation Failed",
                    "Error: " + e.getMessage(), (Stage) rootPane.getScene().getWindow()));
        }
    }

    private void showErrorAndClose(String title, String header, String content, Stage stageToClose) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.setOnHidden(evt -> stageToClose.close());
        alert.showAndWait();
    }
    private boolean ignoreTextChanges = false;


    public void setFileContent(String content) {
        ignoreTextChanges = true;

        // Update the text area with the content
        textArea.setText(content);

        // Position caret at the beginning or end as needed
        textArea.positionCaret(0);

        // Re-enable text change processing AFTER the update
        ignoreTextChanges = false;


    }

    private void UpdateUserList(){
        wsClient.setUserListUpdateHandler(updatedUsers -> {
            Platform.runLater(() -> {
                Set<User> updatedUserSet = new HashSet<>(updatedUsers);
                if (!updatedUserSet.equals(users)) {
                    users.clear();
                    users.addAll(updatedUserSet);
                    populateListView();
                    updateUsersAndCleanCursors(updatedUsers);
                }
            });
        });
    }

    private void updateUsersAndCleanCursors(Set<User> updatedUsers) {
        // Find users who are no longer in the updated list
        Set<String> updatedUserIds = updatedUsers.stream()
                .map(User::getId)
                .collect(Collectors.toSet());

        // Get current user IDs excluding the updated ones
        Set<String> removedUserIds = users.stream()
                .map(User::getId)
                .filter(id -> !updatedUserIds.contains(id))
                .collect(Collectors.toSet());

        // Remove cursors for users who left
        for (String userId : removedUserIds) {
            removeUserCursor(userId);
        }

        // Update the users set with the new list
        users.clear();
        users.addAll(updatedUsers);

        // Update UI list
        UpdateUserList();
    }


    private void getDocumentUsers() {
        wsClient.getDocumentUsers().thenAccept(userSet -> {
            Platform.runLater(() -> {
                users.clear();
                users.addAll(userSet);
                populateListView();
            });
        }).exceptionally(ex -> {
            log.error("Could not retrieve the user list", ex);
            return null;
        });
    }

    private void populateListView() {
        ObservableList<User> userListItems = FXCollections.observableArrayList(
                users != null ? users : Set.of()
        );

        if (userListItems.isEmpty()) {
            userListItems.add(new User("No users available", "#808080", false));
        }

        userList.setItems(userListItems);

        userList.setCellFactory(listView -> new JFXListCell<>() {
            @Override
            protected void updateItem(User user, boolean empty) {
                super.updateItem(user, empty);
                if (empty || user == null) {
                    setText(null);
                    setStyle(null);
                } else {
                    String label = user.equals(me) ? "User: " + user.getId() + " (You)" : "User: " + user.getId();
                    setText(label);
                    try {
                        setTextFill(Color.web(user.getColor()));
                    } catch (IllegalArgumentException e) {
                        setTextFill(Color.BLACK);
                    }
                    setStyle("-fx-font-size: 13px; -fx-effect: dropshadow(one-pass-box, black, 2, 0.0, 0, 0);");
                }
            }
        });

        userList.setFocusTraversable(false);
    }


    public void fetchDocument(String documentId) {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.getMessageConverters().add(new MappingJackson2HttpMessageConverter());
        try {
            List<Map<String, Object>> response = restTemplate.getForObject(documentApiUrl + documentId, List.class);
            if (response!=null) {
                crdt = CRDT.fromSerialized(response);
                Platform.runLater(() -> {setFileContent(crdt.getText());});
            }
        }
        catch (Exception e) {
            Platform.runLater(() -> showErrorAndClose("Error", "Retrieval Failed",
                    "Error retrieving document: " + e.getMessage(), (Stage) rootPane.getScene().getWindow()));
            log.error("Could not retrieve the document from the server", e);
        }


    }

    public void handleOperation(SocketController wsClient) {
        // Handle incoming operations from other users

        wsClient.setOperationHandler(operation -> {
            Platform.runLater(() -> {
                // Skip operations initiated by the current user
                if (me != null && operation.userId().equals(me.getId())) {
                    return;
                }

                try {
                    // Apply the remote operation to the local CRDT
                    if (operation.type().equals("insert")) {
                        crdt.insertCharacter(operation.userId(), operation.clock(), operation.value(), operation.parentId());
                    } else if (operation.type().equals("delete")) {
                        for (String nodeId : operation.nodeId()){
                            crdt.deleteCharacterById(nodeId);
                        }
                    }
                    else if (operation.type().equals("undoDelete")) {
                        for(String nodeId : operation.nodeId()){
                            crdt.getNodeMap().get(nodeId).setDeleted(false);
                        }
                    }
                    log.debug("Applied remote {} operation from user {}", operation.type(),
                            operation.userId());

                    // Set flag to ignore changes while we update the text area
                    ignoreTextChanges = true;
                    int caretPosition = textArea.getCaretPosition();
                    textArea.setText(crdt.getText());

                    try {
                        textArea.positionCaret(Math.min(caretPosition, textArea.getText().length()));
                    } catch (Exception e) {
                        log.warn("Could not reposition the caret", e);
                    }

                    // Re-enable text change processing
                    ignoreTextChanges = false;
                } catch (Exception e) {
                    log.error("Could not apply a remote operation", e);
                }
            });
        });

        // Set up local text change listener
        // Set up local text change listener
        textArea.textProperty().addListener((observable, oldValue, newValue) -> {
            if (ignoreTextChanges || crdt == null || me == null) return;

            // Calculate the change
            if (oldValue.length() < newValue.length()) {
                int caretPos = textArea.getCaretPosition();
                // This listener runs before the caret advances, so caretPos is still the
                // start of the inserted text

                int insertPos = caretPos;
                String insertedString = newValue.substring(insertPos, insertPos + newValue.length() - oldValue.length());


                // Generate a timestamp for this operation
                String clock = generateClock();

                // Find the parent node ID based on insertion position
                String parentId;
                if (insertPos == 0) {
                    // If inserting at the beginning, use the root node as parent
                    parentId = "0:0";
                } else {
                    // Otherwise, get the node at the position before the insertion
                    List<CharacterNode> nodes = crdt.getInOrderTraversal();
                    // Get the node that is BEFORE the insertion position
                    int parentIndex = insertPos - 1;
                    if (parentIndex >= 0 && parentIndex < nodes.size()) {
                        parentId = nodes.get(parentIndex).getId();
                    } else {
                        // Fallback to root if no valid parent found
                        parentId = "0:0";
                    }
                }

            // Apply to local CRDT first
            String[] nodeIds = crdt.insertCharacterAt(me.getId(), insertPos, insertedString, clock);
            List<CharacterNode> nodes = crdt.getInOrderTraversal();
                // Send operation to server
                CrdtOperation operation = new CrdtOperation(
                        "insert",
                        me.getId(),
                        clock,
                        nodeIds,
                        parentId,
                        insertedString
                );

                undoStack.push(operation);
                redoStack.clear();

                wsClient.sendOperation(operation);

            } else if (oldValue.length() > newValue.length()) {
                int caretPos = textArea.getCaretPosition();
                // Backspace: the caret has not moved back yet, so the removed character
                // sits one position before it
                int deletePos = caretPos - 1;

                // Use deleteCharacter method which takes a position

                String[] deletedNodeId = {crdt.deleteCharacter(deletePos)};

                // Send operation to server
                CrdtOperation operation = new CrdtOperation(
                        "delete",
                        me.getId(),
                        generateClock(),
                        deletedNodeId,
                        null,  // parentId not needed for delete
                        null   // value not needed for delete
                );
                undoStack.push(operation);
                redoStack.clear();

                wsClient.sendOperation(operation);
            }
        });
    }

    private void handleCursorChange(SocketController wsClient) {
        // Create a timer for debouncing
        AtomicReference<Timer> cursorUpdateTimer = new AtomicReference<>(new Timer());

        textArea.caretPositionProperty().addListener((observable, oldValue, newValue) -> {
            // Cancel any pending update
            cursorUpdateTimer.get().cancel();
            cursorUpdateTimer.get().purge();

            // Create a new timer
            cursorUpdateTimer.set(new Timer());

            // Schedule update after a short delay (e.g., 50ms)
            cursorUpdateTimer.get().schedule(new TimerTask() {
                @Override
                public void run() {
                    Platform.runLater(() -> {
                        wsClient.sendCursorUpdate(newValue);
                    });
                }
            }, 50);
        });
    }

    @FXML
    private AnchorPane cursorOverlay;


    private void displayUserCursor(User user, int position) {
        String markerId = "cursor-" + user.getId();

        try {
            // Ensure the overlay exists
            ensureCursorOverlayExists();

            // Get the current text
            String text = textArea.getText();
            int safePos = Math.min(position, text.length());

            // Calculate text position data
            TextPositionInfo posInfo = calculateTextPosition(text, safePos);

            // Create a new cursor indicator
            Rectangle cursorMarker = new Rectangle(2, textArea.getFont().getSize());
            cursorMarker.setId(markerId);
            cursorMarker.setFill(Color.web(user.getColor()));

            // Create a tooltip with the user's ID/name
            Tooltip tooltip = new Tooltip(user.getId());
            Tooltip.install(cursorMarker, tooltip);

            // Remove existing cursor for this user if it exists
            Node existingCursor = userCursors.get(user.getId());
            if (existingCursor != null) {
                cursorOverlay.getChildren().remove(existingCursor);
            }

            // Add the new cursor to the overlay and track it
            cursorOverlay.getChildren().add(cursorMarker);
            userCursors.put(user.getId(), cursorMarker);

            // Position the cursor using our precise calculation
            positionCursorMarker(cursorMarker, posInfo);

        } catch (Exception e) {
            log.error("Could not draw a remote cursor", e);
        }
    }


    // Helper class to store text position information
    private static class TextPositionInfo {
        int row;
        int column;
        String lineText;
        double xOffset;
        double yOffset;
    }

    // Calculate precise text position data
    private TextPositionInfo calculateTextPosition(String text, int position) {
        TextPositionInfo info = new TextPositionInfo();
        info.row = 0;
        info.column = 0;

        // Handle empty text case
        if (text.isEmpty()) {
            info.lineText = "";
            return info;
        }

        // Find the row and column
        int currentLineStart = 0;
        for (int i = 0; i < position; i++) {
            if (i < text.length() && text.charAt(i) == '\n') {
                info.row++;
                currentLineStart = i + 1;
                info.column = 0;
            } else {
                info.column++;
            }
        }

        // Extract the current line text
        int lineEnd = text.indexOf('\n', currentLineStart);
        if (lineEnd == -1) {
            lineEnd = text.length();
        }

        // Get the text of the current line up to the cursor position
        int cursorColumnEnd = Math.min(currentLineStart + info.column, text.length());
        String lineTextUpToCursor = text.substring(currentLineStart, cursorColumnEnd);
        info.lineText = lineTextUpToCursor;

        return info;
    }

    // Position the cursor marker using TextFlow for precise measurement
    private void positionCursorMarker(Rectangle cursorMarker, TextPositionInfo posInfo) {
        // Create a temporary Text node with the line text up to the cursor
        Text measuringText = new Text(posInfo.lineText);
        measuringText.setFont(textArea.getFont());

        // Get the Text layout bounds to determine accurate width
        double prefWidth = textArea.getWidth() -
                (textArea.getPadding().getLeft() + textArea.getPadding().getRight() + 20); // Extra padding

        // Create a TextFlow for measurement
        TextFlow measuringFlow = new TextFlow(measuringText);
        measuringFlow.setPrefWidth(prefWidth);
        measuringFlow.setVisible(false);

        // Add to scene temporarily for measurement
        Pane root = (Pane) textArea.getScene().getRoot();
        root.getChildren().add(measuringFlow);

        // Force layout
        measuringFlow.applyCss();
        measuringFlow.layout();

        // Get text bounds
        Bounds textBounds = measuringText.getBoundsInParent();

        // Calculate position
        double lineHeight = textArea.getFont().getSize() * 1.44; // Line height approximation
        double xPos = textBounds.getWidth() + textArea.getPadding().getLeft() + 2; // Add padding and small offset
        double yPos = (posInfo.row * lineHeight) + textArea.getPadding().getTop();

        // Apply position
        AnchorPane.setLeftAnchor(cursorMarker, xPos);
        AnchorPane.setTopAnchor(cursorMarker, yPos);

        // Clean up
        root.getChildren().remove(measuringFlow);
    }

    // Ensure the cursor overlay exists
    private void ensureCursorOverlayExists() {
        if (cursorOverlay != null) return;

        // Create overlay
        cursorOverlay = new AnchorPane();
        cursorOverlay.setId("cursorOverlay");
        cursorOverlay.setMouseTransparent(true);
        cursorOverlay.setStyle("-fx-background-color: transparent;");

        // Get textArea parent
        Parent parent = textArea.getParent();
        if (parent instanceof Pane) {
            Pane parentPane = (Pane) parent;

            // Create a container for text area and overlay
            StackPane container = new StackPane();

            // Get textArea position and size
            double x = textArea.getLayoutX();
            double y = textArea.getLayoutY();
            double width = textArea.getWidth();
            double height = textArea.getHeight();

            // Get textArea's scene position
            parentPane.getChildren().remove(textArea);

            // Add components to container
            container.getChildren().add(textArea);
            container.getChildren().add(cursorOverlay);

            // Position container
            container.setLayoutX(x);
            container.setLayoutY(y);
            container.setPrefWidth(width);
            container.setPrefHeight(height);

            // Add container to parent
            parentPane.getChildren().add(container);

            // Bind overlay size to textArea
            cursorOverlay.prefWidthProperty().bind(textArea.widthProperty());
            cursorOverlay.prefHeightProperty().bind(textArea.heightProperty());

            // Add listener to reposition cursors when text changes
            textArea.textProperty().addListener((obs, oldText, newText) -> {
                updateAllCursors();
            });

        } else {
            log.warn("TextArea parent is not a Pane; cannot create the cursor overlay");
        }
    }

    // Map to track user cursors and their positions
    private Map<String, Node> userCursors = new HashMap<>();
    private Map<String, Integer> userPositions = new HashMap<>();

    // Update positions for all cursors (e.g., after text change)
    private void updateAllCursors() {
        Map<String, Integer> positions = new HashMap<>(userPositions);

        for (Map.Entry<String, Integer> entry : positions.entrySet()) {
            String userId = entry.getKey();
            Integer position = entry.getValue();

            // Find user
            Optional<User> userOpt = users.stream()
                    .filter(u -> userId.equals(u.getId()))
                    .findFirst();

            if (userOpt.isPresent()) {
                // Update cursor position
                displayUserCursor(userOpt.get(), position);
            }
        }
    }

    // Call this when receiving cursor update
    private void cursorUpdate() {
        // Ensure overlay exists
        ensureCursorOverlayExists();

        wsClient.setCursorUpdateHandler(cursorData -> {
            Platform.runLater(() -> {
                // Extract data from the cursor update
                String userId = (String) cursorData.get("userId");
                Integer position = (Integer) cursorData.get("position");

                // Skip if it's our own cursor or invalid data
                if (userId == null || position == null || userId.equals(me.getId())) {
                    return;
                }

                // Store position for later updates
                userPositions.put(userId, position);

                // Find the user by ID
                Optional<User> optionalUser = users.stream()
                        .filter(user -> userId.equals(user.getId()))
                        .findFirst();

                if (optionalUser.isPresent()) {
                    User user = optionalUser.get();

                    // Create or update a cursor marker for this user
                    displayUserCursor(user, position);
                }
            });
        });
    }

    // Remove a user's cursor
    public void removeUserCursor(String userId) {
        Node cursor = userCursors.remove(userId);
        userPositions.remove(userId);

        if (cursor != null && cursorOverlay != null) {
            cursorOverlay.getChildren().remove(cursor);
        }
    }


    private void handleLeaving(SocketController wsClient) {
        wsClient.disconnect();
    }

    // Helper method to generate a clock timestamp
    private String generateClock() {
        // This could be a simple timestamp or a logical clock
        return String.valueOf(System.currentTimeMillis());
    }

    public boolean isCreator() {
        if (me.getId().equals("1")){
            isCreator = true;
            return true;
        }
        return false;
    }

    @FXML
    public void creatorPanel(){
        if (isCreator()) {
            creator.setVisible(true);
        } else {
            creator.setVisible(false);
        }
    }

    @FXML AnchorPane editorPanel;

    @FXML
    public void editorPanel(){
        if(me.isEditor()){
            editorPanel.setVisible(true);

        }
        else {
            editorPanel.setVisible(false);
        }
    }


    @FXML
    private void handleUndo(ActionEvent event) {
        if (undoStack.isEmpty()) return;
        CrdtOperation operation = undoStack.pop();
        redoStack.push(operation);

        if(operation.type().equals("insert")) {
            for(String nodeId : operation.nodeId()) {
                crdt.deleteCharacterById(nodeId);
            }
            CrdtOperation undoOperation = new CrdtOperation("delete", operation.userId(), operation.clock(), operation.nodeId(), null, null);
            wsClient.sendOperation(undoOperation);
            setFileContent(crdt.getText());
        } else if (operation.type().equals("delete")) {
            for (String nodeId : operation.nodeId()) {
                crdt.getNodeMap().get(nodeId).setDeleted(false);
            }
            CrdtOperation undoOperation = new CrdtOperation("undoDelete", operation.userId(), operation.clock(), operation.nodeId(), null, null);
            wsClient.sendOperation(undoOperation);
            setFileContent(crdt.getText());
        }
    }

    @FXML
    private void handleRedo(ActionEvent event) {
        if(redoStack.isEmpty()) return;
        CrdtOperation operation = redoStack.pop();
        undoStack.push(operation);

        if(operation.type().equals("insert")) {
            for (String nodeId : operation.nodeId()) {
                crdt.getNodeMap().get(nodeId).setDeleted(false);
            }
            CrdtOperation undoOperation = new CrdtOperation("undoDelete", operation.userId(), operation.clock(), operation.nodeId(), null, null);
            wsClient.sendOperation(undoOperation);
            setFileContent(crdt.getText());
        } else if (operation.type().equals("delete")) {
            for(String nodeId : operation.nodeId()) {
                crdt.deleteCharacterById(nodeId);
            }
            CrdtOperation undoOperation = new CrdtOperation("delete", operation.userId(), operation.clock(), operation.nodeId(), null, null);
            wsClient.sendOperation(undoOperation);
            setFileContent(crdt.getText());
        }
    }

    @FXML
    private void handleExport(ActionEvent event) {
        // Get the text content from the text area
        String export = textArea.getText();

        if (export == null || export.trim().isEmpty()) {
            // Show an alert if there's no content to export
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Export Warning");
            alert.setHeaderText("Empty Document");
            alert.setContentText("Cannot export an empty document.");
            alert.showAndWait();
            return;
        }

        // Create a file chooser dialog
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Document");

        // Set extension filter for text files
        FileChooser.ExtensionFilter extFilter =
                new FileChooser.ExtensionFilter("Text files (*.txt)", "*.txt");
        fileChooser.getExtensionFilters().add(extFilter);

        // Set initial file name to the document name (assuming you have it stored)
        fileChooser.setInitialFileName(documentName);

        // Show save file dialog
        Window window = textArea.getScene().getWindow();
        File file = fileChooser.showSaveDialog(window);

        if (file != null) {
            try {
                // Write the content to the selected file
                Files.writeString(file.toPath(), export, StandardCharsets.UTF_8);

                // Show success notification
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Export Successful");
                alert.setHeaderText("Document Exported");
                alert.setContentText("Your document has been saved to:\n" + file.getAbsolutePath());
                alert.showAndWait();

            } catch (IOException e) {
                // Handle any IO exceptions
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Export Error");
                alert.setHeaderText("Failed to Export Document");
                alert.setContentText("An error occurred while saving the file: " + e.getMessage());
                alert.showAndWait();
                log.error("Could not write the exported document to disk", e);
            }
        }
    }


    @FXML
    private void handleCopyEditor(ActionEvent event) {
        String code = editorCode.getText();

        // Get the system clipboard
        final Clipboard clipboard = Clipboard.getSystemClipboard();

        // Set content to clipboard
        final ClipboardContent content = new ClipboardContent();
        content.putString(code);
        clipboard.setContent(content);

        // Optional: Show feedback to user
        showFeedback("Editor code copied to clipboard!");
    }

    @FXML
    private void handleCopyViewer(ActionEvent event) {
        String code = viewerCode.getText();

        // Get the system clipboard
        final Clipboard clipboard = Clipboard.getSystemClipboard();

        // Set content to clipboard
        final ClipboardContent content = new ClipboardContent();
        content.putString(code);
        clipboard.setContent(content);

        // Optional: Show feedback to user
        showFeedback("Viewer code copied to clipboard!");
    }

    private void showFeedback(String message) {
         Platform.runLater(() -> {
             Alert alert = new Alert(Alert.AlertType.INFORMATION);
             alert.setTitle("Copied");
             alert.setHeaderText(null);
             alert.setContentText(message);
             alert.show();

             // Auto-close after 1.5 seconds
             PauseTransition delay = new PauseTransition(Duration.seconds(1.5));
             delay.setOnFinished(e -> alert.close());
             delay.play();
         });
    }


    @FXML
    private void handleMinimize(ActionEvent event) {
        ((Stage) ((JFXButton) event.getSource()).getScene().getWindow()).setIconified(true);
    }

    @FXML
    private void handleClose(ActionEvent event) {
        handleLeaving(wsClient);
        wsClient.disconnect(); // Explicitly call disconnect

        // Close the window
        ((Stage) ((JFXButton) event.getSource()).getScene().getWindow()).close();

    }

    @FXML
    private void handleMaximize(ActionEvent event) {
        Stage stage = (Stage) ((JFXButton) event.getSource()).getScene().getWindow();
        stage.setMaximized(!stage.isMaximized());
    }
}
