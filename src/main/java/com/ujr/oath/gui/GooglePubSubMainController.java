package com.ujr.oath.gui;

import java.net.URL;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import com.ujr.oath.client.credentials.google.api.appservice.GoogleAppServiceAccount;
import com.ujr.oath.client.credentials.google.api.appservice.GoogleAppServiceAccountScorecardUjr;
import com.ujr.oath.client.credentials.google.api.pubsub.GooglePubSubApiHandler;
import com.ujr.oath.client.credentials.google.api.pubsub.domain.publish.ListMessages;
import com.ujr.oath.client.credentials.google.api.pubsub.domain.publish.ResponsePublishTopic;
import com.ujr.oath.client.credentials.google.api.pubsub.domain.pull.RequestPullMessagesSubscription;
import com.ujr.oath.client.credentials.google.api.pubsub.domain.pull.ResponsePullMessagesSubscription;
import com.ujr.oath.logger.OathLogOutputStreamAppender;
import com.ujr.oath.utils.MySimpleDb;
import com.ujr.oath.utils.Utils;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;

public class GooglePubSubMainController implements Initializable {

	private static Logger LOG = LoggerFactory.getLogger(GooglePubSubMainController.class);

	private MySimpleDb myDb;

	@FXML
	private ComboBox<String> cmbTopics;
	@FXML
	private TextArea txtLogs;
	@FXML
	private TextArea txtMessagePublish;
	@FXML
	private Label lblSubsOne;
	@FXML
	private Label lblSubsTwo;
	@FXML
	private Button btnPullOne;
	@FXML
	private Button btnPullTwo;
	@FXML
	private Button btnPublish;
	@FXML
	private TableView<MessageReceived> tableViewOne;
	@FXML
	private TableView<MessageReceived> tableViewTwo;
	@FXML
	private TableView<MessageSent> tableMessagesSent;
	@FXML
	private TableColumn<MessageSent, Integer> tableMessageSentColCount;
	@FXML
	private TableColumn<MessageSent, String> tableMessageSentColId;
	@FXML
	private TableColumn<MessageSent, String> tableMessageSentColMessage;

	@FXML
	private TableColumn<MessageReceived,Integer> tableMessagesSubsOneCount;
	@FXML
	private TableColumn<MessageReceived,String> tableMessagesSubsOneMessage;
	@FXML
	private TableColumn<MessageReceived,String> tableMessagesSubsOneTime;
	
	@FXML
	private TableColumn<MessageReceived,Integer> tableMessagesSubsTwoCount;
	@FXML
	private TableColumn<MessageReceived,String> tableMessagesSubsTwoMessage;
	@FXML
	private TableColumn<MessageReceived,String> tableMessagesSubsTwoTime;
	
	@FXML
	private ProgressIndicator progressIndicatorOne;
	
	@FXML
	private ProgressIndicator progressIndicatorTwo;
	
	private ToggleSwitch toggleOne;
	private ToggleSwitch toggleTwo;
	
	private int totalSent;
	private int totalReceivedOne;
	private int totalReceivedTwo;
	
	private boolean isListeningSubsOneON;

	private OathAppStart appStart;

	public GooglePubSubMainController() {
		Yaml yaml = new Yaml();
		this.myDb = yaml.loadAs(GooglePubSubMainController.class.getResourceAsStream("/com/ujr/oath/utils/my-simple-db.yaml"), MySimpleDb.class);
	}

	@Override
	public void initialize(URL arg0, ResourceBundle arg1) {
		initializeUI();
		OathLogOutputStreamAppender.setStaticOutputStream(new LoggerConsoleOutputStream(this.txtLogs));
	}

	public void setAppStart(OathAppStart appStart) {
		this.appStart = appStart;
		initilizeUIProgramatically();
	}

	private void initilizeUIProgramatically() {
		this.toggleOne = new ToggleSwitch();
		toggleOne.setTranslateX(810);
		toggleOne.setTranslateY(22);
        this.getAppStart().getRootLayout().getChildren().add(toggleOne);
        
        this.toggleOne.switchedOnProperty().addListener((observable, oldValue, newValue) -> {
        	isListeningSubsOneON = newValue;
        	changeStateListeningSubsOne();
        });
        
        
        this.toggleTwo = new ToggleSwitch();
        toggleTwo.setTranslateX(1180);
        toggleTwo.setTranslateY(22);
        this.getAppStart().getRootLayout().getChildren().add(toggleTwo);
	}
	
	private TaskPullMessagesSubscription taskPullOne;
	
	private void changeStateListeningSubsOne() {
		if ( this.isListeningSubsOneON ) {
			taskPullOne = pullMessages(0, false);
			this.progressIndicatorOne.setOpacity(1);
		} else {
			if ( taskPullOne != null ) {
				taskPullOne.cancel(true);
				this.btnPullOne.setDisable(false);
			}
			this.progressIndicatorOne.setOpacity(0);
		}
	}

	public OathAppStart getAppStart() {
		return appStart;
	}

	@FXML
	public void handlePublishMessageButton() {
		String message = this.txtMessagePublish.getText();
		
		if (StringUtils.isEmpty(message)) {
			Alert alert = new Alert(AlertType.WARNING);
			alert.setTitle("Ops!");
			alert.setHeaderText(null);
			alert.setContentText("There's no written message to be sent, did you realize that?");
			alert.showAndWait();
		} else {
			String topic = this.cmbTopics.getSelectionModel().getSelectedItem();
			
			TaskPublishMessageTopic taskPublishMessageTopic = new TaskPublishMessageTopic(message, topic);
			ExecutorService executorService = Utils.createExecutorService();
			executorService.submit(taskPublishMessageTopic);
			
			taskPublishMessageTopic.setOnRunning(e -> {
				//this.getAppStart().getStage().getScene().setCursor(Cursor.WAIT);
				this.btnPublish.setDisable(true);
				this.cmbTopics.setDisable(true);
			});
			
			taskPublishMessageTopic.setOnSucceeded(e -> {
				//this.getAppStart().getStage().getScene().setCursor(Cursor.DEFAULT);
				this.btnPublish.setDisable(false);
				this.cmbTopics.setDisable(false);
				
				ResponsePublishTopic response        = taskPublishMessageTopic.getValue();
				final AtomicInteger   count          = new AtomicInteger();
				
				response.getMessages().forEach(m -> {
					int index = count.incrementAndGet();
					MessageSent messageSent = new MessageSent(++totalSent,response.getMessageIds().get(index - 1), m);
					this.tableMessagesSent.getItems().add(messageSent);
				}); 
				
				this.tableMessagesSent.scrollTo(totalSent -1);
			});
		}
		
	}

	@FXML
	public void handlePullMessageSubsOne() {
		pullMessages(0, true);
	}

	@FXML
	public void handlePullMessageSubsTwo() {
		pullMessages(1, true);
	}

	
	
	private TaskPullMessagesSubscription pullMessages(int subscriptionIndex, boolean returnImmediately) {
		ExecutorService executorService = Utils.createExecutorService();
		TaskPullMessagesSubscription taskPullMessagesSubscription = new TaskPullMessagesSubscription(this.myDb, subscriptionIndex, returnImmediately);
		executorService.submit(taskPullMessagesSubscription);
		
		taskPullMessagesSubscription.setOnRunning(e -> {
			//this.getAppStart().getStage().getScene().setCursor(Cursor.WAIT);
			if ( subscriptionIndex == 0) {
				this.btnPullOne.setDisable(true);
			} else {
				this.btnPullTwo.setDisable(true);
			}
			
		});
		taskPullMessagesSubscription.setOnSucceeded(e -> {
			//this.getAppStart().getStage().getScene().setCursor(Cursor.DEFAULT);
			if ( subscriptionIndex == 0) {
				this.btnPullOne.setDisable(false);
			} else {
				this.btnPullTwo.setDisable(false);
			}
			
			ResponsePullMessagesSubscription response = taskPullMessagesSubscription.getValue();
			if ( response.getReceivedMessages() != null ) {
				response.getReceivedMessages().forEach(m -> {
					MessageReceived messageReceived = new MessageReceived(0, m.getMessage().getDataDecoded(), m.getMessage().getPublishTime());
					if ( subscriptionIndex == 0) {
						messageReceived.setCount(++totalReceivedOne);
						this.tableViewOne.getItems().add(messageReceived);
					} else {
						messageReceived.setCount(++totalReceivedTwo);
						this.tableViewTwo.getItems().add(messageReceived);
					}
				});
				this.tableViewOne.scrollTo(totalReceivedOne - 1);
				this.tableViewTwo.scrollTo(totalReceivedTwo - 1);
			}
			
			if ( this.toggleOne.switchedOnProperty().getValue() ) {
				taskPullOne = pullMessages(0, false);
			}
		});
		
		return taskPullMessagesSubscription;
	}

	private void initializeUI() {
		this.myDb.getTopics().forEach(t -> this.cmbTopics.getItems().add(t));
		this.cmbTopics.getSelectionModel().selectFirst();
		
		// Buttons
		
		this.btnPublish.getStyleClass().add("dark-blue");
		this.btnPullOne.getStyleClass().add("dark-blue");
		this.btnPullTwo.getStyleClass().add("dark-blue");

		// Labels
		
		this.lblSubsOne.setText(this.myDb.getSubscriptions().get(0));
		this.lblSubsTwo.setText(this.myDb.getSubscriptions().get(1));

		this.lblSubsOne.getStyleClass().add("lblSubs");
		this.lblSubsTwo.getStyleClass().add("lblSubs");

		DropShadow dropShadow = new DropShadow();
		dropShadow.setOffsetX(3.5f);
		dropShadow.setOffsetY(3.5f);
		dropShadow.setWidth(6.5f);
		dropShadow.setHeight(6.5f);
		dropShadow.setColor(Color.GRAY);

		this.lblSubsOne.setEffect(dropShadow);
		this.lblSubsTwo.setEffect(dropShadow);

		// TxtLogs
		this.txtLogs.getStyleClass().add("logConsole");
		this.txtLogs.setEditable(false);
		
		//Tables
		
		this.tableMessageSentColCount.setCellValueFactory(new PropertyValueFactory<MessageSent, Integer>("count"));
		this.tableMessageSentColId.setCellValueFactory(new PropertyValueFactory<MessageSent, String>("id"));
		this.tableMessageSentColMessage.setCellValueFactory(new PropertyValueFactory<MessageSent, String>("message"));
		
		this.tableMessagesSubsOneCount.setCellValueFactory(new PropertyValueFactory<MessageReceived, Integer>("count"));
		this.tableMessagesSubsOneMessage.setCellValueFactory(new PropertyValueFactory<MessageReceived, String>("message"));
		this.tableMessagesSubsOneTime.setCellValueFactory(new PropertyValueFactory<MessageReceived, String>("publishTime"));
		
		this.tableMessagesSubsTwoCount.setCellValueFactory(new PropertyValueFactory<MessageReceived, Integer>("count"));
		this.tableMessagesSubsTwoMessage.setCellValueFactory(new PropertyValueFactory<MessageReceived, String>("message"));
		this.tableMessagesSubsTwoTime.setCellValueFactory(new PropertyValueFactory<MessageReceived, String>("publishTime"));
	}

	private static class TaskPullMessagesSubscription extends Task<ResponsePullMessagesSubscription> {

		private MySimpleDb myDb;
		private int subscriptionIndex;
		private boolean returnImmediately;

		public TaskPullMessagesSubscription(MySimpleDb myDb, int subscriptionIndex, boolean returnImmediately) {
			super();
			this.myDb = myDb;
			this.subscriptionIndex = subscriptionIndex;
			this.returnImmediately = returnImmediately;
		}

		@Override
		protected ResponsePullMessagesSubscription call() throws Exception {
			GoogleAppServiceAccount appServiceAccount = new GoogleAppServiceAccountScorecardUjr();
			GooglePubSubApiHandler pubSubApiHandler = new GooglePubSubApiHandler(appServiceAccount);

			RequestPullMessagesSubscription request = new RequestPullMessagesSubscription();
			request.setSubscription(this.myDb.getSubscriptions().get(this.subscriptionIndex));
			request.setMaxMessages(1000);
			request.setReturnImmediately(returnImmediately);
			
			FutureTask<ResponsePullMessagesSubscription> futureTaskResponse = pubSubApiHandler.pullMessagesForSubscription(request, true);
			Executor executor = Executors.newFixedThreadPool(1);
			executor.execute(futureTaskResponse);
			
			ResponsePullMessagesSubscription response = futureTaskResponse.get();
			return response;
		}

	}

	private static class TaskPublishMessageTopic extends Task<ResponsePublishTopic> {

		private String message;
		private String topic;

		public TaskPublishMessageTopic(String message, String topic) {
			super();
			this.message = message;
			this.topic = topic;
		}

		@Override
		protected ResponsePublishTopic call() throws Exception {
			GoogleAppServiceAccount appServiceAccount = new GoogleAppServiceAccountScorecardUjr();
			GooglePubSubApiHandler pubSubApiHandler = new GooglePubSubApiHandler(appServiceAccount);

			ListMessages listMessages = new ListMessages();
			for (String line : this.message.split("\n|\r")) {
				listMessages.addMessage(line);
			}

			if (listMessages.getMessages().isEmpty()) {
				listMessages.addMessage(this.message);
			}
			
			ResponsePublishTopic response = pubSubApiHandler.publishMessageOnTopic(this.topic, listMessages);
			
			List<String> messagesSent = listMessages.getMessages().stream().map(m -> m.getDataDecoded()).collect(Collectors.toList());
			response.setMessages(messagesSent);
			
			return response;
		}

	}
	
	public static class MessageSent {
		
		private final SimpleIntegerProperty count;
		private final SimpleStringProperty id;
		private final SimpleStringProperty message;
		
		private MessageSent(int count, String id, String message) {
			super();
			this.count = new SimpleIntegerProperty(count);
			this.id = new SimpleStringProperty(id);
			this.message = new SimpleStringProperty(message);
		}

		public int getCount() {
			return this.count.get();
		}
		public void setCount(int count) {
			this.count.set(count);
		}
		public String getId() {
			return this.id.get();
		}
		public void setId(String id) {
			this.id.set(id);
		}

		public String getMessage() {
			return this.message.get();
		}
		public void setMessage(String message) {
			this.message.set(message);
		}
		
	}
	
	public static class MessageReceived {
		
		private final SimpleIntegerProperty count;
		private final SimpleStringProperty message;
		private final SimpleStringProperty publishTime;
		
		public MessageReceived(int count, String message, String publishTime) {
			this.count = new SimpleIntegerProperty(count);
			this.message = new SimpleStringProperty(message);
			this.publishTime = new SimpleStringProperty(publishTime);
		}

		public int getCount() {
			return this.count.get();
		}
		public void setCount(int count) {
			this.count.set(count);
		}

		public String getMessage() {
			return message.get();
		}
		public void setMessage(String message) {
			this.message.set(message);
		}

		public String getPublishTime() {
			return publishTime.get();
		}
		public void setPublishTime(String publ) {
			this.publishTime.set(publ);
		}
		
		
		
	}

}
