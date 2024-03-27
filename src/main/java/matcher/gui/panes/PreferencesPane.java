package matcher.gui.panes;

import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import job4j.JobManager;

import matcher.Matcher;
import matcher.gui.GuiConstants;

public class PreferencesPane extends VBox {
	public PreferencesPane(Button okButton) {
		super(GuiConstants.padding);

		this.okButton = okButton;

		init();
	}

	private void init() {
		int minSliderValue = 1;
		int sliderLabelStep = 2;
		int originalMaxSliderValue = Runtime.getRuntime().availableProcessors();
		int alignedMaxSliderValue = ((int) Math.floor((float) originalMaxSliderValue / sliderLabelStep)) * sliderLabelStep;
		int normalizedShift = minSliderValue - ((int) Math.floor((float) minSliderValue / sliderLabelStep) * sliderLabelStep);
		int distanceRight = normalizedShift;
		int distanceLeft = sliderLabelStep - distanceRight;

		if (distanceLeft >= distanceRight) {
			alignedMaxSliderValue += distanceRight;
		} else {
			alignedMaxSliderValue -= distanceLeft;
		}

		alignedMaxSliderValue = Math.max(sliderLabelStep, alignedMaxSliderValue);

		Text text = new Text("Main worker threads (only applies after the current workers are finished):");
		text.setWrappingWidth(getWidth());
		getChildren().add(text);

		mainWorkersSlider = newSlider(minSliderValue, alignedMaxSliderValue, sliderLabelStep, Matcher.threadPool.getParallelism());
		mainWorkersSlider.valueProperty().addListener((newValue) -> showWarningIfNecessary());
		getChildren().add(mainWorkersSlider);

		text = new Text("Job executor threads:");
		text.setWrappingWidth(getWidth());
		getChildren().add(text);

		alignedMaxSliderValue = Math.max(2, alignedMaxSliderValue);
		jobExecutorsSlider = newSlider(minSliderValue, alignedMaxSliderValue, sliderLabelStep, JobManager.get().getMaxJobExecutorThreads());
		jobExecutorsSlider.valueProperty().addListener((newValue) -> showWarningIfNecessary());
		getChildren().add(jobExecutorsSlider);

		warningText = new Text();
		warningText.setStyle("-fx-fill: firebrick;");
		VBox.setMargin(warningText, new Insets(GuiConstants.padding, 0, GuiConstants.padding, 0));
		getChildren().add(warningText);
		showWarningIfNecessary();

		widthProperty().addListener((observable, oldWidth, newWidth) -> {
			for (Node child : getChildren()) {
				if (child instanceof Text) {
					((Text) child).setWrappingWidth(newWidth.intValue() - getSpacing() * 5);
				}
			}
		});
		okButton.setOnAction(event -> save());
		setWidth(600);
	}

	private Slider newSlider(int min, int max, int labelDistance, int value) {
		Slider slider = new Slider(min, max, value);
		slider.setShowTickMarks(true);
		slider.setShowTickLabels(true);
		slider.setMajorTickUnit(labelDistance);
		slider.setMinorTickCount(labelDistance - 1);
		slider.setBlockIncrement(1);
		slider.setSnapToTicks(true);
		return slider;
	}

	private void showWarningIfNecessary() {
		StringBuilder warning = new StringBuilder();

		int allocatedMegabytes = (int) (Runtime.getRuntime().maxMemory() / 1024 / 1024);
		int workerThreadCount = (int) mainWorkersSlider.getValue();
		int minBaseMegabytes = 5200;
		int minMegabytesPerThread = 70;
		int minTotalRequiredMegabytes = minBaseMegabytes + workerThreadCount * minMegabytesPerThread;

		if (allocatedMegabytes < minBaseMegabytes) {
			warning.append("The amount of allocated RAM (");
			warning.append(allocatedMegabytes);
			warning.append(" MB) is insufficient! Matcher requires at least ");
			warning.append(minBaseMegabytes);
			warning.append(" MB to work correctly.");
		} else if (allocatedMegabytes < minTotalRequiredMegabytes) {
			warning.append("The amount of allocated RAM (");
			warning.append(allocatedMegabytes);
			warning.append(" MB) is most likely insufficient for the amount of allocated worker threads! ");
			warning.append("Please increase the RAM limit to at least ");
			warning.append((int) Math.ceil(minTotalRequiredMegabytes / 100f) * 100);
			warning.append(" MB (via the '-Xmx' startup arg)!");
		}

		warningText.setText(warning.toString());

		if (!warningText.isVisible() && warning.length() > 0) {
			warningText.setVisible(true);
			requestLayout();
			requestParentLayout();
		} else if (warningText.isVisible() && warning.length() == 0) {
			warningText.setVisible(false);
			requestLayout();
			requestParentLayout();
		}
	}

	private void save() {
		int oldThreadPoolSize = Matcher.threadPool.getParallelism();
		int newThreadPoolSize = (int) mainWorkersSlider.getValue();

		if (newThreadPoolSize != oldThreadPoolSize) {
			Matcher.threadPool = (ForkJoinPool) Executors.newWorkStealingPool(newThreadPoolSize);
		}

		JobManager.get().setMaxJobExecutorThreads((int) jobExecutorsSlider.getValue());
	}

	private final Button okButton;
	private Slider mainWorkersSlider;
	private Slider jobExecutorsSlider;
	private Text warningText;
}
