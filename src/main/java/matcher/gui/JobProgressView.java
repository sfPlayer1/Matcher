package matcher.gui;

import java.util.List;

import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import job4j.Job;
import job4j.JobManager;
import job4j.JobState;

public class JobProgressView extends Control {
	@SuppressWarnings("incomplete-switch")
	public JobProgressView(Gui gui) {
		this.gui = gui;

		getStyleClass().add("task-progress-view");

		JobManager.get().registerEventListener((job, event) -> {
			switch (event) {
			case JOB_QUEUED:
				addJob(job, true);
				break;

			case JOB_FINISHED:
				Platform.runLater(() -> removeJob(job));
				break;
			}
		});
	}

	private void addJob(Job<?> job, boolean append) {
		if (job.getSettings().isInvisible() && !gui.getMatcher().debugMode) {
			return;
		}

		job.addSubJobAddedListener((subJob) -> addJob(subJob, false));

		Platform.runLater(() -> {
			if (jobs.contains(job)) return;

			boolean passthrough = job.getSettings().isVisualPassthrough();

			if (passthrough && !gui.getMatcher().debugMode) {
				return;
			}

			if (append || jobs.isEmpty()) {
				jobs.add(job);
			} else {
				boolean isParent;
				boolean shareParents;

				for (int i = jobs.size() - 1; i >= 0; i--) {
					Job<?> currentJob = jobs.get(i);

					isParent = currentJob == job.getParent();
					shareParents = currentJob.hasParentJobInHierarchy(job.getParent());

					if (isParent || shareParents) {
						jobs.add(i+1, job);
						break;
					}
				}
			}
		});
	}

	private void removeJob(Job<?> job) {
		List<Job<?>> subJobs = job.getSubJobs(false);

		synchronized (subJobs) {
			for (int i = subJobs.size() - 1; i >= 0; i--) {
				removeJob(subJobs.get(i));
			}
		}

		jobs.remove(job);
	}

	@Override
	protected Skin<?> createDefaultSkin() {
		return new JobProgressViewSkin(this);
	}

	private class JobProgressViewSkin extends SkinBase<JobProgressView> {
		JobProgressViewSkin(JobProgressView progressView) {
			super(progressView);

			// list view
			ListView<Job<?>> listView = new ListView<>();
			listView.setPrefSize(400, 380);
			listView.setPlaceholder(new Label("No tasks running"));
			listView.setCellFactory(list -> new TaskCell());
			listView.setFocusTraversable(false);
			listView.setPadding(new Insets(GuiConstants.padding, GuiConstants.padding, GuiConstants.padding, GuiConstants.padding));

			Bindings.bindContent(listView.getItems(), progressView.jobs);

			getChildren().add(listView);
		}

		class TaskCell extends ListCell<Job<?>> {
			private ProgressBar progressBar;
			private Label titleLabel;
			private Label progressLabel;
			private Button cancelButton;

			private Job<?> job;
			private BorderPane borderPane;
			private VBox vbox;

			TaskCell() {
				titleLabel = new Label();
				titleLabel.getStyleClass().add("task-title");

				progressLabel = new Label();
				progressLabel.getStyleClass().add("task-message");

				progressBar = new ProgressBar();
				progressBar.setMaxWidth(Double.MAX_VALUE);
				progressBar.setPrefHeight(10);
				progressBar.getStyleClass().add("task-progress-bar");

				cancelButton = new Button("Cancel");
				cancelButton.getStyleClass().add("task-cancel-button");
				cancelButton.setTooltip(new Tooltip("Cancel Task"));
				cancelButton.setOnAction(event -> {
					if (this.job != null) {
						cancelButton.setDisable(true);
						this.job.cancel();
					}
				});

				vbox = new VBox();
				vbox.setPadding(new Insets(GuiConstants.padding, 0, 0, GuiConstants.padding));
				vbox.setSpacing(GuiConstants.padding * 0.7f);
				vbox.getChildren().add(titleLabel);
				vbox.getChildren().add(progressBar);
				vbox.getChildren().add(progressLabel);

				BorderPane.setAlignment(cancelButton, Pos.CENTER);
				BorderPane.setMargin(cancelButton, new Insets(0, GuiConstants.padding, 0, GuiConstants.padding));

				borderPane = new BorderPane();
				borderPane.setCenter(vbox);
				borderPane.setRight(cancelButton);
				setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
			}

			private void resetProperties() {
				titleLabel.setText(null);
				progressLabel.setText(null);
				progressBar.setProgress(-1);
				progressBar.setStyle(null);
				cancelButton.setText("Cancel");
				cancelButton.setDisable(false);
			}

			@SuppressWarnings("incomplete-switch")
			private void update(Job<?> originatingJob) {
				if (originatingJob != this.job) {
					return;
				}

				if (this.job == null) {
					resetProperties();
					return;
				}

				if (this.job.getProgress() <= 0) {
					progressBar.setProgress(-1);
				} else {
					progressLabel.setText(String.format("%.0f%%", Math.floor(this.job.getProgress() * 100)));
					progressBar.setProgress(this.job.getProgress());
				}

				JobState state = this.job.getState();

				if (state.isFinished()) {
					cancelButton.setDisable(true);
				}

				if (state.compareTo(JobState.CANCELING) >= 0) {
					cancelButton.setDisable(true);

					String text = state.toString();
					text = text.charAt(0) + text.substring(1).toLowerCase();

					switch (state) {
					case CANCELING:
						text += "...";
						break;
					case CANCELED:
					case ERRORED:
						progressBar.setStyle("-fx-accent: darkred");
						break;
					}

					cancelButton.setText(text);
				}
			}

			@Override
			protected void updateItem(Job<?> job, boolean empty) {
				super.updateItem(job, empty);
				this.job = job;

				if (empty || job == null) {
					resetProperties();
					getStyleClass().setAll("task-list-cell-empty");
					setGraphic(null);
				} else if (job != null) {
					job.addCancelListener(() -> Platform.runLater(() -> update(job)));
					job.addProgressListener((progress) -> Platform.runLater(() -> update(job)));
					job.addCompletionListener((result, error) -> Platform.runLater(() -> update(job)));

					update(job);
					getStyleClass().setAll("task-list-cell");
					titleLabel.setText(job.getId());

					int nestLevel = 0;
					Job<?> currentJob = job;
					Job<?> currentJobParent;

					while ((currentJobParent = currentJob.getParent()) != null) {
						if (!currentJobParent.getSettings().isVisualPassthrough() || gui.getMatcher().debugMode) {
							nestLevel++;
						}

						currentJob = currentJobParent;
					}

					BorderPane.setMargin(vbox, new Insets(0, 0, GuiConstants.padding, GuiConstants.padding * (nestLevel * 5)));
					setGraphic(borderPane);
				}
			}
		}
	}

	private final Gui gui;
	private final ObservableList<Job<?>> jobs = FXCollections.observableArrayList();
}
