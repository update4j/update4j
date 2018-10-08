package org.update4jGradleExample;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.text.Font;
import javafx.stage.Stage;


public class HelloWorld extends Application {

	public static void main(String[] args) throws Throwable {
		System.out.println("Hello World");

		launch(args);
	}

	@Override public void start(Stage stage) {
		if(stage==null){
			stage=new Stage();

		}
		//Stage stage = new Stage();
		Label content=new Label("Hello World");
		Font f=content.fontProperty().get();
		content.fontProperty().set(new Font(f.getName(),40));
		Scene scene  = new Scene(content,400,400);
		stage.setTitle("Grade Example");
		stage.setScene(scene);
		stage.setOnCloseRequest(e -> {
			Platform.exit();
			System.exit(0);
		});
		stage.show();
	}




}
