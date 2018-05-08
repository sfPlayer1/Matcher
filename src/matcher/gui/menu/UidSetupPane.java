package matcher.gui.menu;

import java.net.InetSocketAddress;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import matcher.config.UidConfig;
import matcher.gui.GuiConstants;

public class UidSetupPane extends GridPane {
	UidSetupPane(UidConfig config, Window window, Node okButton) {
		//this.window = window;
		this.okButton = okButton;

		init(config);
	}

	private void init(UidConfig config) {
		setHgap(GuiConstants.padding);
		setVgap(GuiConstants.padding);

		InetSocketAddress address = config.getAddress();

		add(new Label("Host:"), 0, 0);
		hostField = new TextField(address != null ? address.getHostString() : "");
		add(hostField, 1, 0);

		add(new Label("Port:"), 0, 1);
		portField = new TextField(address != null ? Integer.toString(address.getPort()) : "");
		add(portField, 1, 1);

		add(new Label("User:"), 0, 2);
		userField = new TextField(config.getUser());
		add(userField, 1, 2);

		add(new Label("Password:"), 0, 3);
		passwordField = new TextField(config.getPassword());
		add(passwordField, 1, 3);

		add(new Label("Project:"), 0, 4);
		projectField = new TextField(config.getProject() != null ? config.getProject() : "");
		add(projectField, 1, 4);

		add(new Label("Version A:"), 0, 5);
		versionAField = new TextField(config.getVersionA() != null ? config.getVersionA() : "");
		add(versionAField, 1, 5);

		add(new Label("Version B:"), 0, 6);
		versionBField = new TextField(config.getVersionB() != null ? config.getVersionB() : "");
		add(versionBField, 1, 6);

		ChangeListener<String> changeListener = (observable, oldValue, newValue) -> okButton.setDisable(!createConfig().isValid());

		hostField.textProperty().addListener(changeListener);
		portField.textProperty().addListener(changeListener);
		userField.textProperty().addListener(changeListener);
		passwordField.textProperty().addListener(changeListener);
		projectField.textProperty().addListener(changeListener);
		versionAField.textProperty().addListener(changeListener);
		versionBField.textProperty().addListener(changeListener);

		changeListener.changed(null, null, null);
	}

	UidConfig createConfig() {
		int port;

		try {
			port = Integer.parseInt(portField.getText());
		} catch (NumberFormatException e) {
			port = 0;
		}

		return new UidConfig(hostField.getText(),
				port,
				userField.getText(),
				passwordField.getText(),
				projectField.getText(),
				versionAField.getText(),
				versionBField.getText());
	}

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
