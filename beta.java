package com.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.media.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.effect.DropShadow;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Modality;
import javafx.util.Duration;

import java.io.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class AuroraPlayer extends Application {

    // Data
    private final ObservableList<File> allSongs = FXCollections.observableArrayList();
    private final ObservableList<File> currentPlaylist = FXCollections.observableArrayList();
    private final Map<String, ObservableList<File>> playlists = new HashMap<>();
    private final Map<String, String> urlMappings = new HashMap<>();
    private String currentPlaylistName = null;

    // Playback state
    private int currentIndex = 0;
    private MediaPlayer mediaPlayer;
    private boolean isShuffleOn = false;
    private RepeatMode repeatMode = RepeatMode.OFF;
    private final List<Integer> shuffleOrder = new ArrayList<>();
    private int shuffleIndex = 0;
    private final Set<Integer> playedIndices = new HashSet<>();
    private final AtomicBoolean isUpdatingProgress = new AtomicBoolean(false);

    // UI Components
    private final Label trackTitle = new Label("Not Playing");
    private final Label trackArtist = new Label("Unknown Artist");
    private final ImageView albumArt = new ImageView();
    private final Slider progressSlider = new Slider();
    private final Slider volumeSlider = new Slider(0, 1, 0.5);
    private final Label currentTimeLabel = new Label("0:00");
    private final Label totalTimeLabel = new Label("0:00");
    private final ListView<String> contentView = new ListView<>();
    private Button playBtn;
    private Button shuffleBtn;
    private Button repeatBtn;
    private VBox mainContent;
    private StackPane albumPane;
    private Button tabSongs;
    private Button tabPlaylist;
    private HBox actionButtonBox;

    // UI State
    private String currentView = "SONGS";
    private double xOffset = 0;
    private double yOffset = 0;
    private BorderPane mainLayout;
    private final Label titleLabel = new Label("AURORA");
    private final Label contentTitle = new Label("Songs");

    // Style constants
    private static final String ACTIVE_TAB_STYLE = "-fx-background-color:#282828; -fx-text-fill:#1DB954; -fx-font-size:14px; -fx-font-weight:bold; -fx-alignment:center; -fx-padding:10 20 10 20;";
    private static final String INACTIVE_TAB_STYLE = "-fx-background-color:transparent; -fx-text-fill:#b3b3b3; -fx-font-size:14px; -fx-font-weight:bold; -fx-alignment:center; -fx-padding:10 20 10 20;";
    private static final String ACTIVE_CONTROL_STYLE = "-fx-background-color:#1DB954; -fx-text-fill:white; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-alignment:center; -fx-border-width:0; -fx-background-radius:8;";
    private static final String INACTIVE_CONTROL_STYLE = "-fx-background-color:#282828; -fx-text-fill:#b3b3b3; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-alignment:center; -fx-border-width:0; -fx-background-radius:8;";
    private static final String REPEAT_ONE_STYLE = "-fx-background-color:#00ff88; -fx-text-fill:black; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-alignment:center; -fx-border-width:0; -fx-background-radius:8;";
    private static final String DATA_FILE = System.getProperty("user.home") + "/.aurora_player_data.dat";

    enum RepeatMode {
        OFF, ALL, ONE
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        loadDataFromFile();
        initializeUI(stage);
        updateContentView();
        updateActionButtons();
    }

    private void initializeUI(Stage stage) {
        VBox titleBar = createTitleBar(stage);
        VBox leftSidebar = createLeftSidebar(stage);
        VBox rightSidebar = createRightSidebar();
        VBox bottomBar = createBottomBar();

        mainContent = createMainContent();

        mainLayout = new BorderPane();
        mainLayout.setTop(titleBar);
        mainLayout.setLeft(leftSidebar);
        mainLayout.setCenter(mainContent);
        mainLayout.setRight(rightSidebar);
        mainLayout.setBottom(bottomBar);
        mainLayout.setStyle("-fx-background-color:#121212;");

        Scene scene = new Scene(mainLayout, 1100, 680);
        loadCustomFont();
        scene.getRoot().setStyle("-fx-font-family: 'Noto Sans', 'Segoe UI', Arial; -fx-font-smoothing-type: gray;");
        System.setProperty("prism.lcdtext", "false");

        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.SPACE) {
                togglePlay();
                e.consume();
            }
        });

        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(scene);
        stage.setTitle("AURORA");
        setApplicationIcon(stage);
        stage.setOnCloseRequest(e -> saveDataToFile());
        stage.show();
    }

    private VBox createTitleBar(Stage stage) {
        HBox titleBar = new HBox();
        titleBar.setStyle("-fx-background-color:#000000; -fx-padding:10 15 10 15;");
        titleBar.setAlignment(Pos.CENTER_LEFT);

        loadTitleIcon();
        titleLabel.setContentDisplay(ContentDisplay.LEFT);
        titleLabel.setStyle("-fx-text-fill: #1DB954; -fx-font-size:18px; -fx-font-weight:bold; -fx-padding:0 12 0 0;");
        titleLabel.setEffect(new DropShadow(6, Color.rgb(0, 0, 0, 0.6)));

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        Button minimizeBtn = createTitleBarButton("ー", e -> stage.setIconified(true));
        Button maximizeBtn = createTitleBarButton("🗖", e -> stage.setMaximized(!stage.isMaximized()));
        Button closeBtn = createTitleBarButton("✕", e -> {
            saveDataToFile();
            Platform.exit();
        });

        titleBar.getChildren().addAll(titleLabel, titleSpacer, minimizeBtn, maximizeBtn, closeBtn);
        makeDraggable(titleBar, stage);

        return new VBox(titleBar);
    }

    private Button createTitleBarButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color:transparent; -fx-text-fill:#b3b3b3; -fx-font-size:16px; -fx-cursor:hand; -fx-padding:5 10 5 10;");
        btn.setOnAction(handler);
        applyHoverEffect(btn);
        return btn;
    }

    private void makeDraggable(HBox titleBar, Stage stage) {
        titleBar.setOnMousePressed(e -> {
            xOffset = e.getSceneX();
            yOffset = e.getSceneY();
        });
        titleBar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - xOffset);
            stage.setY(e.getScreenY() - yOffset);
        });
    }

    private VBox createLeftSidebar(Stage stage) {
        tabSongs = createTabButton("SONG", this::switchToSongsView);
        tabPlaylist = createTabButton("PLAYLIST", this::switchToPlaylistsView);
        tabSongs.setStyle(ACTIVE_TAB_STYLE);

        VBox menuBox = new VBox(5, tabSongs, tabPlaylist);
        menuBox.setPadding(new Insets(10));
        menuBox.setAlignment(Pos.CENTER);

        Button addFileBtn = createUploadButton("FILE", e -> handleAddFile(stage));
        Button addUrlBtn = createUploadButton("URL", e -> addFromUrl());

        VBox uploadBox = new VBox(8, addFileBtn, addUrlBtn);
        uploadBox.setPadding(new Insets(20));

        Region leftSpacer = new Region();
        VBox.setVgrow(leftSpacer, Priority.ALWAYS);

        VBox leftSidebar = new VBox(menuBox, leftSpacer, uploadBox);
        leftSidebar.setStyle("-fx-background-color:#000000;");
        leftSidebar.setPrefWidth(220);

        titleLabel.translateXProperty().bind(
                leftSidebar.widthProperty().divide(2).subtract(titleLabel.widthProperty().divide(1.5))
        );

        return leftSidebar;
    }

    private Button createTabButton(String text, Runnable action) {
        Button btn = new Button(text);
        btn.setStyle(INACTIVE_TAB_STYLE);
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(e -> {
            action.run();
            updateTabStyles();
        });
        applyHoverEffect(btn);
        return btn;
    }

    private Button createUploadButton(String text, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color:transparent; -fx-text-fill:#1DB954; -fx-font-weight:bold; -fx-font-size:12px; -fx-padding:8 15 8 15; -fx-border-color:#1DB954; -fx-border-width:1; -fx-border-radius:20; -fx-background-radius:20;");
        btn.setMaxWidth(Double.MAX_VALUE);
        btn.setOnAction(handler);
        applyHoverEffect(btn);
        return btn;
    }

    private void handleAddFile(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Music Files");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.m4a"));
        List<File> files = fc.showOpenMultipleDialog(stage);
        
        if (files == null || files.isEmpty()) return;

        if ("PLAYLIST_DETAIL".equals(currentView) && currentPlaylistName != null) {
            addFilesToPlaylist(files, currentPlaylistName);
        } else {
            addFilesToLibrary(files);
        }
    }

    private void addFilesToPlaylist(List<File> files, String playlistName) {
        ObservableList<File> playlist = playlists.get(playlistName);
        if (playlist == null) return;

        int added = 0;
        for (File f : files) {
            if (!playlist.contains(f)) {
                playlist.add(f);
                added++;
            }
        }

        if (added > 0) {
            saveDataToFile();
            openPlaylist(playlistName);
            showAlert("Success", added + " song(s) added to playlist!");
        }
    }

    private void addFilesToLibrary(List<File> files) {
        int added = 0;
        for (File f : files) {
            if (!allSongs.contains(f)) {
                allSongs.add(f);
                added++;
            }
        }

        if (added > 0) {
            saveDataToFile();
            updateContentView();
            
            if (allSongs.size() == files.size() && currentPlaylist.isEmpty()) {
                currentPlaylist.setAll(allSongs);
                currentIndex = 0;
                playTrack();
            }
            showAlert("Success", added + " file(s) added.");
        }
    }

    private VBox createRightSidebar() {
        trackTitle.setStyle("-fx-text-fill:white; -fx-font-size:16px; -fx-font-weight:bold;");
        trackTitle.setWrapText(true);
        trackTitle.setMaxWidth(180);
        trackTitle.setAlignment(Pos.CENTER);

        trackArtist.setStyle("-fx-text-fill:#b3b3b3; -fx-font-size:13px;");
        trackArtist.setWrapText(true);
        trackArtist.setMaxWidth(180);
        trackArtist.setAlignment(Pos.CENTER);

        VBox trackBox = new VBox(5, trackTitle, trackArtist);
        trackBox.setAlignment(Pos.TOP_CENTER);
        trackBox.setPadding(new Insets(10, 0, 20, 0));

        albumPane = new StackPane();
        albumPane.setPrefSize(180, 180);
        updateAlbumArt(null);

        Label volumeLabel = new Label("Volume");
        volumeLabel.setStyle("-fx-text-fill:#b3b3b3; -fx-font-size:12px; -fx-font-weight:bold;");

        volumeSlider.setStyle("-fx-background-color: transparent; -fx-control-inner-background: #1a1a1a; -fx-accent: #00ff88; -fx-background-radius: 10; -fx-padding: 5;");
        volumeSlider.setMaxWidth(150);
        volumeSlider.valueProperty().addListener((obs, old, newVal) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(newVal.doubleValue());
            }
        });

        VBox volumeBox = new VBox(8, volumeLabel, volumeSlider);
        volumeBox.setAlignment(Pos.CENTER);
        volumeBox.setPadding(new Insets(20, 0, 0, 0));

        Region rightSpacer = new Region();
        VBox.setVgrow(rightSpacer, Priority.ALWAYS);

        VBox rightSidebar = new VBox(trackBox, albumPane, volumeBox, rightSpacer);
        rightSidebar.setPadding(new Insets(20));
        rightSidebar.setStyle("-fx-background-color:#121212;");
        rightSidebar.setAlignment(Pos.TOP_CENTER);
        rightSidebar.setPrefWidth(220);

        return rightSidebar;
    }

    private VBox createMainContent() {
        contentTitle.setStyle("-fx-text-fill:white; -fx-font-size:20px; -fx-font-weight:bold;");

        actionButtonBox = new HBox(10);
        actionButtonBox.setAlignment(Pos.CENTER_LEFT);
        actionButtonBox.setPadding(new Insets(5, 0, 5, 0));

        HBox headerBox = new HBox(20, actionButtonBox);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(actionButtonBox, Priority.ALWAYS);

        setupContentView();

        VBox content = new VBox(15, headerBox, contentView);
        content.setStyle("-fx-background-color:#181818;");
        content.setPadding(new Insets(20));
        VBox.setVgrow(contentView, Priority.ALWAYS);

        return content;
    }

    private void setupContentView() {
        contentView.setStyle("-fx-background-color:#181818; -fx-control-inner-background:#181818; -fx-text-fill:white; -fx-font-size:13px; -fx-border-color:transparent;");
        contentView.setPlaceholder(new Label("No songs added.\nClick 'FILE' or 'URL' to get started."));
        
        contentView.setCellFactory(lv -> new ListCell<String>() {
            private final HBox hbox = new HBox(8);
            private final Label lbl = new Label();
            private final Button delBtn = new Button("DELETE");

            {
                lbl.setStyle("-fx-text-fill:white; -fx-font-size:13px;");
                lbl.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(lbl, Priority.ALWAYS);

                delBtn.setStyle("-fx-background-color:#282828; -fx-text-fill:#e03b3b; -fx-font-size:11px; -fx-font-weight:bold; -fx-padding:6 12 6 12; -fx-background-radius:15; -fx-cursor:hand;");
                applyHoverEffect(delBtn);
                delBtn.setOnAction(e -> handleDeleteButtonClick(getIndex(), getItem()));

                hbox.getChildren().addAll(lbl, delBtn);
                hbox.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    lbl.setText(item);
                    setGraphic(hbox);
                }
            }
        });

        contentView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) {
                handleDoubleClick();
            }
        });
    }

    private void handleDeleteButtonClick(int idx, String item) {
        if (idx < 0 || item == null) return;

        switch (currentView) {
            case "SONGS":
                handleSongDelete(idx, item);
                break;
            case "PLAYLISTS":
                handlePlaylistDelete(item);
                break;
            case "PLAYLIST_DETAIL":
                handlePlaylistItemDelete(idx);
                break;
        }
    }

    private void handleSongDelete(int idx, String item) {
        if (showCustomConfirmDialog("Delete Song", "Delete " + item + "?", "This action cannot be undone.")) {
            if (idx >= 0 && idx < allSongs.size()) {
                File removed = allSongs.remove(idx);
                currentPlaylist.remove(removed);
                saveDataToFile();
                updateContentView();
            }
        }
    }

    private void handlePlaylistDelete(String item) {
        int pidx = item.lastIndexOf(" (");
        String playlistName = pidx > 0 ? item.substring(0, pidx) : item;
        
        if (showCustomConfirmDialog("Delete Playlist", "Delete playlist '" + playlistName + "'?", "This action cannot be undone.")) {
            playlists.remove(playlistName);
            saveDataToFile();
            updateContentView();
        }
    }

    private void handlePlaylistItemDelete(int idx) {
        ObservableList<File> playlist = playlists.get(currentPlaylistName);
        if (playlist != null && idx >= 0 && idx < playlist.size()) {
            playlist.remove(idx);
            saveDataToFile();
            openPlaylist(currentPlaylistName);
        }
    }

    private VBox createBottomBar() {
        shuffleBtn = createControlButton("Shuffle", INACTIVE_CONTROL_STYLE);
        Button prevBtn = createControlButton("Prev", INACTIVE_CONTROL_STYLE);
        playBtn = createPlayButton();
        Button nextBtn = createControlButton("Next", INACTIVE_CONTROL_STYLE);
        repeatBtn = createControlButton("Repeat", INACTIVE_CONTROL_STYLE);

        shuffleBtn.setOnAction(e -> toggleShuffle());
        prevBtn.setOnAction(e -> playPrev());
        playBtn.setOnAction(e -> togglePlay());
        nextBtn.setOnAction(e -> playNext());
        repeatBtn.setOnAction(e -> toggleRepeat());

        HBox controlBox = new HBox(20, shuffleBtn, prevBtn, playBtn, nextBtn, repeatBtn);
        controlBox.setAlignment(Pos.CENTER);
        controlBox.setPadding(new Insets(10, 0, 5, 0));

        currentTimeLabel.setStyle("-fx-text-fill:#b3b3b3; -fx-font-size:11px;");
        totalTimeLabel.setStyle("-fx-text-fill:#b3b3b3; -fx-font-size:11px;");

        progressSlider.setStyle("-fx-background-color: transparent; -fx-control-inner-background: #1a1a1a; -fx-accent: #00ff88; -fx-background-radius: 10; -fx-padding: 5;");
        HBox.setHgrow(progressSlider, Priority.ALWAYS);
        
        progressSlider.setOnMousePressed(e -> seekToPosition());
        progressSlider.setOnMouseDragged(e -> seekToPosition());

        HBox progressBox = new HBox(10, currentTimeLabel, progressSlider, totalTimeLabel);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(new Insets(0, 20, 0, 20));

        VBox bottomBar = new VBox(controlBox, progressBox);
        bottomBar.setStyle("-fx-background-color:#181818; -fx-border-color:#282828; -fx-border-width:1 0 0 0;");
        bottomBar.setPadding(new Insets(15));
        bottomBar.setAlignment(Pos.CENTER);

        return bottomBar;
    }

    private Button createControlButton(String text, String style) {
        Button btn = new Button(text);
        btn.setMinSize(80, 40);
        btn.setStyle(style);
        applyHoverEffect(btn);
        return btn;
    }

    private Button createPlayButton() {
        Button btn = new Button("▶");
        btn.setMinSize(60, 50);
        btn.setStyle("-fx-background-color:#1DB954; -fx-text-fill:white; -fx-font-size:14px; -fx-font-weight:bold; -fx-background-radius:25; -fx-cursor:hand; -fx-alignment:center;");
        applyHoverEffect(btn);
        return btn;
    }

    private void seekToPosition() {
        if (mediaPlayer != null && !isUpdatingProgress.get()) {
            mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
        }
    }

    // View Management
    private void updateTabStyles() {
        if (currentView.equals("SONGS")) {
            setTabActive(tabSongs, true);
            setTabActive(tabPlaylist, false);
        } else {
            setTabActive(tabPlaylist, true);
            setTabActive(tabSongs, false);
        }
    }

    private void setTabActive(Button tab, boolean active) {
        String style = active ? ACTIVE_TAB_STYLE : INACTIVE_TAB_STYLE;
        tab.setStyle(style);
        tab.getProperties().put("active", active);
        tab.getProperties().put("activeStyle", style);
    }

    private void switchToSongsView() {
        currentView = "SONGS";
        updateContentView();
        updateActionButtons();
    }

    private void switchToPlaylistsView() {
        currentView = "PLAYLISTS";
        updateContentView();
        updateActionButtons();
    }

    private void updateActionButtons() {
        actionButtonBox.getChildren().clear();

        if (currentView.equals("PLAYLISTS")) {
            Button createBtn = createActionButton("+");
            createBtn.setOnAction(e -> createNewPlaylist());
            actionButtonBox.getChildren().add(createBtn);
        } else if (currentView.equals("PLAYLIST_DETAIL")) {
            Button backBtn = createActionButton("←");
            backBtn.setOnAction(e -> {
                switchToPlaylistsView();
                updateTabStyles();
            });

            Button playAllBtn = createActionButton("▶");
            playAllBtn.setOnAction(e -> playPlaylist());

            actionButtonBox.getChildren().addAll(backBtn, playAllBtn);
        }
    }

    private void playPlaylist() {
        ObservableList<File> playlist = playlists.get(currentPlaylistName);
        if (playlist != null && !playlist.isEmpty()) {
            currentPlaylist.setAll(playlist);
            currentIndex = 0;
            playedIndices.clear();
            if (isShuffleOn) generateShuffleOrder();
            playTrack();
        }
    }

    private Button createActionButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color:#282828; -fx-text-fill:#1DB954; -fx-font-size:11px; -fx-font-weight:bold; -fx-padding:6 12 6 12; -fx-background-radius:15; -fx-cursor:hand;");
        applyHoverEffect(btn);
        return btn;
    }

    private void updateContentView() {
        contentView.getItems().clear();

        if (currentView.equals("SONGS")) {
            populateSongsList();
        } else {
            populatePlaylistsList();
        }
    }

    private void populateSongsList() {
        for (int i = 0; i < allSongs.size(); i++) {
            String displayName = stripExtension(allSongs.get(i).getName());
            contentView.getItems().add((i + 1) + ". " + displayName);
        }
        
        if (allSongs.isEmpty()) {
            contentView.setPlaceholder(new Label("No songs added.\nClick 'FILE' or 'URL' to get started."));
        }
    }

    private void populatePlaylistsList() {
        for (String playlistName : playlists.keySet()) {
            ObservableList<File> pl = playlists.get(playlistName);
            int size = pl == null ? 0 : pl.size();
            contentView.getItems().add(playlistName + " (" + size + " songs)");
        }
        
        if (playlists.isEmpty()) {
            contentView.setPlaceholder(new Label("No playlists yet.\nClick '+' to start."));
        }
    }

    private void handleDoubleClick() {
        int selected = contentView.getSelectionModel().getSelectedIndex();
        if (selected < 0) return;

        switch (currentView) {
            case "SONGS":
                playSongAtIndex(selected);
                break;
            case "PLAYLISTS":
                openPlaylistAtIndex(selected);
                break;
            case "PLAYLIST_DETAIL":
                playPlaylistSongAtIndex(selected);
                break;
        }
    }

    private void playSongAtIndex(int index) {
        currentPlaylist.setAll(allSongs);
        currentIndex = index;
        playedIndices.clear();
        if (isShuffleOn) generateShuffleOrder();
        playTrack();
    }

    private void openPlaylistAtIndex(int index) {
        String item = contentView.getItems().get(index);
        int idx = item.lastIndexOf(" (");
        String playlistName = idx > 0 ? item.substring(0, idx) : item;
        openPlaylist(playlistName);
    }

    private void playPlaylistSongAtIndex(int index) {
        ObservableList<File> playlist = playlists.get(currentPlaylistName);
        if (playlist == null || playlist.isEmpty()) return;
        
        currentPlaylist.setAll(playlist);
        currentIndex = index;
        playedIndices.clear();
        if (isShuffleOn) generateShuffleOrder();
        playTrack();
    }

    private void createNewPlaylist() {
        Optional<String> result = showCustomTextInputDialog("Create Playlist", "Enter playlist name:");
        result.ifPresent(name -> {
            if (!name.trim().isEmpty() && !playlists.containsKey(name)) {
                playlists.put(name, FXCollections.observableArrayList());
                saveDataToFile();
                updateContentView();
                showAlert("Success", "Playlist '" + name + "' created!");
            } else {
                showAlert("Error", "Invalid or duplicate playlist name.");
            }
        });
    }

    private void openPlaylist(String playlistName) {
        currentView = "PLAYLIST_DETAIL";
        currentPlaylistName = playlistName;
        contentTitle.setText("♫ " + playlistName);

        ObservableList<File> playlist = playlists.get(playlistName);
        contentView.getItems().clear();

        if (playlist == null) {
            contentView.setPlaceholder(new Label("Playlist not found."));
            updateActionButtons();
            return;
        }

        for (int i = 0; i < playlist.size(); i++) {
            String displayName = stripExtension(playlist.get(i).getName());
            contentView.getItems().add((i + 1) + ". " + displayName);
        }

        if (playlist.isEmpty()) {
            contentView.setPlaceholder(new Label("Playlist is empty.\nAdd songs using 'FILE' or 'URL' button."));
        }

        updateActionButtons();
    }

// ------------------------- 플레이리스트 관련 -------------------------
private void addFromUrl() {
    Optional<String> result = showCustomTextInputDialog("Add Music from URL", "Enter YouTube URL:");
    result.ifPresent(url -> {
        if (!url.trim().isEmpty()) {
            downloadAndPlayYoutubeAudio(url.trim());
        }
    });
}

// -----------------------------
// 공용 runProcessWithProgress
// -----------------------------
private int runProcessWithProgress(String[] command, ProgressBar progressBar, Label progressLabel, double min, double max) throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder(command);
    pb.redirectErrorStream(true);
    Process process = pb.start();

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String line;
        int progressCount = 0;
        while ((line = reader.readLine()) != null) {
            System.out.println(line);
            progressCount++;
            int finalProgressCount = progressCount;
            String finalLine = line;
            Platform.runLater(() -> {
                double progress = min + Math.min(finalProgressCount / 50.0, max - min);
                progressBar.setProgress(progress);
                progressLabel.setText(finalLine);
            });
        }
    }

    int exitCode = process.waitFor();
    return exitCode;
}

// -----------------------------
// showDownloadProgressWindow에서 호출 예시
// -----------------------------
private void showDownloadProgressWindow(String url, Consumer<File> onComplete) {
    Stage progressStage = new Stage();
    progressStage.initModality(Modality.APPLICATION_MODAL);
    progressStage.initStyle(StageStyle.UNDECORATED);
    progressStage.setTitle("Downloading YouTube Audio");

    VBox root = new VBox(10);
    root.setPadding(new Insets(20));
    root.setStyle("-fx-background-color:#121212; -fx-border-color:#282828; -fx-border-width:1;");

    ProgressBar progressBar = new ProgressBar(0);
    progressBar.setPrefWidth(300);
    progressBar.setStyle("-fx-accent: #1DB954;");

    Label progressLabel = new Label("Starting download...");
    progressLabel.setStyle("-fx-text-fill:#b3b3b3; -fx-font-size:12px;");

    root.getChildren().addAll(progressLabel, progressBar);
    Scene scene = new Scene(root);
    progressStage.setScene(scene);
    progressStage.show();

    new Thread(() -> {
        try {
            File tempMp3 = File.createTempFile("yt_audio_temp", ".mp3");
            File tempWav = File.createTempFile("yt_audio_temp", ".wav");

            if (tempMp3.exists()) tempMp3.delete();
            if (tempWav.exists()) tempWav.delete();

            // 다운로드
            String[] downloadCmd = {
                    "yt-dlp",
                    "-f", "bestaudio",
                    "--extract-audio",
                    "--audio-format", "mp3",
                    "-o", tempMp3.getAbsolutePath(),
                    url
            };
            int downloadExit = runProcessWithProgress(downloadCmd, progressBar, progressLabel, 0, 0.5);
            if (downloadExit != 0 || !tempMp3.exists() || tempMp3.length() == 0)
                throw new IOException("yt-dlp failed to download audio.");

            Platform.runLater(() -> progressLabel.setText("Converting to WAV..."));

            // 변환
            String[] convertCmd = {
                    "ffmpeg", "-y", "-i", tempMp3.getAbsolutePath(),
                    "-ar", "44100", "-ac", "2", tempWav.getAbsolutePath()
            };
            int convertExit = runProcessWithProgress(convertCmd, progressBar, progressLabel, 0.5, 1.0);
            if (convertExit != 0 || !tempWav.exists() || tempWav.length() == 0)
                throw new IOException("ffmpeg failed to convert audio.");

            Platform.runLater(() -> {
                File urlFile = new File("URL: " + url);
                urlMappings.put(urlFile.getAbsolutePath(), tempWav.getAbsolutePath());
                if ("PLAYLIST_DETAIL".equals(currentView) && currentPlaylistName != null) {
                    addUrlToPlaylist(urlFile, currentPlaylistName);
                } else {
                    addUrlToLibrary(urlFile);
                }

                progressStage.close();
                if (onComplete != null) onComplete.accept(tempWav);
            });

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                progressStage.close();
                showAlert("Error", "Failed to download/convert audio:\n" + e.getMessage());
            });
        }
    }).start();
}

// ------------------------- 다운로드 후 재생 -------------------------
private void downloadAndPlayYoutubeAudio(String url) {
    showDownloadProgressWindow(url, tempWav -> playDownloadedFile(tempWav));
}

// ------------------------- MediaPlayer 재생 -------------------------
private void playDownloadedFile(File file) {
    disposeMediaPlayer();

    try {
        Media media = new Media(file.toURI().toString());
        mediaPlayer = new MediaPlayer(media);
        mediaPlayer.setVolume(volumeSlider.getValue());

        mediaPlayer.setOnReady(() -> {
            updateTrackInfo(stripExtension(file.getName()));
            mediaPlayer.play();
            playBtn.setText("■");
        });

        mediaPlayer.setOnEndOfMedia(this::playNext);
        mediaPlayer.setOnError(() -> {
            showAlert("Playback Error", "Could not play file:\n" + mediaPlayer.getError());
            disposeMediaPlayer();
        });

    } catch (Exception e) {
        showAlert("Playback Error", "Could not play file:\n" + e.getMessage());
        e.printStackTrace();
    }
}

// ------------------------- 기존 플레이리스트 재생 -------------------------
private void playTrack() {
    if (currentPlaylist.isEmpty()) return;

    disposeMediaPlayer();
    File file = currentPlaylist.get(currentIndex);

    try {
        String source = file.getName().startsWith("URL: ") ? urlMappings.get(file.getAbsolutePath()) : file.toURI().toString();

        if (source == null) {
            showAlert("Error", "Failed to get stream URL for playback.");
            return;
        }

        Media media = new Media(source);
        mediaPlayer = new MediaPlayer(media);
        mediaPlayer.setVolume(volumeSlider.getValue());

        String baseName = stripExtension(file.getName());
        updateTrackInfo(baseName);
        playBtn.setText("■");
        playedIndices.add(currentIndex);

        setupMetadataListener(media);
        setupTimeListener();
        setupMediaPlayerCallbacks();

    } catch (Exception e) {
        showAlert("Playback Error", "Could not play file: " + e.getMessage());
        e.printStackTrace();
    }
}

private void addUrlToPlaylist(File urlFile, String playlistName) {
    ObservableList<File> pl = playlists.get(playlistName);
    if (pl != null && !pl.contains(urlFile)) {
        pl.add(urlFile);
        saveDataToFile();
        openPlaylist(playlistName);
        showAlert("Success", "YouTube audio added to playlist!");
    }
}

private void addUrlToLibrary(File urlFile) {
    if (!allSongs.contains(urlFile)) {
        allSongs.add(urlFile);
        saveDataToFile();
        updateContentView();
        showAlert("Success", "YouTube audio added to library!");
    }
}

    private void disposeMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }

    private void updateTrackInfo(String baseName) {
        String displayName = baseName.length() > 30 ? baseName.substring(0, 27) + "..." : baseName;
        trackTitle.setText(displayName);
        trackArtist.setText("Unknown Artist");
    }

    private void setupMetadataListener(Media media) {
        media.getMetadata().addListener((javafx.collections.MapChangeListener.Change<? extends String, ? extends Object> change) -> {
            if (change.wasAdded()) {
                String key = change.getKey();
                Object value = change.getValueAdded();
                
                Platform.runLater(() -> {
                    switch (key) {
                        case "image":
                            updateAlbumArt((Image) value);
                            break;
                        case "artist":
                            trackArtist.setText((String) value);
                            break;
                        case "title":
                            trackTitle.setText((String) value);
                            break;
                    }
                });
            }
        });
    }

    private void setupTimeListener() {
        mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
            if (!progressSlider.isValueChanging() && !isUpdatingProgress.get()) {
                isUpdatingProgress.set(true);
                Platform.runLater(() -> {
                    progressSlider.setValue(newTime.toSeconds());
                    currentTimeLabel.setText(formatTime(newTime));
                    isUpdatingProgress.set(false);
                });
            }
        });
    }

    private void setupMediaPlayerCallbacks() {
        mediaPlayer.setOnReady(() -> {
            Duration totalDuration = mediaPlayer.getTotalDuration();
            progressSlider.setMax(totalDuration.toSeconds());
            totalTimeLabel.setText(formatTime(totalDuration));
            mediaPlayer.play();
        });

        mediaPlayer.setOnEndOfMedia(() -> {
            if (repeatMode == RepeatMode.ONE) {
                mediaPlayer.seek(Duration.ZERO);
                mediaPlayer.play();
            } else {
                playNext();
            }
        });
    }

    private void updateAlbumArt(Image image) {
        albumPane.getChildren().clear();
        Rectangle albumRect = new Rectangle(180, 180);
        albumRect.setFill(Color.web("#282828"));
        albumRect.setArcWidth(8);
        albumRect.setArcHeight(8);

        if (image != null) {
            albumArt.setImage(image);
            albumArt.setFitWidth(180);
            albumArt.setFitHeight(180);
            albumArt.setPreserveRatio(true);
            
            Rectangle clip = new Rectangle(180, 180);
            clip.setArcWidth(8);
            clip.setArcHeight(8);
            albumArt.setClip(clip);
            
            albumPane.getChildren().add(albumArt);
        } else {
            Label noAlbum = new Label("No Cover");
            noAlbum.setStyle("-fx-text-fill:#535353; -fx-font-size:16px; -fx-font-weight:bold;");
            albumPane.getChildren().addAll(albumRect, noAlbum);
        }
    }

    private void togglePlay() {
        if (mediaPlayer == null) {
            initializePlayback();
            return;
        }

        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            pausePlayback();
        } else {
            resumePlayback();
        }
    }

    private void initializePlayback() {
        if (!currentPlaylist.isEmpty()) {
            playTrack();
        } else if (!allSongs.isEmpty()) {
            currentPlaylist.setAll(allSongs);
            currentIndex = 0;
            playedIndices.clear();
            if (isShuffleOn) generateShuffleOrder();
            playTrack();
        }
    }

    private void pausePlayback() {
        mediaPlayer.pause();
        playBtn.setText("▶");
        playBtn.getProperties().put("active", Boolean.FALSE);
    }

    private void resumePlayback() {
        mediaPlayer.play();
        playBtn.setText("■");
        String playActive = "-fx-background-color:#1DB954; -fx-text-fill:white; -fx-font-size:14px; -fx-font-weight:bold; -fx-min-width:60px; -fx-min-height:50px; -fx-background-radius:25; -fx-cursor:hand; -fx-alignment:center;";
        playBtn.setStyle(playActive);
        playBtn.getProperties().put("active", Boolean.TRUE);
        playBtn.getProperties().put("activeStyle", playActive);
    }

    private void playPrev() {
        if (currentPlaylist.isEmpty()) return;

        if (isShuffleOn && !shuffleOrder.isEmpty()) {
            shuffleIndex = (shuffleIndex - 1 + shuffleOrder.size()) % shuffleOrder.size();
            currentIndex = shuffleOrder.get(shuffleIndex);
        } else {
            currentIndex = (currentIndex - 1 + currentPlaylist.size()) % currentPlaylist.size();
        }
        playTrack();
    }

    private void playNext() {
        if (currentPlaylist.isEmpty()) return;

        if (repeatMode == RepeatMode.ONE) {
            playTrack();
            return;
        }

        if (isShuffleOn && !shuffleOrder.isEmpty()) {
            handleShuffleNext();
        } else {
            handleNormalNext();
        }
    }

    private void handleShuffleNext() {
        if (playedIndices.size() >= currentPlaylist.size()) {
            if (repeatMode == RepeatMode.ALL) {
                playedIndices.clear();
                generateShuffleOrder();
            } else {
                stopPlayback();
                return;
            }
        }
        shuffleIndex = (shuffleIndex + 1) % shuffleOrder.size();
        currentIndex = shuffleOrder.get(shuffleIndex);
        playTrack();
    }

    private void handleNormalNext() {
        currentIndex = (currentIndex + 1) % currentPlaylist.size();
        if (currentIndex == 0 && repeatMode == RepeatMode.OFF) {
            stopPlayback();
        } else {
            playTrack();
        }
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            playBtn.setText("▶");
        }
    }

    private void toggleShuffle() {
        isShuffleOn = !isShuffleOn;
        
        if (isShuffleOn) {
            shuffleBtn.setText("Shuffle");
            shuffleBtn.setStyle(ACTIVE_CONTROL_STYLE);
            shuffleBtn.getProperties().put("active", Boolean.TRUE);
            shuffleBtn.getProperties().put("activeStyle", ACTIVE_CONTROL_STYLE);
            playedIndices.clear();
            generateShuffleOrder();
        } else {
            shuffleBtn.setText("Shuffle");
            shuffleBtn.setStyle(INACTIVE_CONTROL_STYLE);
            shuffleBtn.getProperties().put("active", Boolean.FALSE);
            shuffleBtn.getProperties().put("activeStyle", INACTIVE_CONTROL_STYLE);
            shuffleOrder.clear();
            playedIndices.clear();
        }
    }

    private void generateShuffleOrder() {
        shuffleOrder.clear();
        List<Integer> indices = new ArrayList<>();
        
        for (int i = 0; i < currentPlaylist.size(); i++) {
            if (i != currentIndex) {
                indices.add(i);
            }
        }
        
        applyHoverEffect(shuffleBtn);
        Collections.shuffle(indices);
        shuffleOrder.add(currentIndex);
        shuffleOrder.addAll(indices);
        shuffleIndex = 0;
    }

    private void toggleRepeat() {
        applyHoverEffect(repeatBtn);
        switch (repeatMode) {
            case OFF:
                setRepeatMode(RepeatMode.ALL, "Repeat", ACTIVE_CONTROL_STYLE);
                break;
            case ALL:
                setRepeatMode(RepeatMode.ONE, "Repeat 1", REPEAT_ONE_STYLE);
                break;
            case ONE:
                setRepeatMode(RepeatMode.OFF, "Repeat", INACTIVE_CONTROL_STYLE);
                break;
        }
    }

    private void setRepeatMode(RepeatMode mode, String text, String style) {
        repeatMode = mode;
        repeatBtn.setText(text);
        repeatBtn.setStyle(style);
        repeatBtn.getProperties().put("active", mode != RepeatMode.OFF);
        repeatBtn.getProperties().put("activeStyle", style);
    }

    // Save/Load Data
    private void saveDataToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            oos.writeObject(convertFilesToPaths(allSongs));
            oos.writeObject(convertPlaylistsToPaths());
        } catch (IOException e) {
            System.err.println("Failed to save data: " + e.getMessage());
        }
    }

    private List<String> convertFilesToPaths(ObservableList<File> files) {
        List<String> paths = new ArrayList<>();
        for (File f : files) {
            paths.add(f.getName().startsWith("URL: ") ? f.getName() : f.getAbsolutePath());
        }
        return paths;
    }

    private Map<String, List<String>> convertPlaylistsToPaths() {
        Map<String, List<String>> playlistData = new HashMap<>();
        for (Map.Entry<String, ObservableList<File>> entry : playlists.entrySet()) {
            playlistData.put(entry.getKey(), convertFilesToPaths(entry.getValue()));
        }
        return playlistData;
    }

    @SuppressWarnings("unchecked")
    private void loadDataFromFile() {
        File dataFile = new File(DATA_FILE);
        if (!dataFile.exists()) return;

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(dataFile))) {
            List<String> songPaths = (List<String>) ois.readObject();
            loadSongsFromPaths(songPaths);

            Map<String, List<String>> playlistData = (Map<String, List<String>>) ois.readObject();
            loadPlaylistsFromData(playlistData);
        } catch (Exception e) {
            System.err.println("Failed to load data: " + e.getMessage());
        }
    }

    private void loadSongsFromPaths(List<String> songPaths) {
        allSongs.clear();
        for (String path : songPaths) {
            if (path.startsWith("URL: ")) {
                allSongs.add(new File(path));
            } else {
                File musicFile = new File(path);
                if (musicFile.exists()) {
                    allSongs.add(musicFile);
                }
            }
        }
    }

    private void loadPlaylistsFromData(Map<String, List<String>> playlistData) {
        playlists.clear();
        for (Map.Entry<String, List<String>> entry : playlistData.entrySet()) {
            ObservableList<File> playlist = FXCollections.observableArrayList();
            for (String path : entry.getValue()) {
                if (path.startsWith("URL: ")) {
                    playlist.add(new File(path));
                } else {
                    File musicFile = new File(path);
                    if (musicFile.exists()) {
                        playlist.add(musicFile);
                    }
                }
            }
            playlists.put(entry.getKey(), playlist);
        }
    }

    // UI Dialogs
    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Stage dialog = new Stage();
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initStyle(StageStyle.UNDECORATED);
            dialog.setTitle(title);

            Label lbl = new Label(content);
            lbl.setStyle("-fx-text-fill:#b3b3b3; -fx-font-size:13px;");
            lbl.setWrapText(true);
            lbl.setMaxWidth(420);

            Button ok = new Button("OK");
            ok.setStyle("-fx-background-color:#1DB954; -fx-text-fill:white; -fx-font-weight:bold; -fx-padding:6 14; -fx-background-radius:6;");
            applyHoverEffect(ok);
            ok.setOnAction(e -> dialog.close());

            HBox btnBox = new HBox(ok);
            btnBox.setAlignment(Pos.CENTER_RIGHT);
            btnBox.setPadding(new Insets(8, 0, 0, 0));

            VBox box = new VBox(10, lbl, btnBox);
            box.setPadding(new Insets(14));
            box.setStyle("-fx-background-color:#121212; -fx-border-color:#282828; -fx-border-width:1; -fx-background-radius:6;");

            dialog.setScene(new Scene(box));
            dialog.showAndWait();
        });
    }

    private Optional<String> showCustomTextInputDialog(String title, String prompt) {
        final String[] result = {null};
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setTitle(title);

        Label lbl = new Label(prompt);
        lbl.setStyle("-fx-text-fill:white; -fx-font-size:14px;");
        
        TextField input = new TextField();
        input.setPrefWidth(320);
        input.setStyle("-fx-background-color:#1a1a1a; -fx-text-fill:white; -fx-border-color:#282828;");

        Button ok = new Button("Create");
        Button cancel = new Button("Cancel");
        ok.setStyle("-fx-background-color:#1DB954; -fx-text-fill:white; -fx-font-weight:bold;");
        cancel.setStyle("-fx-background-color:#282828; -fx-text-fill:#b3b3b3;");
        
        applyHoverEffect(ok);
        applyHoverEffect(cancel);

        ok.setOnAction(e -> {
            result[0] = input.getText();
            dialog.close();
        });
        cancel.setOnAction(e -> dialog.close());

        input.setOnAction(e -> ok.fire());

        HBox buttons = new HBox(8, ok, cancel);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(12, lbl, input, buttons);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color:#121212; -fx-border-color:#282828; -fx-border-width:1; -fx-background-radius:6;");

        dialog.setScene(new Scene(box));
        dialog.showAndWait();

        return Optional.ofNullable(result[0]).map(String::trim).filter(s -> !s.isEmpty());
    }

    private boolean showCustomConfirmDialog(String title, String header, String content) {
        final boolean[] confirmed = {false};
        Stage dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initStyle(StageStyle.UNDECORATED);
        dialog.setTitle(title);

        Label lblHeader = new Label(header);
        lblHeader.setStyle("-fx-text-fill:white; -fx-font-size:14px; -fx-font-weight:bold;");
        
        Label lblContent = new Label(content);
        lblContent.setStyle("-fx-text-fill:#b3b3b3; -fx-font-size:12px;");

        Button ok = new Button("Delete");
        Button cancel = new Button("Cancel");
        ok.setStyle("-fx-background-color:#e03b3b; -fx-text-fill:white; -fx-font-weight:bold;");
        cancel.setStyle("-fx-background-color:#282828; -fx-text-fill:#b3b3b3;");
        
        applyHoverEffect(ok);
        applyHoverEffect(cancel);
        
        ok.setOnAction(e -> {
            confirmed[0] = true;
            dialog.close();
        });
        cancel.setOnAction(e -> dialog.close());

        HBox buttons = new HBox(8, cancel, ok);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(8, lblHeader, lblContent, buttons);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color:#121212; -fx-border-color:#282828; -fx-border-width:1; -fx-background-radius:6;");

        dialog.setScene(new Scene(box));
        dialog.showAndWait();

        return confirmed[0];
    }

    // Utility Methods
    private String formatTime(Duration duration) {
        if (duration == null || duration.toSeconds() < 0) return "0:00";
        int totalSeconds = (int) duration.toSeconds();
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private String stripExtension(String name) {
        if (name == null) return "";
        
        String prefix = "";
        if (name.startsWith("URL: ")) {
            prefix = "URL: ";
            name = name.substring(5);
        }
        
        int lastSep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = (lastSep >= 0) ? name.substring(lastSep + 1) : name;
        
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        
        return prefix + base;
    }

    private void applyHoverEffect(Button btn) {
        btn.setOnMouseEntered(e -> {
            btn.getProperties().put("hoverBaseStyle", btn.getStyle());
            String base = btn.getStyle() == null ? "" : btn.getStyle();
            btn.setStyle(base + " -fx-background-color:#282828; -fx-text-fill:#1DB954;");
        });
        
        btn.setOnMouseExited(e -> {
            if (Boolean.TRUE.equals(btn.getProperties().get("active"))) {
                Object activeStyle = btn.getProperties().get("activeStyle");
                if (activeStyle instanceof String) {
                    btn.setStyle((String) activeStyle);
                    return;
                }
            }
            
            Object base = btn.getProperties().get("hoverBaseStyle");
            if (base instanceof String) {
                btn.setStyle((String) base);
            }
        });
    }

    private void loadTitleIcon() {
        try {
            InputStream iconStream = getClass().getResourceAsStream("/aurora.png");
            if (iconStream != null) {
                Image icon = new Image(iconStream);
                ImageView iv = new ImageView(icon);
                iv.setFitWidth(18);
                iv.setFitHeight(18);
                iv.setPreserveRatio(true);
                titleLabel.setGraphic(iv);
            }
        } catch (Exception ex) {
            titleLabel.setGraphic(null);
        }
    }

    private void loadCustomFont() {
        try {
            InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf");
            if (fontStream != null) {
                Font.loadFont(fontStream, 12);
            }
        } catch (Exception ignored) {
        }
    }

    private void setApplicationIcon(Stage stage) {
        try {
            InputStream iconStream = getClass().getResourceAsStream("/aurora.png");
            if (iconStream != null) {
                stage.getIcons().add(new Image(iconStream));
            } else {
                stage.getIcons().add(createDefaultIcon());
            }
        } catch (Exception e) {
            stage.getIcons().add(createDefaultIcon());
        }
    }

    private Image createDefaultIcon() {
        int size = 64;
        WritableImage icon = new WritableImage(size, size);
        PixelWriter writer = icon.getPixelWriter();

        int centerX = size / 2;
        int centerY = size / 2;
        int radius = size / 2 - 4;

        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));
                if (distance <= radius) {
                    writer.setColor(x, y, Color.web("#1DB954"));
                } else {
                    writer.setColor(x, y, Color.TRANSPARENT);
                }
            }
        }

        return icon;
    }
}