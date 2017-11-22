package matcher.gui.menu;

import java.net.InetSocketAddress;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import matcher.ProjectConfig;
import matcher.gui.GuiConstants;

public class UidSetupPane extends GridPane {
	UidSetupPane(ProjectConfig config, Window window, Node okButton) {
		this.config = config;
		//this.window = window;
		this.okButton = okButton;

		init();
	}

	private void init() {
		setHgap(GuiConstants.padding);
		setVgap(GuiConstants.padding);

		InetSocketAddress address = config.getUidAddress();

		add(new Label("Host:"), 0, 0);
		hostField = new TextField(address != null ? address.getHostString() : "");
		add(hostField, 1, 0);

		add(new Label("Port:"), 0, 1);
		portField = new TextField(address != null ? Integer.toString(address.getPort()) : "");
		add(portField, 1, 1);

		add(new Label("User:"), 0, 2);
		userField = new TextField(config.getUidUser() != null ? config.getUidUser() : "");
		add(userField, 1, 2);

		add(new Label("Password:"), 0, 3);
		passwordField = new TextField(config.getUidPassword() != null ? config.getUidPassword() : "");
		add(passwordField, 1, 3);

		add(new Label("Project:"), 0, 4);
		projectField = new TextField(config.getUidProject() != null ? config.getUidProject() : "");
		add(projectField, 1, 4);

		add(new Label("Version A:"), 0, 5);
		versionAField = new TextField(config.getUidVersionA() != null ? config.getUidVersionA() : "");
		add(versionAField, 1, 5);

		add(new Label("Version B:"), 0, 6);
		versionBField = new TextField(config.getUidVersionB() != null ? config.getUidVersionB() : "");
		add(versionBField, 1, 6);

		ChangeListener<String> changeListener = (observable, oldValue, newValue) -> {
			int port;

			okButton.setDisable(hostField.getText().isEmpty()
					|| portField.getText().isEmpty()
					|| !portField.getText().matches("\\d{1,5}")
					|| (port = Integer.parseInt(portField.getText())) > 0xffff
					|| port <= 0
					|| userField.getText().isEmpty()
					|| passwordField.getText().isEmpty()
					|| projectField.getText().isEmpty()
					|| versionAField.getText().isEmpty()
					|| versionBField.getText().isEmpty());
		};

		hostField.textProperty().addListener(changeListener);
		portField.textProperty().addListener(changeListener);
		userField.textProperty().addListener(changeListener);
		passwordField.textProperty().addListener(changeListener);
		projectField.textProperty().addListener(changeListener);
		versionAField.textProperty().addListener(changeListener);
		versionBField.textProperty().addListener(changeListener);

		changeListener.changed(null, null, null);
	}

	void updateConfig() {
		config.setUidSettings(hostField.getText(),
				Integer.parseInt(portField.getText()),
				userField.getText(),
				passwordField.getText(),
				projectField.getText(),
				versionAField.getText(),
				versionBField.getText());
	}

	private final ProjectConfig config;
	//private final Window window;
	private final Node okButton;

	private TextField hostField;
	private TextField portField;
	private TextField userField;
	private TextField passwordField;
	private TextField projectField;
	private TextField versionAField;
	private TextField versionBField;
}
