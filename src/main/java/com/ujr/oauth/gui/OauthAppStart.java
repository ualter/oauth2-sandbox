package com.ujr.oauth.gui;

import java.io.IOException;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;

/**
 * 
 * @author Ualter
 *
 */
public class OauthAppStart extends Application {
	
	final static Logger LOG = LoggerFactory.getLogger(OauthAppStart.class);
	
	private Stage stage;
	private Pane rootLayout;

	public static void main(String[] args) {
		launch(args);
	}
	
	@Override
	public void start(Stage stage) throws Exception {
		this.stage = stage;
		this.stage.setTitle("OAuth 2.0 - Google Pub/Sub API Services");
		this.initRootLayout();
		
	}
	
	public void initRootLayout() {
		
		try {
			FXMLLoader loader = new FXMLLoader();
			loader.setLocation(OauthAppStart.class.getResource("/com/ujr/oauth/gui/GooglePubSubMain.fxml"));
			this.rootLayout = (Pane) loader.load();
			
			GooglePubSubMainController googlePuSubMainController = loader.getController();
			googlePuSubMainController.setAppStart(this);
			
			Scene scene = new Scene(rootLayout);
			scene.getStylesheets().add("/com/ujr/oauth/gui/GooglePubSubMain.css");
			stage.setScene(scene);
			stage.show();
			
		} catch (IOException e) {
			LOG.error(e.getMessage(),e);
			throw new RuntimeException(e.getMessage(), e);
		}
		
	}

	public Stage getStage() {
		return stage;
	}

	public void setStage(Stage stage) {
		this.stage = stage;
	}

	public Pane getRootLayout() {
		return this.rootLayout;
	}


}
