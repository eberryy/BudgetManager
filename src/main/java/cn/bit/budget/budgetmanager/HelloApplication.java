package cn.bit.budget.budgetmanager;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class HelloApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader fxmlLoader = new FXMLLoader(HelloApplication.class.getResource("hello-view.fxml"));
        Scene scene = new Scene(fxmlLoader.load(), 1000, 600); // 调大一点尺寸

        scene.getStylesheets().add(getClass().getResource("style.css").toExternalForm());

        stage.setTitle("我的私人账本 v1.0");
        stage.setScene(scene);
        stage.show();
    }
}
