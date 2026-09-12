package com.docshub.client;

import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import com.jfoenix.controls.JFXButton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class Browser {

    private static final Logger log = LoggerFactory.getLogger(Browser.class);

    private Stage stage;
    private Scene scene;
    private Parent root;

    @FXML
    private JFXButton browseButton;

    @FXML
    private void openFileExplorer(ActionEvent event) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select a Text File");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Text Files", "*.txt", "*.md")
        );

        Window window = browseButton.getScene().getWindow();
        File selected = chooser.showOpenDialog(window);
        if (selected == null) return;

        try {
            // Read file line-by-line to preserve structure
            StringBuilder contentBuilder = new StringBuilder();
            try (BufferedReader reader = Files.newBufferedReader(selected.toPath(), StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    contentBuilder.append(line).append("\n"); // Preserve line structure
                }
            }
            String content = contentBuilder.toString();

            // Load viewer with absolute path
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("document.fxml")
            );
            root = loader.load();

            // Inject content
            DocumentController documentController = loader.getController();
            documentController.createDocument(selected.getName(), content);

            // Show in new window
            stage = new Stage();
            stage.setTitle("DocsHub/ " + selected.getName());
            stage.initStyle(StageStyle.UNDECORATED);
            stage.setResizable(false);
            stage.setScene(new Scene(root));
            stage.show();

        } catch (IOException e) {
            log.error("Could not open the selected file", e);
            // Optionally alert the user here
        }
    }

    public void switchToSceneMain(ActionEvent event) throws IOException {
        // Use a fully qualified path with the package name
        root = FXMLLoader.load(getClass().getResource("/com/docshub/client/main.fxml"));
        stage = (Stage)((Node)event.getSource()).getScene().getWindow();
        scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

    private double xOffset = 0;
    private double yOffset = 0;

    @FXML
    private AnchorPane rootPane; // You must fx:id the root AnchorPane!

    public void initialize() {
        // Set mouse pressed event
        rootPane.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });

        // Set mouse dragged event
        rootPane.setOnMouseDragged(event -> {
            rootPane.getScene().getWindow().setX(event.getScreenX() - xOffset);
            rootPane.getScene().getWindow().setY(event.getScreenY() - yOffset);
        });
    }

}
