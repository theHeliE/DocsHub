module com.docshub.client {
    requires javafx.fxml;
    requires com.jfoenix;
    requires javafx.controls;
    requires spring.messaging;
    requires spring.websocket;
    requires spring.web;
    requires java.desktop;
    requires com.fasterxml.jackson.annotation;
    requires org.slf4j;


    opens com.docshub.client to javafx.fxml;
    exports com.docshub.client;
}