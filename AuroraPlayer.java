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

public class AuroraPlayer extends Application {

    private ObservableList<File> allSongs = FXCollections.observableArrayList();
    private ObservableList<File> currentPlaylist = FXCollections.observableArrayList();
    private Map<String, ObservableList<File>> playlists = new HashMap<>();
    private String currentPlaylistName = null;

    private int currentIndex = 0;
    private MediaPlayer mediaPlayer;
    private boolean isShuffleOn = false;
    private RepeatMode repeatMode = RepeatMode.OFF;
    private List<Integer> shuffleOrder = new ArrayList<>();
    private int shuffleIndex = 0;
    private Set<Integer> playedIndices = new HashSet<>();

    private Label trackTitle = new Label("Not Playing");
    private Label trackArtist = new Label("Unknown Artist");
    private ImageView albumArt = new ImageView();
    private Slider progressSlider = new Slider();
    private Slider volumeSlider = new Slider(0, 1, 0.5);
    private Label currentTimeLabel = new Label("0:00");
    private Label totalTimeLabel = new Label("0:00");
    private ListView<String> contentView = new ListView<>();
    private Button playBtn;
    private Button shuffleBtn;
    private Button repeatBtn;
    private VBox mainContent;
    private StackPane albumPane;
    private Button tabSongs;
    private Button tabPlaylist;
    private HBox actionButtonBox;

    private String currentView = "SONGS";
    private double xOffset = 0;
    private double yOffset = 0;
    // mainLayout을 필드로 선언
    private BorderPane mainLayout;

    // 타이틀 및 콘텐츠 제목 라벨 선언
    private Label titleLabel = new Label("AURORA");
    private Label contentTitle = new Label("Songs");

    enum RepeatMode {
        OFF, ALL, ONE
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        // 데이터 로드 먼저
        loadDataFromFile();

        // ================= Custom Title Bar =================
        HBox titleBar = new HBox();
        titleBar.setStyle("-fx-background-color:#000000; -fx-padding:10 15 10 15;");
        titleBar.setAlignment(Pos.CENTER_LEFT);

        // 타이틀 라벨 스타일링: 왼쪽에 aurora.png 아이콘 추가, 글자 색상은 초록 유지 (그림자 포함)
        try {
            InputStream iconStream = getClass().getResourceAsStream("/aurora.png");
            if (iconStream != null) {
                Image icon = new Image(iconStream);
                ImageView iv = new ImageView(icon);
                iv.setFitWidth(18);
                iv.setFitHeight(18);
                iv.setPreserveRatio(true);
                titleLabel.setGraphic(iv);
            } else {
                titleLabel.setGraphic(null);
            }
        } catch (Exception ex) {
            titleLabel.setGraphic(null);
        }
        titleLabel.setContentDisplay(ContentDisplay.LEFT);
        titleLabel.setStyle("-fx-text-fill: #1DB954; -fx-font-size:18px; -fx-font-weight:bold; -fx-padding:0 12 0 0;");
        titleLabel.setEffect(new DropShadow(6, Color.rgb(0,0,0,0.6)));

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        Button minimizeBtn = new Button("ー");
        Button maximizeBtn = new Button("🗖");
        Button closeBtn = new Button("✕");

        Button[] titleButtons = {minimizeBtn, maximizeBtn, closeBtn};
        for (Button btn : titleButtons) {
            btn.setStyle("-fx-background-color:transparent; -fx-text-fill:#b3b3b3; -fx-font-size:16px; -fx-cursor:hand; -fx-padding:5 10 5 10;");
            applyLeftSidebarHover(btn);
        }

        // 애니메이션 제거: 즉시 동작으로 변경
        minimizeBtn.setOnAction(e -> stage.setIconified(true));
        maximizeBtn.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));
        closeBtn.setOnAction(e -> {
            saveDataToFile();
            Platform.exit();
        });

        titleBar.getChildren().addAll(titleLabel, titleSpacer, minimizeBtn, maximizeBtn, closeBtn);

        // Make window draggable
        titleBar.setOnMousePressed(e -> {
            xOffset = e.getSceneX();
            yOffset = e.getSceneY();
        });
        titleBar.setOnMouseDragged(e -> {
            stage.setX(e.getScreenX() - xOffset);
            stage.setY(e.getScreenY() - yOffset);
        });

        // ================= Left Sidebar =================
        tabSongs = new Button("SONG");
        tabPlaylist = new Button("PLAYLIST");
        Button[] tabs = new Button[]{tabSongs, tabPlaylist};

        for (Button b : tabs) {
            b.setStyle("-fx-background-color:transparent; -fx-text-fill:#b3b3b3; -fx-font-size:14px; -fx-font-weight:bold; -fx-alignment:center; -fx-padding:10 20 10 20; -fx-border-width:0;");
            b.setMaxWidth(Double.MAX_VALUE);
            applyLeftSidebarHover(b);
        }

        tabSongs.setOnAction(e -> {
            switchToSongsView();
            updateTabStyles();
        });

        tabPlaylist.setOnAction(e -> {
            switchToPlaylistsView();
            updateTabStyles();
        });

        tabSongs.setStyle("-fx-background-color:#282828; -fx-text-fill:#1DB954; -fx-font-size:14px; -fx-font-weight:bold; -fx-alignment:center; -fx-padding:10 20 10 20;");

        VBox menuBox = new VBox(5, tabs);
        menuBox.setPadding(new Insets(10));
        menuBox.setAlignment(Pos.CENTER); // 탭들을 수평/수직 중앙 정렬

        Button addFileBtn = new Button("FILE");
        addFileBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#1DB954; -fx-font-weight:bold; -fx-font-size:12px; -fx-padding:8 15 8 15; -fx-border-color:#1DB954; -fx-border-width:1; -fx-border-radius:20; -fx-background-radius:20;");
        addFileBtn.setMaxWidth(Double.MAX_VALUE);
        applyLeftSidebarHover(addFileBtn);
        addFileBtn.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Select Music Files");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.m4a"));
            List<File> files = fc.showOpenMultipleDialog(stage);
            if (files != null && !files.isEmpty()) {
                // 플레이리스트 상세(PLAYLIST_DETAIL)일 경우: "해당 플레이리스트에만" 추가
                if ("PLAYLIST_DETAIL".equals(currentView) && currentPlaylistName != null) {
                    ObservableList<File> pl = playlists.get(currentPlaylistName);
                    if (pl != null) {
                        int added = 0;
                        for (File f : files) {
                            if (!pl.contains(f)) { pl.add(f); added++; }
                        }
                        if (added > 0) {
                            saveDataToFile();
                            openPlaylist(currentPlaylistName);
                            showAlert("Success", added + " song(s) added to playlist!");
                        }
                    }
                } else {
                    // 일반 화면(SONGS 등): allSongs에만 추가
                    int added = 0;
                    for (File f : files) {
                        if (!allSongs.contains(f)) { allSongs.add(f); added++; }
                    }
                    if (added > 0) {
                        saveDataToFile();
                        updateContentView();
                        // 초기 자동 재생 동작 유지
                        if (allSongs.size() == files.size() && currentPlaylist.isEmpty()) {
                            currentPlaylist.setAll(allSongs);
                            currentIndex = 0;
                            playTrack();
                        }
                        showAlert("Success", added + " file(s) added.");
                    }
                }
            }
        });

        Button addUrlBtn = new Button("URL");
        addUrlBtn.setStyle("-fx-background-color:transparent; -fx-text-fill:#1DB954; -fx-font-weight:bold; -fx-font-size:12px; -fx-padding:8 15 8 15; -fx-border-color:#1DB954; -fx-border-width:1; -fx-border-radius:20; -fx-background-radius:20;");
        addUrlBtn.setMaxWidth(Double.MAX_VALUE);
        applyLeftSidebarHover(addUrlBtn);
        addUrlBtn.setOnAction(e -> addFromUrl());

        VBox uploadBox = new VBox(8, addFileBtn, addUrlBtn);
        uploadBox.setPadding(new Insets(20));

        // 탭을 왼쪽 사이드바 상단으로 배치 (파일 업로드 영역은 하단 유지)
        Region leftSpacer = new Region();
        VBox.setVgrow(leftSpacer, Priority.ALWAYS);

        VBox leftSidebar = new VBox(menuBox, leftSpacer, uploadBox);
        leftSidebar.setStyle("-fx-background-color:#000000;");
        leftSidebar.setPrefWidth(220);
        titleLabel.translateXProperty().bind(
                leftSidebar.widthProperty().divide(2).subtract(titleLabel.widthProperty().divide(1.5))
        );

        // ================= Right Sidebar =================

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

        volumeSlider.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-control-inner-background: #1a1a1a;" +
                        "-fx-accent: #00ff88;" +
                        "-fx-background-radius: 10;" +
                        "-fx-padding: 5;"
        );
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

        // ================= Main Content =================
        contentTitle.setStyle("-fx-text-fill:white; -fx-font-size:20px; -fx-font-weight:bold;");

        actionButtonBox = new HBox(10);
        actionButtonBox.setAlignment(Pos.CENTER_LEFT);
        actionButtonBox.setPadding(new Insets(5, 0, 5, 0));

        HBox headerBox = new HBox(20, actionButtonBox);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(actionButtonBox, Priority.ALWAYS);

        contentView.setStyle("-fx-background-color:#181818; -fx-control-inner-background:#181818; -fx-text-fill:white; -fx-font-size:13px; -fx-border-color:transparent;");
        contentView.setPlaceholder(new Label("No songs added.\nClick 'FILE' or 'URL' to get started."));
        // 각 항목 우측에 DELETE 버튼 표시 (빨간 텍스트)
        contentView.setCellFactory(lv -> new ListCell<String>() {
            private final HBox hbox = new HBox(8);
            private final Label lbl = new Label();
            private final Button delBtn = new Button("DELETE");

            {
                // 레이블이 가로로 확장되어 버튼을 오른쪽 끝으로 밀어내도록 설정
                lbl.setStyle("-fx-text-fill:white; -fx-font-size:13px;");
                lbl.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(lbl, Priority.ALWAYS);

                // DELETE 버튼: 호버 시 스타일 변화 제거(항상 동일한 스타일 유지)
                delBtn.setStyle("-fx-background-color:#282828; -fx-text-fill:#e03b3b; -fx-font-size:11px; -fx-font-weight:bold; -fx-padding:6 12 6 12; -fx-background-radius:15; -fx-cursor:hand;");
                applyLeftSidebarHover(delBtn);

                delBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx < 0 || getItem() == null) return;
                    // 현재 뷰에 따라 삭제 동작 분기
                    if (currentView.equals("SONGS")) {
                        // SONGS에서 삭제 시 커스텀 확인창을 띄워 확인받음
                        String itemName = (getItem() != null) ? getItem() : "this song";
                        boolean ok = showCustomConfirmDialog("Delete Song", "Delete " + itemName + "?", "This action cannot be undone.");
                        if (ok) {
                            if (idx >= 0 && idx < allSongs.size()) {
                                File removed = allSongs.remove(idx);
                                currentPlaylist.remove(removed);
                                saveDataToFile();
                                updateContentView();
                            }
                        }
                    } else if (currentView.equals("PLAYLISTS")) {
                         String item = getItem();
                         int pidx = item.lastIndexOf(" (");
                         String playlistName = pidx > 0 ? item.substring(0, pidx) : item;
                         boolean ok = showCustomConfirmDialog("Delete Playlist", "Delete playlist '" + playlistName + "'?", "This action cannot be undone.");
                         if (ok) {
                             playlists.remove(playlistName);
                             saveDataToFile();
                             updateContentView();
                         }
                     } else if (currentView.equals("PLAYLIST_DETAIL")) {
                         ObservableList<File> playlist = playlists.get(currentPlaylistName);
                         if (playlist != null && idx >= 0 && idx < playlist.size()) {
                             playlist.remove(idx);
                             saveDataToFile();
                             openPlaylist(currentPlaylistName);
                         }
                     }
                 });

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

        mainContent = new VBox(15, headerBox, contentView);
        mainContent.setStyle("-fx-background-color:#181818;");
        mainContent.setPadding(new Insets(20));
        VBox.setVgrow(contentView, Priority.ALWAYS);

        // ================= Bottom Player =================
        shuffleBtn = new Button("Shuffle");
        shuffleBtn.setMinSize(80, 40);
        Button prevBtn = new Button("Prev");
        prevBtn.setMinSize(50, 40);
        playBtn = new Button("▶");
        Button nextBtn = new Button("Next");
        nextBtn.setMinSize(50, 40);
        repeatBtn = new Button("Repeat");
        repeatBtn.setMinSize(80, 40);

        Button[] controls = new Button[]{shuffleBtn, prevBtn, playBtn, nextBtn, repeatBtn};

        for (Button b : controls) {
            if (b == playBtn) {
                b.setStyle("-fx-background-color:#1DB954; -fx-text-fill:white; -fx-font-size:14px; -fx-font-weight:bold; -fx-min-width:60px; -fx-min-height:50px; -fx-background-radius:25; -fx-cursor:hand; -fx-alignment:center;");
            } else {
                b.setStyle("-fx-background-color:#282828; -fx-text-fill:#b3b3b3; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-alignment:center; -fx-border-width:0; -fx-background-radius:8;");
            }
            applyLeftSidebarHover(b);
        }

        playBtn.setOnAction(e -> togglePlay());
        prevBtn.setOnAction(e -> playPrev());
        nextBtn.setOnAction(e -> playNext());
        shuffleBtn.setOnAction(e -> toggleShuffle());
        repeatBtn.setOnAction(e -> toggleRepeat());

        HBox controlBox = new HBox(20, shuffleBtn, prevBtn, playBtn, nextBtn, repeatBtn);
        controlBox.setAlignment(Pos.CENTER);
        controlBox.setPadding(new Insets(10, 0, 5, 0));

        currentTimeLabel.setStyle("-fx-text-fill:#b3b3b3; -fx-font-size:11px;");
        totalTimeLabel.setStyle("-fx-text-fill:#b3b3b3; -fx-font-size:11px;");

        progressSlider.setStyle(
                "-fx-background-color: transparent;" +
                        "-fx-control-inner-background: #1a1a1a;" +
                        "-fx-accent: #00ff88;" +
                        "-fx-background-radius: 10;" +
                        "-fx-padding: 5;"
        );
        HBox.setHgrow(progressSlider, Priority.ALWAYS);
        progressSlider.setOnMousePressed(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
            }
        });
        progressSlider.setOnMouseDragged(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
            }
        });

        HBox progressBox = new HBox(10, currentTimeLabel, progressSlider, totalTimeLabel);
        progressBox.setAlignment(Pos.CENTER);
        progressBox.setPadding(new Insets(0, 20, 0, 20));

        VBox bottomBar = new VBox(controlBox, progressBox);
        bottomBar.setStyle("-fx-background-color:#181818; -fx-border-color:#282828; -fx-border-width:1 0 0 0;");
        bottomBar.setPadding(new Insets(15));
        bottomBar.setAlignment(Pos.CENTER);

        // ================= Root Layout =================
        mainLayout = new BorderPane();
        mainLayout.setTop(titleBar);
        mainLayout.setLeft(leftSidebar);
        mainLayout.setCenter(mainContent);
        mainLayout.setRight(rightSidebar);
        mainLayout.setBottom(bottomBar);
        mainLayout.setStyle("-fx-background-color:#121212;");

        Scene scene = new Scene(mainLayout, 1100, 680);
        // 전역 폰트 설정: Noto Sans 우선 사용, 없으면 폴백
        try {
            InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf");
            if (fontStream != null) {
                Font.loadFont(fontStream, 12);
            }
        } catch (Exception ignored) {
        }

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

        // Set application icon for taskbar
        try {
            InputStream iconStream = getClass().getResourceAsStream("/aurora.png");
            if (iconStream != null) {
                Image icon = new Image(iconStream);
                stage.getIcons().add(icon);
            } else {
                stage.getIcons().add(createDefaultIcon());
            }
        } catch (Exception e) {
            stage.getIcons().add(createDefaultIcon());
        }

        stage.setOnCloseRequest(e -> saveDataToFile());

        stage.show();

        // Load initial view (after UI built)
        updateContentView();
        updateActionButtons();
    }

    // ================= View Management =================
    private void updateTabStyles() {
        // active 상태는 항상 초록색을 유지하도록 버튼 프로퍼티에 active 상태와 activeStyle을 저장
        final String activeStyle = "-fx-background-color:#282828; -fx-text-fill:#1DB954; -fx-font-size:14px; -fx-font-weight:bold; -fx-alignment:center; -fx-padding:10 20 10 20;";
        final String inactiveStyle = "-fx-background-color:transparent; -fx-text-fill:#b3b3b3; -fx-font-size:14px; -fx-font-weight:bold; -fx-alignment:center; -fx-padding:10 20 10 20;";

        if (currentView.equals("SONGS")) {
            tabSongs.setStyle(activeStyle);
            tabSongs.getProperties().put("active", Boolean.TRUE);
            tabSongs.getProperties().put("activeStyle", activeStyle);

            tabPlaylist.setStyle(inactiveStyle);
            tabPlaylist.getProperties().put("active", Boolean.FALSE);
            tabPlaylist.getProperties().put("activeStyle", inactiveStyle);
        } else {
            tabPlaylist.setStyle(activeStyle);
            tabPlaylist.getProperties().put("active", Boolean.TRUE);
            tabPlaylist.getProperties().put("activeStyle", activeStyle);

            tabSongs.setStyle(inactiveStyle);
            tabSongs.getProperties().put("active", Boolean.FALSE);
            tabSongs.getProperties().put("activeStyle", inactiveStyle);
        }
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

        if (currentView.equals("SONGS")) {
            // 상단의 DELETE 버튼 제거 — 삭제은 리스트 항목의 오른쪽 DELETE 버튼으로 처리
        } else if (currentView.equals("PLAYLISTS")) {
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
            playAllBtn.setOnAction(e -> {
                ObservableList<File> playlist = playlists.get(currentPlaylistName);
                if (playlist != null && !playlist.isEmpty()) {
                    currentPlaylist.setAll(playlist);
                    currentIndex = 0;
                    playedIndices.clear();
                    if (isShuffleOn) generateShuffleOrder();
                    playTrack();
                }
            });

            // Remove 버튼 제거 — 상단에서 개별 항목 삭제는 각 항목의 DELETE로 처리
            actionButtonBox.getChildren().addAll(backBtn, playAllBtn);
        }
    }

    private Button createActionButton(String text) {
        Button btn = new Button(text);
        btn.setStyle("-fx-background-color:#282828; -fx-text-fill:#1DB954; -fx-font-size:11px; -fx-font-weight:bold; -fx-padding:6 12 6 12; -fx-background-radius:15; -fx-cursor:hand;");
        applyLeftSidebarHover(btn);
        return btn;
    }

    private void updateContentView() {
        contentView.getItems().clear();

        if (currentView.equals("SONGS")) {
            for (int i = 0; i < allSongs.size(); i++) {
                String rawName = allSongs.get(i).getName();
                String displayName = stripExtension(rawName);
                contentView.getItems().add((i + 1) + ". " + displayName);
            }
            if (allSongs.isEmpty()) {
                contentView.setPlaceholder(new Label("No songs added.\nClick 'FILE' or 'URL' to get started."));
            }
        } else {
            for (String playlistName : playlists.keySet()) {
                ObservableList<File> pl = playlists.get(playlistName);
                int size = pl == null ? 0 : pl.size();
                contentView.getItems().add(playlistName + " (" + size + " songs)");
            }
            if (playlists.isEmpty()) {
                contentView.setPlaceholder(new Label("No playlists yet.\nClick '+' to start."));
            }
        }
    }

    private void handleDoubleClick() {
        int selected = contentView.getSelectionModel().getSelectedIndex();
        if (selected < 0) return;

        if (currentView.equals("SONGS")) {
            currentPlaylist.setAll(allSongs);
            currentIndex = selected;
            playedIndices.clear();
            if (isShuffleOn) generateShuffleOrder();
            playTrack();
        } else if (currentView.equals("PLAYLISTS")) {
            String item = contentView.getItems().get(selected);
            int idx = item.lastIndexOf(" (");
            String playlistName = idx > 0 ? item.substring(0, idx) : item;
            openPlaylist(playlistName);
        } else if (currentView.equals("PLAYLIST_DETAIL")) {
            if (selected >= 0) {
                ObservableList<File> playlist = playlists.get(currentPlaylistName);
                if (playlist == null || playlist.isEmpty()) return;
                currentPlaylist.setAll(playlist);
                currentIndex = selected;
                playedIndices.clear();
                if (isShuffleOn) generateShuffleOrder();
                playTrack();
            }
        }
    }

    private void handleDelete() {
        int selected = contentView.getSelectionModel().getSelectedIndex();
        if (selected < 0) return;

        if (currentView.equals("SONGS")) {
            if (selected >= 0 && selected < allSongs.size()) {
                File removed = allSongs.remove(selected);
                currentPlaylist.remove(removed);
                saveDataToFile();
                updateContentView();
            }
        } else if (currentView.equals("PLAYLISTS")) {
            String item = contentView.getItems().get(selected);
            int idx = item.lastIndexOf(" (");
            String playlistName = idx > 0 ? item.substring(0, idx) : item;

            boolean ok = showCustomConfirmDialog("Delete Playlist", "Delete playlist '" + playlistName + "'?", "This action cannot be undone.");
            if (ok) {
                playlists.remove(playlistName);
                saveDataToFile();
                updateContentView();
            }
        }
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

    // 커스텀 텍스트 입력 모달 (앱 스타일에 맞춘 간단한 구현)
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
        // 다이얼로그 버튼에도 호버 적용
        applyLeftSidebarHover(ok);
        applyLeftSidebarHover(cancel);

        ok.setOnAction(e -> {
            result[0] = input.getText();
            dialog.close();
        });
        cancel.setOnAction(e -> {
            result[0] = null;
            dialog.close();
        });

        HBox buttons = new HBox(8, ok, cancel);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(12, lbl, input, buttons);
        box.setPadding(new Insets(16));
        box.setStyle("-fx-background-color:#121212; -fx-border-color:#282828; -fx-border-width:1; -fx-background-radius:6;");

        Scene scene = new Scene(box);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();

        return Optional.ofNullable(result[0]).map(String::trim).filter(s -> !s.isEmpty());
    }

    // 커스텀 확인 모달 (예/아니오)
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
        applyLeftSidebarHover(ok);
        applyLeftSidebarHover(cancel);
        ok.setOnAction(e -> {
            confirmed[0] = true;
            dialog.close();
        });
        cancel.setOnAction(e -> {
            confirmed[0] = false;
            dialog.close();
        });

        HBox buttons = new HBox(8, cancel, ok);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(8, lblHeader, lblContent, buttons);
        box.setPadding(new Insets(14));
        box.setStyle("-fx-background-color:#121212; -fx-border-color:#282828; -fx-border-width:1; -fx-background-radius:6;");

        Scene scene = new Scene(box);
        scene.setFill(Color.TRANSPARENT);
        dialog.setScene(scene);
        dialog.showAndWait();

        return confirmed[0];
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
            String rawName = playlist.get(i).getName();
            String displayName = stripExtension(rawName);
            contentView.getItems().add((i + 1) + ". " + displayName);
        }

        if (playlist.isEmpty()) {
            contentView.setPlaceholder(new Label("Playlist is empty.\nClick 'Add Songs' to add music."));
        }

        updateActionButtons();
    }

    private void addSongsToPlaylist(String playlistName) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Music Files for Playlist");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.m4a"));
        List<File> files = fc.showOpenMultipleDialog(null);

        if (files != null && !files.isEmpty()) {
            ObservableList<File> playlist = playlists.get(playlistName);
            if (playlist == null) return;
            int added = 0;

            for (File file : files) {
                if (!allSongs.contains(file)) {
                    allSongs.add(file);
                }
                if (!playlist.contains(file)) {
                    playlist.add(file);
                    added++;
                }
            }

            saveDataToFile();
            openPlaylist(playlistName);

            if (added > 0) {
                showAlert("Success", added + " song(s) added to playlist!");
            }
        }
    }

    private void addFromUrl() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Music from URL");
        dialog.setHeaderText("Enter audio file URL:");
        dialog.setContentText("URL:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(url -> {
            if (!url.trim().isEmpty()) {
                try {
                    Media media = new Media(url);
                    MediaPlayer testPlayer = new MediaPlayer(media);
                    testPlayer.setOnReady(() -> {
                        testPlayer.dispose();
                        // Create a temporary file reference for URL
                        File urlFile = new File("URL: " + url);
                        if ("PLAYLIST_DETAIL".equals(currentView) && currentPlaylistName != null) {
                            // 플레이리스트 상세일 때는 해당 플레이리스트에만 추가
                            ObservableList<File> pl = playlists.get(currentPlaylistName);
                            if (pl != null) {
                                if (!pl.contains(urlFile)) {
                                    pl.add(urlFile);
                                    saveDataToFile();
                                    openPlaylist(currentPlaylistName);
                                    showAlert("Success", "URL added to playlist!");
                                }
                            }
                        } else {
                            // 일반 화면: allSongs에 추가
                            if (!allSongs.contains(urlFile)) {
                                allSongs.add(urlFile);
                                saveDataToFile();
                                updateContentView();
                                showAlert("Success", "URL added successfully!");
                            }
                        }
                     });
                     testPlayer.setOnError(() -> {
                         testPlayer.dispose();
                         showAlert("Error", "Invalid audio URL or format not supported.");
                     });
                 } catch (Exception e) {
                     showAlert("Error", "Failed to add URL: " + e.getMessage());
                 }
             }
         });
     }

    // ================= Player Logic =================
    private void playTrack() {
        if (currentPlaylist.isEmpty()) return;
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }

        File file = currentPlaylist.get(currentIndex);
        try {
            // URL 항목은 File.name이 "URL: <url>" 형태로 저장되어 있음
            String source = file.getName().startsWith("URL: ")
                    ? file.getName().substring(5)
                    : file.toURI().toString();

            Media media = new Media(source);
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(volumeSlider.getValue());

            String rawName = file.getName();
            String baseName = stripExtension(rawName);
            trackTitle.setText(baseName.length() > 30 ? baseName.substring(0, 27) + "..." : baseName);
            trackArtist.setText("Unknown Artist");
            playBtn.setText("■");

            playedIndices.add(currentIndex);

            media.getMetadata().addListener((javafx.collections.MapChangeListener.Change<? extends String, ? extends Object> change) -> {
                if (change.wasAdded()) {
                    if ("image".equals(change.getKey())) {
                        Image image = (Image) change.getValueAdded();
                        Platform.runLater(() -> updateAlbumArt(image));
                    }
                    if ("artist".equals(change.getKey())) {
                        Platform.runLater(() -> trackArtist.setText((String) change.getValueAdded()));
                    }
                    if ("title".equals(change.getKey())) {
                        Platform.runLater(() -> trackTitle.setText((String) change.getValueAdded()));
                    }
                }
            });

            mediaPlayer.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (!progressSlider.isValueChanging()) {
                    progressSlider.setValue(newTime.toSeconds());
                    currentTimeLabel.setText(formatTime(newTime));
                }
            });

            mediaPlayer.setOnReady(() -> {
                progressSlider.setMax(mediaPlayer.getTotalDuration().toSeconds());
                totalTimeLabel.setText(formatTime(mediaPlayer.getTotalDuration()));
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

        } catch (Exception e) {
            showAlert("Playback Error", "Could not play file: " + e.getMessage());
            e.printStackTrace();
        }
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
            if (!currentPlaylist.isEmpty()) {
                playTrack();
            } else if (!allSongs.isEmpty()) {
                currentPlaylist.setAll(allSongs);
                currentIndex = 0;
                playedIndices.clear();
                if (isShuffleOn) generateShuffleOrder();
                playTrack();
            }
            return;
        }
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            playBtn.setText("▶");
            // 재생 중 아님 -> active 해제
            playBtn.getProperties().put("active", Boolean.FALSE);
            playBtn.getProperties().put("activeStyle", "-fx-background-color:#1DB954; -fx-text-fill:white; -fx-font-size:14px; -fx-font-weight:bold; -fx-min-width:60px; -fx-min-height:50px; -fx-background-radius:25; -fx-cursor:hand; -fx-alignment:center;");
        } else {
            mediaPlayer.play();
            playBtn.setText("■");
            // 재생 중 -> active 유지
            String playActive = "-fx-background-color:#1DB954; -fx-text-fill:white; -fx-font-size:14px; -fx-font-weight:bold; -fx-min-width:60px; -fx-min-height:50px; -fx-background-radius:25; -fx-cursor:hand; -fx-alignment:center;";
            playBtn.setStyle(playActive);
            playBtn.getProperties().put("active", Boolean.TRUE);
            playBtn.getProperties().put("activeStyle", playActive);
        }
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
            if (playedIndices.size() >= currentPlaylist.size()) {
                if (repeatMode == RepeatMode.ALL) {
                    playedIndices.clear();
                    generateShuffleOrder();
                } else {
                    if (mediaPlayer != null) {
                        mediaPlayer.stop();
                        playBtn.setText("▶");
                    }
                    return;
                }
            }
            shuffleIndex = (shuffleIndex + 1) % shuffleOrder.size();
            currentIndex = shuffleOrder.get(shuffleIndex);
        } else {
            currentIndex = (currentIndex + 1) % currentPlaylist.size();
            if (currentIndex == 0 && repeatMode == RepeatMode.OFF) {
                if (mediaPlayer != null) {
                    mediaPlayer.stop();
                    playBtn.setText("▶");
                }
                return;
            }
        }
        playTrack();
    }

    private void toggleShuffle() {
        isShuffleOn = !isShuffleOn;
        if (isShuffleOn) {
            String activeStyle = "-fx-background-color:#1DB954; -fx-text-fill:white; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-alignment:center; -fx-border-width:0; -fx-background-radius:8;";
            shuffleBtn.setText("Shuffle");
            shuffleBtn.setStyle(activeStyle);
            // active 고정
            shuffleBtn.getProperties().put("active", Boolean.TRUE);
            shuffleBtn.getProperties().put("activeStyle", activeStyle);
            playedIndices.clear();
            generateShuffleOrder();
        } else {
            String normalStyle = "-fx-background-color:#282828; -fx-text-fill:#b3b3b3; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-alignment:center; -fx-border-width:0; -fx-background-radius:8;";
            shuffleBtn.setText("Shuffle");
            shuffleBtn.setStyle(normalStyle);
            shuffleBtn.getProperties().put("active", Boolean.FALSE);
            shuffleBtn.getProperties().put("activeStyle", normalStyle);
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
        Collections.shuffle(indices);

        shuffleOrder.add(currentIndex);
        shuffleOrder.addAll(indices);
        shuffleIndex = 0;
    }

    private void toggleRepeat() {
        switch (repeatMode) {
            case OFF:
                repeatMode = RepeatMode.ALL;
                String allStyle = "-fx-background-color:#1DB954; -fx-text-fill:white; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-alignment:center; -fx-border-width:0; -fx-background-radius:8;";
                repeatBtn.setText("Repeat");
                repeatBtn.setStyle(allStyle);
                repeatBtn.getProperties().put("active", Boolean.TRUE);
                repeatBtn.getProperties().put("activeStyle", allStyle);
                break;
            case ALL:
                repeatMode = RepeatMode.ONE;
                String oneStyle = "-fx-background-color:#00ff88; -fx-text-fill:black; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-alignment:center; -fx-border-width:0; -fx-background-radius:8;";
                repeatBtn.setText("Repeat 1");
                repeatBtn.setStyle(oneStyle);
                repeatBtn.getProperties().put("active", Boolean.TRUE);
                repeatBtn.getProperties().put("activeStyle", oneStyle);
                break;
            case ONE:
                repeatMode = RepeatMode.OFF;
                String offStyle = "-fx-background-color:#282828; -fx-text-fill:#b3b3b3; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-alignment:center; -fx-border-width:0; -fx-background-radius:8;";
                repeatBtn.setText("Repeat");
                repeatBtn.setStyle(offStyle);
                repeatBtn.getProperties().put("active", Boolean.FALSE);
                repeatBtn.getProperties().put("activeStyle", offStyle);
                break;
        }
    }

    private void updateControlButtonStyle(Button button) {
        if (button == shuffleBtn) {
            if (isShuffleOn) {
                button.setStyle("-fx-background-color:#1DB954; -fx-text-fill:white; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-alignment:center; -fx-border-width:0; -fx-background-radius:8;");
            } else {
                button.setStyle("-fx-background-color:#282828; -fx-text-fill:#b3b3b3; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-alignment:center; -fx-border-width:0; -fx-background-radius:8;");
            }
        } else if (button == repeatBtn) {
            switch (repeatMode) {
                case OFF:
                    button.setStyle("-fx-background-color:#282828; -fx-text-fill:#b3b3b3; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-alignment:center; -fx-border-width:0; -fx-background-radius:8;");
                    break;
                case ALL:
                    button.setStyle("-fx-background-color:#1DB954; -fx-text-fill:white; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-alignment:center; -fx-border-width:0; -fx-background-radius:8;");
                    break;
                case ONE:
                    button.setStyle("-fx-background-color:#00ff88; -fx-text-fill:black; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-alignment:center; -fx-border-width:0; -fx-background-radius:8;");
                    break;
            }
        }
    }

    // ================= Save/Load Data =================
    private static final String DATA_FILE = System.getProperty("user.home") + "/.aurora_player_data.dat";

    private void saveDataToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DATA_FILE))) {
            List<String> songPaths = new ArrayList<>();
            for (File f : allSongs) {
                if (f.getName().startsWith("URL: ")) {
                    songPaths.add(f.getName());
                } else {
                    songPaths.add(f.getAbsolutePath());
                }
            }
            oos.writeObject(songPaths);

            Map<String, List<String>> playlistData = new HashMap<>();
            for (Map.Entry<String, ObservableList<File>> entry : playlists.entrySet()) {
                List<String> paths = new ArrayList<>();
                for (File f : entry.getValue()) {
                    if (f.getName().startsWith("URL: ")) {
                        paths.add(f.getName());
                    } else {
                        paths.add(f.getAbsolutePath());
                    }
                }
                playlistData.put(entry.getKey(), paths);
            }
            oos.writeObject(playlistData);
        } catch (IOException e) {
            System.err.println("Failed to save data: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadDataFromFile() {
        File dataFile = new File(DATA_FILE);
        if (!dataFile.exists()) {
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(dataFile))) {
            List<String> songPaths = (List<String>) ois.readObject();
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

            Map<String, List<String>> playlistData = (Map<String, List<String>>) ois.readObject();
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
        } catch (Exception e) {
            System.err.println("Failed to load data: " + e.getMessage());
        }
    }

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
            applyLeftSidebarHover(ok);
            ok.setOnAction(e -> dialog.close());

            HBox btnBox = new HBox(ok);
            btnBox.setAlignment(Pos.CENTER_RIGHT);
            btnBox.setPadding(new Insets(8, 0, 0, 0));

            VBox box = new VBox(10, lbl, btnBox);
            box.setPadding(new Insets(14));
            box.setStyle("-fx-background-color:#121212; -fx-border-color:#282828; -fx-border-width:1; -fx-background-radius:6;");

            Scene scene = new Scene(box);
            dialog.setScene(scene);
            dialog.showAndWait();
        });
    }

    private String formatTime(Duration duration) {
        int totalSeconds = (int) duration.toSeconds();
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private Image createDefaultIcon() {
        int size = 64;
        WritableImage icon = new WritableImage(size, size);
        javafx.scene.image.PixelWriter writer = icon.getPixelWriter();

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

    // 파일명 또는 URL에서 확장자 제거
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

    // 공통 호버 스타일 적용 (왼쪽 사이드바 호버 기준)
    private void applyLeftSidebarHover(Button b) {
        // 마우스 진입 시 현재 스타일을 저장하고 호버 스타일 적용.
        // 마우스 나감 시, 버튼이 active이면 activeStyle을 유지하고 아니면 저장해둔 원래 스타일로 복원.
        b.setOnMouseEntered(e -> {
            // 현재 스타일을 저장 (추후 복원용)
            b.getProperties().put("hoverBaseStyle", b.getStyle() == null ? "" : b.getStyle());
            String base = b.getStyle() == null ? "" : b.getStyle();
            String hover = base + " -fx-background-color:#282828; -fx-text-fill:#1DB954;";
            b.setStyle(hover);
        });
        b.setOnMouseExited(e -> {
            Object active = b.getProperties().get("active");
            if (Boolean.TRUE.equals(active)) {
                Object activeStyle = b.getProperties().get("activeStyle");
                if (activeStyle instanceof String) {
                    b.setStyle((String) activeStyle);
                    return;
                }
            }
            Object base = b.getProperties().get("hoverBaseStyle");
            if (base instanceof String) {
                b.setStyle((String) base);
            }
        });
    }
}