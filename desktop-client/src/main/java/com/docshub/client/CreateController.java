package com.docshub.client;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXTextArea;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;

public class CreateController {

    private Stage stage;
    private Scene scene;
    private Parent root;

    @FXML
    JFXTextArea documentNameArea;

    @FXML
    JFXButton createButton;

    public void switchToSceneMain(ActionEvent event) throws IOException {
        root = FXMLLoader.load(getClass().getResource("main.fxml"));
        stage = (Stage)((Node)event.getSource()).getScene().getWindow();
        scene = new Scene(root);
        stage.setScene(scene);
        stage.show();
    }

    public void switchToSceneFileViewer(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("document.fxml"));
        // Load the FXML file to get the root node
        root = loader.load();

        // Now call getController() on the loader instance
        DocumentController documentController = loader.getController();
        documentController.createDocument(documentNameArea.getText().trim());

        stage = new Stage();
        stage.setTitle("DocsHub/ " + documentNameArea.getText().trim());
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setResizable(false);
        stage.setScene(new Scene(root));
        stage.show();
    }

    @FXML
    private void handleTextChange(javafx.scene.input.KeyEvent event) {
        JFXTextArea source = (JFXTextArea) event.getSource();
        JFXButton button = (JFXButton) source.getScene().lookup("#createButton");
        if (button != null) {
            button.setDisable(source.getText().isBlank());
        }
    }

}
