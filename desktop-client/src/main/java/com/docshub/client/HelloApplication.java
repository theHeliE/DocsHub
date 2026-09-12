package com.docshub.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class HelloApplication extends Application {

    private static final Logger log = LoggerFactory.getLogger(HelloApplication.class);

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("main.fxml"));
        Scene scene = new Scene(fxmlLoader.load());
        stage.setTitle("DocsHub");
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setResizable(false);
        Image icon = new Image(getClass().getResource("/images/document.png").toExternalForm());
        stage.getIcons().add(icon);
        stage.setScene(scene);
        stage.show();

        log.info("DocsHub client started");
    }

    public static void main(String[] args) {
        launch();
    }
}