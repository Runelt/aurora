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
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.effect.DropShadow;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Modality;
import javafx.util.Duration;

import java.io.*;
import java.nio.file.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;

public class aurora extends Application {

    // ── 상태 ──────────────────────────────────────────────────────────────
    private enum View { SONGS, PLAYLISTS, PLAYLIST_DETAIL }
    private enum RepeatMode { OFF, ALL, ONE }

    private final ObservableList<File> allSongs        = FXCollections.observableArrayList();
    private final ObservableList<File> currentPlaylist = FXCollections.observableArrayList();
    private final Map<String, ObservableList<File>> playlists = new LinkedHashMap<>();
    private final Map<String, String> urlMappings      = new HashMap<>();

    private View   currentView         = View.SONGS;
    private String currentPlaylistName = null;
    private int    currentIndex        = 0;

    private MediaPlayer mediaPlayer;
    private boolean     isShuffleOn    = false;
    private RepeatMode  repeatMode     = RepeatMode.OFF;
    private final List<Integer> shuffleOrder  = new ArrayList<>();
    private int shuffleIndex = 0;
    private final Set<Integer>    playedIndices = new HashSet<>();
    private final AtomicBoolean   isUpdatingProgress = new AtomicBoolean(false);

    private File dataFolder;
    private static final String DATA_FILE = "aurora_player_data.dat";

    // ── 색상 팔레트 (미니멀 화이트) ─────────────────────────────────────
    private static final String C_BG          = "#FFFFFF";
    private static final String C_SIDEBAR     = "#F7F7F8";
    private static final String C_DIVIDER     = "#E8E8EA";
    private static final String C_SURFACE     = "#FAFAFA";
    private static final String C_TEXT_PRI    = "#111111";
    private static final String C_TEXT_SEC    = "#888888";
    private static final String C_ACCENT      = "#111111";
    private static final String C_ACCENT_HOV  = "#333333";
    private static final String C_ITEM_HOV    = "#F0F0F2";
    private static final String C_ITEM_SEL    = "#EBEBED";
    private static final String C_DANGER      = "#E53935";
    private static final String C_PLAY_BG     = "#111111";
    private static final String C_PLAY_ICON   = "#FFFFFF";

    // ── UI 컴포넌트 ───────────────────────────────────────────────────────
    private final Label    trackTitle      = new Label("Not Playing");
    private final Label    trackArtist     = new Label("—");
    private final ImageView albumArt       = new ImageView();
    private final Slider   progressSlider  = new Slider();
    private final Slider   volumeSlider    = new Slider(0, 1, 0.5);
    private final Label    currentTimeLabel= new Label("0:00");
    private final Label    totalTimeLabel  = new Label("0:00");
    private final ListView<String> contentView = new ListView<>();

    private Button playBtn;
    private Button shuffleBtn;
    private Button repeatBtn;
    private Button tabSongs;
    private Button tabPlaylists;
    private HBox   actionButtonBox;
    private StackPane albumPane;
    private VBox   mainContent;

    // 사이드바 하단 플레이리스트 상태
    private final Label playlistStatusLabel = new Label("No Playlist");
    private Stage       mainStage;

    // ══════════════════════════════════════════════════════════════════════
    public static void main(String[] args) { launch(args); }

    @Override
    public void start(Stage stage) {
        initializeDataFolder();
        loadDataFromFile();
        mainStage = stage;
        initializeUI(stage);
        updateContentView();
        updateActionButtons();
    }

    // ── 데이터 폴더 ──────────────────────────────────────────────────────
    private void initializeDataFolder() {
        try {
            URI uri = aurora.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            File codeLoc = new File(uri);
            File baseDir = codeLoc.isDirectory() ? codeLoc : codeLoc.getParentFile();
            if (baseDir == null) baseDir = new File(System.getProperty("user.dir"));
            dataFolder = new File(baseDir, "Data");
        } catch (Exception e) {
            dataFolder = new File(System.getProperty("user.dir"), "Data");
        }
        if (!dataFolder.exists()) dataFolder.mkdirs();
    }

    // ── UI 초기화 ──────────────────────────────────────────────────────
    private void initializeUI(Stage stage) {
        BorderPane root = new BorderPane();
        root.setStyle("-fx-background-color:" + C_BG + ";");

        root.setLeft(buildSidebar(stage));
        root.setCenter(buildCenter());
        root.setBottom(buildPlayerBar());

        Scene scene = new Scene(root, 1080, 660);
        loadCustomFont();
        scene.getRoot().setStyle("-fx-font-family:'Noto Sans','Segoe UI',Arial; -fx-font-smoothing-type:gray;");
        System.setProperty("prism.lcdtext", "false");
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.SPACE) { togglePlay(); e.consume(); } });

        stage.setScene(scene);
        stage.setTitle("AURORA");
        setApplicationIcon(stage);
        stage.setOnCloseRequest(e -> saveDataToFile());
        stage.show();
    }

    // ── 사이드바 ──────────────────────────────────────────────────────────
    private VBox buildSidebar(Stage stage) {
        tabSongs     = navTab("◈SONG",     () -> { currentView = View.SONGS;     updateContentView(); updateActionButtons(); updateTabStyles(); });
        tabPlaylists = navTab("◈PLAYLIST", () -> { currentView = View.PLAYLISTS; updateContentView(); updateActionButtons(); updateTabStyles(); });
        updateTabStyles();

        VBox nav = new VBox(2, tabSongs, tabPlaylists);
        nav.setPadding(new Insets(16, 8, 8, 8));

        // 하단: 플레이리스트 상태 표시
        Label statusHeader = new Label("PLAYLIST STATUS");
        statusHeader.setStyle("-fx-text-fill:" + C_TEXT_SEC + "; -fx-font-size:10px; -fx-font-weight:bold;");

        playlistStatusLabel.setStyle("-fx-text-fill:" + C_TEXT_PRI + "; -fx-font-size:12px; -fx-font-weight:bold;");
        playlistStatusLabel.setWrapText(true);
        playlistStatusLabel.setMaxWidth(160);

        VBox statusBox = new VBox(4, statusHeader, playlistStatusLabel);
        statusBox.setPadding(new Insets(12, 12, 20, 14));
        statusBox.setStyle("-fx-border-color:" + C_DIVIDER + "; -fx-border-width:1 0 0 0;");

        Region sp = new Region();
        VBox.setVgrow(sp, Priority.ALWAYS);

        VBox sidebar = new VBox(nav, sp, statusBox);
        sidebar.setPrefWidth(200);
        sidebar.setStyle("-fx-background-color:" + C_SIDEBAR + "; -fx-border-color:" + C_DIVIDER + "; -fx-border-width:0 1 0 0;");
        return sidebar;
    }

    private Button navTab(String text, Runnable action) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setPadding(new Insets(9, 14, 9, 14));
        b.setStyle(navTabStyle(false));
        b.setOnAction(e -> action.run());
        b.setOnMouseEntered(e -> { if (!isTabActive(b)) b.setStyle(navTabStyle(false) + "-fx-background-color:" + C_ITEM_HOV + ";"); });
        b.setOnMouseExited (e -> { if (!isTabActive(b)) b.setStyle(navTabStyle(false)); });
        return b;
    }

    private boolean isTabActive(Button b) {
        return Boolean.TRUE.equals(b.getProperties().get("active"));
    }

    private String navTabStyle(boolean active) {
        return "-fx-background-color:" + (active ? C_ITEM_SEL : "transparent") + "; "
             + "-fx-text-fill:" + (active ? C_TEXT_PRI : C_TEXT_SEC) + "; "
             + "-fx-font-size:13px; -fx-font-weight:" + (active ? "bold" : "normal") + "; "
             + "-fx-background-radius:8; -fx-cursor:hand; -fx-border-width:0;";
    }

    private void updateTabStyles() {
        boolean songsActive = (currentView == View.SONGS);
        boolean plActive    = (currentView == View.PLAYLISTS || currentView == View.PLAYLIST_DETAIL);

        tabSongs.setStyle(navTabStyle(songsActive));
        tabSongs.getProperties().put("active", songsActive);
        tabPlaylists.setStyle(navTabStyle(plActive));
        tabPlaylists.getProperties().put("active", plActive);
    }

    private Button sidebarActionBtn(String text, javafx.event.EventHandler<javafx.event.ActionEvent> h) {
        Button b = new Button(text);
        b.setMaxWidth(Double.MAX_VALUE);
        String base = "-fx-background-color:transparent; -fx-text-fill:" + C_TEXT_SEC
                    + "; -fx-font-size:12px; -fx-cursor:hand; -fx-padding:7 10 7 10;"
                    + "-fx-background-radius:8; -fx-border-color:" + C_DIVIDER + "; -fx-border-width:1; -fx-border-radius:8;";
        b.setStyle(base);
        b.setOnAction(h);
        b.setOnMouseEntered(e -> b.setStyle(base + "-fx-background-color:" + C_ITEM_HOV + ";"));
        b.setOnMouseExited (e -> b.setStyle(base));
        return b;
    }

    // ── 중앙 콘텐츠 ──────────────────────────────────────────────────────
    private BorderPane buildCenter() {
        // 헤더
        actionButtonBox = new HBox(8);
        actionButtonBox.setAlignment(Pos.CENTER_LEFT);

        HBox header = new HBox(actionButtonBox);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(16, 20, 12, 20));
        header.setStyle("-fx-border-color:" + C_DIVIDER + "; -fx-border-width:0 0 1 0;");
        HBox.setHgrow(actionButtonBox, Priority.ALWAYS);

        // 리스트
        setupContentView();

        BorderPane center = new BorderPane();
        center.setTop(header);
        center.setCenter(contentView);
        center.setStyle("-fx-background-color:" + C_BG + ";");
        return center;
    }

    private void setupContentView() {
        contentView.setStyle(
            "-fx-background-color:" + C_BG + "; -fx-control-inner-background:" + C_BG + "; "
          + "-fx-border-color:transparent; -fx-focus-color:transparent; -fx-faint-focus-color:transparent;");

        Label empty = new Label("No items here yet.");
        empty.setStyle("-fx-text-fill:" + C_TEXT_SEC + "; -fx-font-size:13px;");
        contentView.setPlaceholder(empty);

        contentView.setCellFactory(lv -> new ListCell<>() {
            private final HBox  row     = new HBox(10);
            private final Label idx     = new Label();
            private final Label name    = new Label();
            private final Button editBtn= smallBtn("Edit",   "#555555");
            private final Button delBtn = smallBtn("Delete", C_DANGER);

            {
                idx.setMinWidth(28);
                idx.setStyle("-fx-text-fill:" + C_TEXT_SEC + "; -fx-font-size:12px;");
                name.setStyle("-fx-text-fill:" + C_TEXT_PRI + "; -fx-font-size:13px;");
                name.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(name, Priority.ALWAYS);

                editBtn.setOnAction(e -> handleEditButtonClick(getIndex(), getItem()));
                delBtn .setOnAction(e -> handleDeleteButtonClick(getIndex(), getItem()));
                editBtn.setVisible(false);
                delBtn .setVisible(false);

                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(6, 12, 6, 12));
                row.getChildren().addAll(idx, name, editBtn, delBtn);
                row.setStyle("-fx-background-color:transparent; -fx-background-radius:8;");

                setOnMouseEntered(e -> {
                    if (!isEmpty()) {
                        row.setStyle("-fx-background-color:" + C_ITEM_HOV + "; -fx-background-radius:8;");
                        editBtn.setVisible(true);
                        delBtn .setVisible(true);
                    }
                });
                setOnMouseExited(e -> {
                    row.setStyle("-fx-background-color:transparent; -fx-background-radius:8;");
                    editBtn.setVisible(false);
                    delBtn .setVisible(false);
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setStyle("-fx-background-color:transparent; -fx-padding:0 4 0 4;");
                if (empty || item == null) { setText(null); setGraphic(null); return; }
                idx.setText(String.valueOf(getIndex() + 1));
                name.setText(item);
                setGraphic(row);
                setText(null);
            }
        });

        contentView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && e.getButton() == MouseButton.PRIMARY) handleDoubleClick();
        });
    }

    private Button smallBtn(String text, String color) {
        Button b = new Button(text);
        String base = "-fx-background-color:transparent; -fx-text-fill:" + color
                    + "; -fx-font-size:11px; -fx-cursor:hand; -fx-padding:3 8 3 8;"
                    + "-fx-background-radius:5; -fx-border-color:" + color + "; -fx-border-width:1; -fx-border-radius:5;";
        b.setStyle(base);
        b.setOnMouseEntered(e -> b.setStyle(base + "-fx-background-color:" + color + "22;"));
        b.setOnMouseExited (e -> b.setStyle(base));
        return b;
    }

    // ── 플레이어바 ───────────────────────────────────────────────
    private VBox buildPlayerBar() {
        // 앨범 아트
        albumPane = new StackPane();
        albumPane.setPrefSize(44, 44);
        updateAlbumArt(null);

        // 트랙 정보
        trackTitle.setStyle("-fx-text-fill:" + C_TEXT_PRI + "; -fx-font-size:13px; -fx-font-weight:bold;");
        trackArtist.setStyle("-fx-text-fill:" + C_TEXT_SEC + "; -fx-font-size:11px;");
        VBox trackInfo = new VBox(2, trackTitle, trackArtist);
        trackInfo.setAlignment(Pos.CENTER_LEFT);
        trackInfo.setPrefWidth(180);

        HBox leftZone = new HBox(12, albumPane, trackInfo);
        leftZone.setAlignment(Pos.CENTER_LEFT);
        leftZone.setPrefWidth(230);

        // 컨트롤 버튼
        shuffleBtn = controlBtn("⇄");
        Button prevBtn = controlBtn("◁");
        playBtn = playButton();
        Button nextBtn = controlBtn("▷");
        repeatBtn = controlBtn("↻");

        shuffleBtn.setOnAction(e -> toggleShuffle());
        prevBtn   .setOnAction(e -> playPrev());
        playBtn   .setOnAction(e -> togglePlay());
        nextBtn   .setOnAction(e -> playNext());
        repeatBtn .setOnAction(e -> toggleRepeat());

        HBox controls = new HBox(8, shuffleBtn, prevBtn, playBtn, nextBtn, repeatBtn);
        controls.setAlignment(Pos.CENTER);

        // 프로그레스
        currentTimeLabel.setStyle("-fx-text-fill:" + C_TEXT_SEC + "; -fx-font-size:11px;");
        totalTimeLabel  .setStyle("-fx-text-fill:" + C_TEXT_SEC + "; -fx-font-size:11px;");

        progressSlider.setStyle(
            "-fx-background-color:transparent; -fx-control-inner-background:" + C_DIVIDER + "; "
          + "-fx-accent:" + C_ACCENT + "; -fx-padding:2;");
        progressSlider.setOnMousePressed(e -> seekToPosition());
        progressSlider.setOnMouseDragged(e -> seekToPosition());
        HBox.setHgrow(progressSlider, Priority.ALWAYS);

        HBox progressRow = new HBox(8, currentTimeLabel, progressSlider, totalTimeLabel);
        progressRow.setAlignment(Pos.CENTER);

        VBox centerZone = new VBox(6, controls, progressRow);
        centerZone.setAlignment(Pos.CENTER);
        HBox.setHgrow(centerZone, Priority.ALWAYS);

        // 볼륨
        Label volIcon = new Label("🔊");
        volIcon.setStyle("-fx-font-size:13px;");
        volumeSlider.setMaxWidth(100);
        volumeSlider.setStyle(
            "-fx-background-color:transparent; -fx-control-inner-background:" + C_DIVIDER + "; "
          + "-fx-accent:" + C_ACCENT + "; -fx-padding:2;");
        volumeSlider.valueProperty().addListener((obs, o, n) -> { if (mediaPlayer != null) mediaPlayer.setVolume(n.doubleValue()); });

        HBox rightZone = new HBox(8, volIcon, volumeSlider);
        rightZone.setAlignment(Pos.CENTER_RIGHT);
        rightZone.setPrefWidth(160);

        HBox playerRow = new HBox(leftZone, centerZone, rightZone);
        playerRow.setAlignment(Pos.CENTER);
        playerRow.setPadding(new Insets(12, 20, 14, 20));

        VBox bar = new VBox(playerRow);
        bar.setStyle("-fx-background-color:" + C_BG + "; -fx-border-color:" + C_DIVIDER + "; -fx-border-width:1 0 0 0;");
        return bar;
    }

    private Button controlBtn(String icon) {
        Button b = new Button(icon);
        String base = "-fx-background-color:transparent; -fx-text-fill:" + C_TEXT_SEC
                    + "; -fx-font-size:16px; -fx-cursor:hand; -fx-padding:6 10; -fx-background-radius:6; -fx-border-width:0;";
        b.setStyle(base);
        b.setOnMouseEntered(e -> b.setStyle(base + "-fx-text-fill:" + C_TEXT_PRI + "; -fx-background-color:" + C_ITEM_HOV + ";"));
        b.setOnMouseExited (e -> {
            if (!Boolean.TRUE.equals(b.getProperties().get("active"))) b.setStyle(base);
            else b.setStyle(base + "-fx-text-fill:" + C_TEXT_PRI + ";");
        });
        return b;
    }

    private Button playButton() {
        Button b = new Button("▶");
        String style = "-fx-background-color:" + C_PLAY_BG + "; -fx-text-fill:" + C_PLAY_ICON
                     + "; -fx-font-size:14px; -fx-background-radius:50%; -fx-cursor:hand; "
                     + "-fx-min-width:40px; -fx-min-height:40px; -fx-max-width:40px; -fx-max-height:40px; -fx-border-width:0;";
        b.setStyle(style);
        b.setOnMouseEntered(e -> b.setStyle(style + "-fx-background-color:" + C_ACCENT_HOV + ";"));
        b.setOnMouseExited (e -> b.setStyle(style));
        return b;
    }

    private void setControlActive(Button b, boolean active) {
        b.getProperties().put("active", active);
        String base = "-fx-background-color:transparent; -fx-font-size:16px; -fx-cursor:hand; -fx-padding:6 10; -fx-background-radius:6; -fx-border-width:0;";
        b.setStyle(base + (active ? "-fx-text-fill:" + C_TEXT_PRI + ";" : "-fx-text-fill:" + C_TEXT_SEC + ";"));
    }

    // ── 액션 버튼 ─────────────────────────────────────────────────
    private void updateActionButtons() {
        actionButtonBox.getChildren().clear();

        if (currentView == View.SONGS) {
            Button addFile = headerBtn("＋ from File");
            Button addUrl  = headerBtn("＋ from URL");
            addFile.setOnAction(e -> handleAddFile(mainStage));
            addUrl .setOnAction(e -> addFromUrl());
            actionButtonBox.getChildren().addAll(addFile, addUrl);

        } else if (currentView == View.PLAYLISTS) {
            Button create = headerBtn("＋ New Playlist");
            create.setOnAction(e -> createNewPlaylist());
            actionButtonBox.getChildren().add(create);

        } else if (currentView == View.PLAYLIST_DETAIL) {
            Button back    = headerBtn("← Back");
            Button playAll = headerBtn("▶ Play All");
            Button addFile = headerBtn("＋ from File");
            Button addUrl  = headerBtn("＋ from URL");
            back.setOnAction(e -> { currentView = View.PLAYLISTS; updateContentView(); updateActionButtons(); updateTabStyles(); });
            playAll.setOnAction(e -> playPlaylist());
            addFile.setOnAction(e -> handleAddFile(mainStage));
            addUrl .setOnAction(e -> addFromUrl());
            actionButtonBox.getChildren().addAll(back, playAll, addFile, addUrl);
        }
    }

    private Button headerBtn(String text) {
        Button b = new Button(text);
        String base = "-fx-background-color:transparent; -fx-text-fill:" + C_TEXT_PRI
                    + "; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand; -fx-padding:5 12;"
                    + "-fx-background-radius:8; -fx-border-color:" + C_DIVIDER + "; -fx-border-width:1; -fx-border-radius:8;";
        b.setStyle(base);
        b.setOnMouseEntered(e -> b.setStyle(base + "-fx-background-color:" + C_ITEM_HOV + ";"));
        b.setOnMouseExited (e -> b.setStyle(base));
        return b;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  콘텐츠 뷰 업데이트
    // ══════════════════════════════════════════════════════════════════════
    private void updateContentView() {
        contentView.getItems().clear();
        switch (currentView) {
            case SONGS:
                allSongs.forEach(f -> contentView.getItems().add(stripExtension(f.getName())));
                break;
            case PLAYLISTS:
                playlists.forEach((name, pl) -> contentView.getItems().add(name + "  ·  " + pl.size() + " songs"));
                break;
            case PLAYLIST_DETAIL:
                ObservableList<File> pl = playlists.get(currentPlaylistName);
                if (pl != null) pl.forEach(f -> contentView.getItems().add(stripExtension(f.getName())));
                break;
        }
    }

    // ── 더블클릭 처리 ────────────────────────────────────────────────────
    private void handleDoubleClick() {
        int sel = contentView.getSelectionModel().getSelectedIndex();
        if (sel < 0) return;
        switch (currentView) {
            case SONGS:           playSongAtIndex(sel);        break;
            case PLAYLISTS:       openPlaylistAtIndex(sel);    break;
            case PLAYLIST_DETAIL: playPlaylistSongAtIndex(sel); break;
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
        int dot = item.indexOf("  ·  ");
        String name = dot > 0 ? item.substring(0, dot) : item;
        openPlaylist(name);
    }

    private void playPlaylistSongAtIndex(int index) {
        ObservableList<File> pl = playlists.get(currentPlaylistName);
        if (pl == null || pl.isEmpty()) return;
        currentPlaylist.setAll(pl);
        currentIndex = index;
        playedIndices.clear();
        if (isShuffleOn) generateShuffleOrder();
        playTrack();
    }

    private void openPlaylist(String name) {
        currentView = View.PLAYLIST_DETAIL;
        currentPlaylistName = name;
        updateContentView();
        updateActionButtons();
        updateTabStyles();
        updatePlaylistStatus();
    }

    // ── 파일 추가 ─────────────────────────────────────────────────────────
    private void handleAddFile(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Music Files");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Audio Files", "*.mp3","*.wav","*.m4a"));
        List<File> files = fc.showOpenMultipleDialog(stage);
        if (files == null || files.isEmpty()) return;

        if (currentView == View.PLAYLIST_DETAIL && currentPlaylistName != null) {
            addFilesToPlaylist(files, currentPlaylistName);
        } else {
            addFilesToLibrary(files);
        }
    }

    private void addFilesToLibrary(List<File> files) {
        int added = 0;
        for (File f : files) if (!allSongs.contains(f)) { allSongs.add(f); added++; }
        if (added > 0) {
            saveDataToFile();
            updateContentView();
            if (allSongs.size() == files.size() && currentPlaylist.isEmpty()) {
                currentPlaylist.setAll(allSongs); currentIndex = 0; playTrack();
            }
            showAlert("Added", added + " file(s) added to library.");
        }
    }

    private void addFilesToPlaylist(List<File> files, String playlistName) {
        ObservableList<File> pl = playlists.get(playlistName);
        if (pl == null) return;
        int added = 0;
        for (File f : files) if (!pl.contains(f)) { pl.add(f); added++; }
        if (added > 0) {
            saveDataToFile();
            updateContentView();
            showAlert("Added", added + " song(s) added to playlist.");
        }
    }

    private void addFromUrl() {
        showCustomTextInputDialog("Add from URL", "Paste YouTube URL:").ifPresent(url -> {
            if (!url.isBlank()) downloadAndPlayYoutubeAudio(url.trim());
        });
    }

    // ── 플레이리스트 관리 ─────────────────────────────────────────────────
    private void createNewPlaylist() {
        showCustomTextInputDialog("New Playlist", "Playlist name:").ifPresent(name -> {
            if (!name.isBlank() && !playlists.containsKey(name)) {
                playlists.put(name, FXCollections.observableArrayList());
                saveDataToFile();
                updateContentView();
            } else {
                showAlert("Error", "Invalid or duplicate name.");
            }
        });
    }

    private void playPlaylist() {
        ObservableList<File> pl = playlists.get(currentPlaylistName);
        if (pl != null && !pl.isEmpty()) {
            currentPlaylist.setAll(pl);
            currentIndex = 0;
            playedIndices.clear();
            if (isShuffleOn) generateShuffleOrder();
            playTrack();
            updatePlaylistStatus();
        }
    }

    // ── 편집 / 삭제 ──────────────────────────────────────────────────────
    private void handleEditButtonClick(int idx, String item) {
        if (idx < 0 || item == null) return;
        switch (currentView) {
            case SONGS:           handleSongEdit(idx);                                  break;
            case PLAYLIST_DETAIL: handlePlaylistItemEdit(idx);                          break;
            default:              showAlert("Not Editable", "Only songs can be edited."); break;
        }
    }

    private void handleSongEdit(int idx) {
        if (idx < 0 || idx >= allSongs.size()) return;
        File f = allSongs.get(idx);
        if (!f.getName().startsWith("URL: ")) { showAlert("Edit", "Local file names cannot be changed here."); return; }
        showCustomTextInputDialog("Rename Song", "New name:").ifPresent(newName -> {
            if (!newName.isBlank() && renameUrlSong(f, newName.trim(), allSongs, idx)) {
                saveDataToFile(); updateContentView();
            }
        });
    }

    private void handlePlaylistItemEdit(int idx) {
        ObservableList<File> pl = playlists.get(currentPlaylistName);
        if (pl == null || idx < 0 || idx >= pl.size()) return;
        File f = pl.get(idx);
        if (!f.getName().startsWith("URL: ")) { showAlert("Edit", "Local file names cannot be changed here."); return; }
        showCustomTextInputDialog("Rename Song", "New name:").ifPresent(newName -> {
            if (!newName.isBlank() && renameUrlSong(f, newName.trim(), pl, idx)) {
                saveDataToFile(); updateContentView();
            }
        });
    }

    private void handleDeleteButtonClick(int idx, String item) {
        if (idx < 0 || item == null) return;
        switch (currentView) {
            case SONGS:           handleSongDelete(idx, item); break;
            case PLAYLISTS:       handlePlaylistDelete(item);  break;
            case PLAYLIST_DETAIL: handlePlaylistItemDelete(idx); break;
        }
    }

    private void handleSongDelete(int idx, String item) {
        if (!showConfirmDialog("Delete Song", "Delete \"" + item + "\"?")) return;
        if (idx < 0 || idx >= allSongs.size()) return;
        File removed = allSongs.remove(idx);
        currentPlaylist.remove(removed);
        saveDataToFile();
        updateContentView();
    }

    private void handlePlaylistDelete(String item) {
        int dot = item.indexOf("  ·  ");
        String name = dot > 0 ? item.substring(0, dot) : item;
        if (!showConfirmDialog("Delete Playlist", "Delete \"" + name + "\" and all its songs?")) return;
        playlists.remove(name);
        saveDataToFile();
        updateContentView();
    }

    private void handlePlaylistItemDelete(int idx) {
        ObservableList<File> pl = playlists.get(currentPlaylistName);
        if (pl == null || idx < 0 || idx >= pl.size()) return;
        String name = stripExtension(pl.get(idx).getName());
        if (!showConfirmDialog("Remove Song", "Remove \"" + name + "\" from playlist?")) return;
        pl.remove(idx);
        saveDataToFile();
        updateContentView();
    }

    // ── URL 노래 이름 변경 ────────────────────────────────────────────────
    private boolean renameUrlSong(File urlEntry, String newBaseName, ObservableList<File> list, int indexInList) {
        try {
            String oldKey  = urlEntry.getName();
            String mapped  = urlMappings.get(oldKey);
            if (mapped == null) { showAlert("Error", "No mapping found for this song."); return false; }

            File srcFile;
            try { srcFile = mapped.startsWith("file:") ? new File(new URI(mapped)) : new File(mapped); }
            catch (Exception ex) { srcFile = new File(mapped); }
            if (!srcFile.exists()) { showAlert("Error", "Source file not found:\n" + srcFile.getAbsolutePath()); return false; }

            // 현재 재생 중이면 정지
            boolean wasPlayingThis = false;
            for (int i = 0; i < currentPlaylist.size(); i++) {
                if (currentPlaylist.get(i).getName().equals(oldKey) && i == currentIndex) {
                    wasPlayingThis = true; break;
                }
            }
            if (wasPlayingThis && mediaPlayer != null) {
                disposeMediaPlayer();
                try { Thread.sleep(200); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
            }

            String sanitized  = newBaseName.replaceAll("[/\\\\:*?\"<>|]", "_").trim();
            if (sanitized.isEmpty()) { showAlert("Error", "Invalid file name."); return false; }

            String newFilename = sanitized.toLowerCase().endsWith(".mp3") || sanitized.toLowerCase().endsWith(".wav")
                               ? sanitized : sanitized + ".mp3";
            File targetFile = new File(dataFolder, newFilename);
            if (targetFile.exists()) { showAlert("Error", "A file with this name already exists."); return false; }

            Files.move(srcFile.toPath(), targetFile.toPath(), StandardCopyOption.ATOMIC_MOVE);

            File   newUrlFile = new File("URL: " + targetFile.getName());
            String newKey     = newUrlFile.getName();
            urlMappings.remove(oldKey);
            urlMappings.put(newKey, targetFile.toURI().toString());

            // 모든 리스트에서 교체
            replaceInList(list, indexInList, urlEntry, newUrlFile);
            replaceInList(allSongs, -1, urlEntry, newUrlFile);
            playlists.values().forEach(pl -> replaceInList(pl, -1, urlEntry, newUrlFile));
            replaceInList(currentPlaylist, -1, urlEntry, newUrlFile);

            if (wasPlayingThis) playTrack();
            else updateTrackInfo(stripExtension(newUrlFile.getName()));

            showAlert("Renamed", "Song renamed successfully.");
            return true;
        } catch (Exception ex) {
            ex.printStackTrace();
            showAlert("Error", "Failed to rename song:\n" + ex.getMessage());
            return false;
        }
    }

    private void replaceInList(ObservableList<File> list, int knownIdx, File oldFile, File newFile) {
        if (list == null) return;
        if (knownIdx >= 0 && knownIdx < list.size() && list.get(knownIdx).getName().equals(oldFile.getName())) {
            list.set(knownIdx, newFile);
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getName().equals(oldFile.getName())) { list.set(i, newFile); break; }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  재생 / 컨트롤
    // ══════════════════════════════════════════════════════════════════════
    private void playTrack() {
        if (currentPlaylist.isEmpty()) return;
        disposeMediaPlayer();
        File file = currentPlaylist.get(currentIndex);

        try {
            String source;
            if (file.getName().startsWith("URL: ")) {
                String mapped = urlMappings.get(file.getName());
                if (mapped == null) { showAlert("Error", "Cannot find stream URL for this song."); return; }
                source = (mapped.startsWith("file:") || mapped.startsWith("http")) ? mapped : new File(mapped).toURI().toString();
            } else {
                source = file.toURI().toString();
            }

            Media media = new Media(source);
            mediaPlayer = new MediaPlayer(media);
            mediaPlayer.setVolume(volumeSlider.getValue());
            updateTrackInfo(stripExtension(file.getName()));
            playBtn.setText("⏸");
            playedIndices.add(currentIndex);

            setupMetadataListener(media);
            setupTimeListener();
            setupMediaPlayerCallbacks();

        } catch (Exception e) {
            showAlert("Playback Error", "Could not play file:\n" + e.getMessage());
        }
    }

    private void togglePlay() {
        if (mediaPlayer == null) {
            if (!currentPlaylist.isEmpty()) playTrack();
            else if (!allSongs.isEmpty()) { currentPlaylist.setAll(allSongs); currentIndex = 0; playedIndices.clear(); if (isShuffleOn) generateShuffleOrder(); playTrack(); }
            return;
        }
        if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause(); playBtn.setText("▶");
        } else {
            mediaPlayer.play();  playBtn.setText("⏸");
        }
        updatePlaylistStatus();
    }

    private void playPrev() {
        if (currentPlaylist.isEmpty()) return;
        if (isShuffleOn && !shuffleOrder.isEmpty())
            shuffleIndex = (shuffleIndex - 1 + shuffleOrder.size()) % shuffleOrder.size();
        else
            currentIndex = (currentIndex - 1 + currentPlaylist.size()) % currentPlaylist.size();
        if (isShuffleOn) currentIndex = shuffleOrder.get(shuffleIndex);
        playTrack();
    }

    private void playNext() {
        if (currentPlaylist.isEmpty()) return;
        if (repeatMode == RepeatMode.ONE) { playTrack(); return; }
        if (isShuffleOn && !shuffleOrder.isEmpty()) handleShuffleNext();
        else handleNormalNext();
    }

    private void handleShuffleNext() {
        if (playedIndices.size() >= currentPlaylist.size()) {
            if (repeatMode == RepeatMode.ALL) { playedIndices.clear(); generateShuffleOrder(); }
            else { stopPlayback(); return; }
        }
        shuffleIndex = (shuffleIndex + 1) % shuffleOrder.size();
        currentIndex = shuffleOrder.get(shuffleIndex);
        playTrack();
    }

    private void handleNormalNext() {
        currentIndex = (currentIndex + 1) % currentPlaylist.size();
        if (currentIndex == 0 && repeatMode == RepeatMode.OFF) stopPlayback();
        else playTrack();
    }

    private void stopPlayback() {
        if (mediaPlayer != null) { mediaPlayer.stop(); playBtn.setText("▶"); }
        updatePlaylistStatus();
    }

    private void toggleShuffle() {
        isShuffleOn = !isShuffleOn;
        setControlActive(shuffleBtn, isShuffleOn);
        if (isShuffleOn) { playedIndices.clear(); generateShuffleOrder(); }
        else             { shuffleOrder.clear(); playedIndices.clear(); }
    }

    private void toggleRepeat() {
        switch (repeatMode) {
            case OFF: repeatMode = RepeatMode.ALL; break;
            case ALL: repeatMode = RepeatMode.ONE; break;
            case ONE: repeatMode = RepeatMode.OFF; break;
        }
    }

    private void setupMetadataListener(Media media) {
        media.getMetadata().addListener((javafx.collections.MapChangeListener.Change<? extends String, ? extends Object> c) -> {
            if (!c.wasAdded()) return;
            Platform.runLater(() -> {
                String key = c.getKey();
                if ("image".equals(key)) {
                    updateAlbumArt((Image) c.getValueAdded());
                } else if ("artist".equals(key)) {
                    trackArtist.setText((String) c.getValueAdded());
                } else if ("title".equals(key)) {
                    trackTitle.setText((String) c.getValueAdded());
                }
            });
        });
    }

    private void generateShuffleOrder() {
        shuffleOrder.clear();
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < currentPlaylist.size(); i++) if (i != currentIndex) indices.add(i);
        Collections.shuffle(indices);
        shuffleOrder.add(currentIndex);
        shuffleOrder.addAll(indices);
        shuffleIndex = 0;
    }

    private void seekToPosition() {
        if (mediaPlayer != null && !isUpdatingProgress.get())
            mediaPlayer.seek(Duration.seconds(progressSlider.getValue()));
    }

    private void disposeMediaPlayer() {
        if (mediaPlayer != null) { mediaPlayer.stop(); mediaPlayer.dispose(); mediaPlayer = null; }
    }

    // ── 미디어 플레이어 콜백 ──────────────────────────────────────────────
    private void setupTimeListener() {
        mediaPlayer.currentTimeProperty().addListener((obs, o, n) -> {
            if (!progressSlider.isValueChanging() && !isUpdatingProgress.get()) {
                isUpdatingProgress.set(true);
                Platform.runLater(() -> {
                    progressSlider.setValue(n.toSeconds());
                    currentTimeLabel.setText(formatTime(n));
                    isUpdatingProgress.set(false);
                });
            }
        });
    }

    private void setupMediaPlayerCallbacks() {
        mediaPlayer.setOnReady(() -> {
            Duration total = mediaPlayer.getTotalDuration();
            if (total != null && !total.isUnknown() && total.toSeconds() > 0) {
                progressSlider.setMax(total.toSeconds());
                totalTimeLabel.setText(formatTime(total));
            } else {
                // 폴링으로 재시도
                ScheduledExecutorService poller = Executors.newSingleThreadScheduledExecutor();
                AtomicInteger tries = new AtomicInteger(0);
                poller.scheduleAtFixedRate(() -> {
                    try {
                        Duration d = mediaPlayer.getTotalDuration();
                        if (d != null && !d.isUnknown() && d.toSeconds() > 0) {
                            Platform.runLater(() -> { progressSlider.setMax(d.toSeconds()); totalTimeLabel.setText(formatTime(d)); });
                            poller.shutdown();
                        } else if (tries.incrementAndGet() >= 12) poller.shutdown();
                    } catch (Exception ex) { poller.shutdown(); }
                }, 200, 500, TimeUnit.MILLISECONDS);
            }
            mediaPlayer.play();
            playBtn.setText("⏸");
        });

        mediaPlayer.setOnEndOfMedia(() -> {
            if (repeatMode == RepeatMode.ONE) { mediaPlayer.seek(Duration.ZERO); mediaPlayer.play(); }
            else playNext();
        });

        mediaPlayer.setOnError(() -> {
            showAlert("Playback Error", "Could not play: " + mediaPlayer.getError());
            disposeMediaPlayer();
        });
    }

    // ── 앨범 아트 ────────────────────────────────────────────────────────
    private void updateAlbumArt(Image image) {
        albumPane.getChildren().clear();
        if (image != null) {
            albumArt.setImage(image);
            albumArt.setFitWidth(44);
            albumArt.setFitHeight(44);
            albumArt.setPreserveRatio(true);
            Rectangle clip = new Rectangle(44, 44);
            clip.setArcWidth(6); clip.setArcHeight(6);
            albumArt.setClip(clip);
            albumPane.getChildren().add(albumArt);
        } else {
            Rectangle placeholder = new Rectangle(44, 44);
            placeholder.setFill(Color.web(C_ITEM_SEL));
            placeholder.setArcWidth(6); placeholder.setArcHeight(6);
            Label note = new Label("♪");
            note.setStyle("-fx-text-fill:" + C_TEXT_SEC + "; -fx-font-size:18px;");
            albumPane.getChildren().addAll(placeholder, note);
        }
    }

    private void updateTrackInfo(String baseName) {
        String display = baseName.length() > 35 ? baseName.substring(0, 32) + "…" : baseName;
        trackTitle.setText(display);
        trackArtist.setText("—");
        updatePlaylistStatus();
    }

    private void updatePlaylistStatus() {
        String status = "No Playlist";
        if (currentPlaylistName != null && !currentPlaylistName.isEmpty()) {
            String statusIcon = "⏹";
            if (mediaPlayer != null) {
                switch (mediaPlayer.getStatus()) {
                    case PLAYING -> statusIcon = "▶";
                    case PAUSED  -> statusIcon = "⏸";
                    case STOPPED -> statusIcon = "⏹";
                    default      -> statusIcon = "⏹";
                }
            }
            status = currentPlaylistName + " " + statusIcon;
        }
        playlistStatusLabel.setText(status);
    }

    // ── yt-dlp 다운로드 ──────────────────────────────────────────────────
    private void downloadAndPlayYoutubeAudio(String url) {
        showDownloadProgressWindow(url, mp3 -> Platform.runLater(() -> {
            File urlFile = new File("URL: " + mp3.getName());
            urlMappings.put(urlFile.getName(), mp3.toURI().toString());
            if (currentView == View.PLAYLIST_DETAIL && currentPlaylistName != null) {
                ObservableList<File> pl = playlists.get(currentPlaylistName);
                if (pl != null && !pl.contains(urlFile)) { pl.add(urlFile); saveDataToFile(); updateContentView(); }
            } else {
                if (!allSongs.contains(urlFile)) { allSongs.add(urlFile); saveDataToFile(); updateContentView(); }
            }
        }));
    }

    private File getLibExe(String exeName) {
        try {
            URI uri = aurora.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            File codeLoc = new File(uri);
            File baseDir = codeLoc.isDirectory() ? codeLoc : codeLoc.getParentFile();
            if (baseDir == null) baseDir = new File(System.getProperty("user.dir"));
            File candidate = new File(baseDir, "lib" + File.separator + exeName);
            if (candidate.exists()) return candidate;
        } catch (Exception ignored) {}
        File fallback = new File(System.getProperty("user.dir"), "lib" + File.separator + exeName);
        return fallback.exists() ? fallback : new File(exeName);
    }

    private void showDownloadProgressWindow(String url, Consumer<File> onComplete) {
        Stage dlStage = new Stage();
        dlStage.initModality(Modality.APPLICATION_MODAL);
        dlStage.initStyle(StageStyle.UNDECORATED);

        ProgressBar bar = new ProgressBar(0);
        bar.setPrefWidth(360);
        bar.setStyle("-fx-accent:" + C_ACCENT + ";");

        Label statusLbl = new Label("Starting download…");
        statusLbl.setStyle("-fx-text-fill:" + C_TEXT_SEC + "; -fx-font-size:12px;");
        statusLbl.setWrapText(true);
        statusLbl.setMaxWidth(360);

        Label title = new Label("Downloading audio");
        title.setStyle("-fx-text-fill:" + C_TEXT_PRI + "; -fx-font-size:14px; -fx-font-weight:bold;");

        VBox box = new VBox(12, title, bar, statusLbl);
        box.setPadding(new Insets(24));
        box.setStyle("-fx-background-color:" + C_BG + "; -fx-border-color:" + C_DIVIDER + "; -fx-border-width:1; -fx-background-radius:10;");
        dlStage.setScene(new Scene(box));
        dlStage.show();

        new Thread(() -> {
            File[] mp3 = {null};
            try {
                String ts = String.valueOf(System.currentTimeMillis());
                mp3[0] = new File(dataFolder, "yt_audio_" + ts + ".mp3");
                boolean isWin = System.getProperty("os.name").toLowerCase().contains("win");
                File ytdlp = getLibExe(isWin ? "yt-dlp.exe" : "yt-dlp");

                String[] cmd = { ytdlp.getAbsolutePath(), "--no-playlist", "--extract-audio",
                                 "--audio-format","mp3", "-o", mp3[0].getAbsolutePath(), url };
                int exit = runProcessWithProgress(cmd, bar, statusLbl, 0.0, 1.0);

                if (exit != 0 || !mp3[0].exists() || mp3[0].length() == 0)
                    throw new IOException("yt-dlp failed (exit=" + exit + ")");

                Platform.runLater(() -> {
                    dlStage.close();
                    if (onComplete != null) onComplete.accept(mp3[0]);
                    showAlert("Done", "Download complete!");
                });

            } catch (Exception e) {
                if (mp3[0] != null && mp3[0].exists()) mp3[0].delete();
                Platform.runLater(() -> {
                    dlStage.close();
                    showAlert("Download Failed", e.getMessage());
                });
            }
        }, "yt-download").start();
    }

    private int runProcessWithProgress(String[] command, ProgressBar progressBar, Label label, double min, double max)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process process = pb.start();

        ExecutorService ex = Executors.newSingleThreadExecutor();
        Future<?> reader = ex.submit(() -> {
            try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line; int count = 0;
                while ((line = br.readLine()) != null) {
                    double progress = min + Math.min(++count / 120.0, 1.0) * (max - min);
                    String shortLine = line.length() > 120 ? line.substring(0, 120) + "…" : line;
                    Platform.runLater(() -> { try { progressBar.setProgress(progress); label.setText(shortLine); } catch (Exception ignored) {} });
                }
            } catch (IOException ignored) {}
        });

        boolean done = process.waitFor(600, TimeUnit.SECONDS);
        if (!done) { process.destroyForcibly(); reader.cancel(true); ex.shutdownNow(); throw new IOException("Process timed out."); }
        try { reader.get(2, TimeUnit.SECONDS); } catch (Exception ignored) {} finally { ex.shutdownNow(); }
        Platform.runLater(() -> { try { progressBar.setProgress(max); } catch (Exception ignored) {} });
        return process.exitValue();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  저장 / 불러오기
    // ══════════════════════════════════════════════════════════════════════
    private void saveDataToFile() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(new File(dataFolder, DATA_FILE)))) {
            oos.writeObject(toPathList(allSongs));
            oos.writeObject(toPlaylistPaths());
            oos.writeObject(urlMappings);
        } catch (IOException e) { System.err.println("Save failed: " + e.getMessage()); }
    }

    private List<String> toPathList(ObservableList<File> files) {
        List<String> out = new ArrayList<>();
        for (File f : files) out.add(f.getName().startsWith("URL: ") ? f.getName() : f.getAbsolutePath());
        return out;
    }

    private Map<String, List<String>> toPlaylistPaths() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        playlists.forEach((k, v) -> out.put(k, toPathList(v)));
        return out;
    }

    @SuppressWarnings("unchecked")
    private void loadDataFromFile() {
        File f = new File(dataFolder, DATA_FILE);
        if (!f.exists()) return;
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(f))) {
            loadSongsFromPaths((List<String>) ois.readObject());
            loadPlaylistsFromData((Map<String, List<String>>) ois.readObject());
            try {
                Map<String, String> loaded = (Map<String, String>) ois.readObject();
                loaded.forEach((k, v) -> {
                    String nameKey = k;
                    try { String n = new File(k).getName(); if (n != null && !n.isBlank()) nameKey = n; } catch (Exception ignored) {}
                    urlMappings.putIfAbsent(nameKey, v);
                });
            } catch (Exception ignored) {}
        } catch (Exception e) { System.err.println("Load failed: " + e.getMessage()); }
    }

    private void loadSongsFromPaths(List<String> paths) {
        allSongs.clear();
        for (String p : paths) {
            if (p.startsWith("URL: ")) allSongs.add(new File(p));
            else { File mf = new File(p); if (mf.exists()) allSongs.add(mf); }
        }
    }

    private void loadPlaylistsFromData(Map<String, List<String>> data) {
        playlists.clear();
        data.forEach((name, paths) -> {
            ObservableList<File> pl = FXCollections.observableArrayList();
            for (String p : paths) {
                if (p.startsWith("URL: ")) pl.add(new File(p));
                else { File mf = new File(p); if (mf.exists()) pl.add(mf); }
            }
            playlists.put(name, pl);
        });
    }

    // ══════════════════════════════════════════════════════════════════════
    //  다이얼로그
    // ══════════════════════════════════════════════════════════════════════
    private void showAlert(String title, String content) {
        Platform.runLater(() -> {
            Stage d = new Stage();
            d.initModality(Modality.APPLICATION_MODAL);
            d.initStyle(StageStyle.UNDECORATED);

            Label lbl = new Label(content);
            lbl.setStyle("-fx-text-fill:" + C_TEXT_SEC + "; -fx-font-size:13px;");
            lbl.setWrapText(true); lbl.setMaxWidth(380);

            Button ok = dialogBtn("OK", C_ACCENT, C_PLAY_ICON);
            ok.setOnAction(e -> d.close());

            Label hdr = new Label(title);
            hdr.setStyle("-fx-text-fill:" + C_TEXT_PRI + "; -fx-font-size:14px; -fx-font-weight:bold;");

            HBox btnRow = new HBox(ok);
            btnRow.setAlignment(Pos.CENTER_RIGHT);

            VBox box = new VBox(10, hdr, lbl, btnRow);
            box.setPadding(new Insets(20));
            box.setStyle("-fx-background-color:" + C_BG + "; -fx-border-color:" + C_DIVIDER + "; -fx-border-width:1; -fx-background-radius:10;");
            d.setScene(new Scene(box));
            d.showAndWait();
        });
    }

    private Optional<String> showCustomTextInputDialog(String title, String prompt) {
        final String[] result = {null};
        Stage d = new Stage();
        d.initModality(Modality.APPLICATION_MODAL);
        d.initStyle(StageStyle.UNDECORATED);

        Label hdr = new Label(title);
        hdr.setStyle("-fx-text-fill:" + C_TEXT_PRI + "; -fx-font-size:14px; -fx-font-weight:bold;");

        Label lbl = new Label(prompt);
        lbl.setStyle("-fx-text-fill:" + C_TEXT_SEC + "; -fx-font-size:12px;");

        TextField input = new TextField();
        input.setPrefWidth(320);
        input.setStyle("-fx-background-color:" + C_SURFACE + "; -fx-text-fill:" + C_TEXT_PRI
                     + "; -fx-border-color:" + C_DIVIDER + "; -fx-border-width:1; -fx-border-radius:6; -fx-background-radius:6; -fx-padding:7 10;");

        Button ok     = dialogBtn("OK",     C_ACCENT,   C_PLAY_ICON);
        Button cancel = dialogBtn("Cancel", C_ITEM_SEL, C_TEXT_PRI);
        ok.setOnAction(e -> { result[0] = input.getText(); d.close(); });
        cancel.setOnAction(e -> d.close());
        input.setOnAction(e -> ok.fire());

        HBox btns = new HBox(8, cancel, ok);
        btns.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(10, hdr, lbl, input, btns);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color:" + C_BG + "; -fx-border-color:" + C_DIVIDER + "; -fx-border-width:1; -fx-background-radius:10;");
        d.setScene(new Scene(box));
        d.showAndWait();

        return Optional.ofNullable(result[0]).map(String::trim).filter(s -> !s.isEmpty());
    }

    private boolean showConfirmDialog(String title, String message) {
        final boolean[] confirmed = {false};
        Stage d = new Stage();
        d.initModality(Modality.APPLICATION_MODAL);
        d.initStyle(StageStyle.UNDECORATED);

        Label hdr = new Label(title);
        hdr.setStyle("-fx-text-fill:" + C_TEXT_PRI + "; -fx-font-size:14px; -fx-font-weight:bold;");

        Label msg = new Label(message);
        msg.setStyle("-fx-text-fill:" + C_TEXT_SEC + "; -fx-font-size:12px;");

        Button del    = dialogBtn("Delete", C_DANGER, "#FFFFFF");
        Button cancel = dialogBtn("Cancel", C_ITEM_SEL, C_TEXT_PRI);
        del.setOnAction(e -> { confirmed[0] = true; d.close(); });
        cancel.setOnAction(e -> d.close());

        HBox btns = new HBox(8, cancel, del);
        btns.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(10, hdr, msg, btns);
        box.setPadding(new Insets(20));
        box.setStyle("-fx-background-color:" + C_BG + "; -fx-border-color:" + C_DIVIDER + "; -fx-border-width:1; -fx-background-radius:10;");
        d.setScene(new Scene(box));
        d.showAndWait();
        return confirmed[0];
    }

    private Button dialogBtn(String text, String bg, String fg) {
        Button b = new Button(text);
        String base = "-fx-background-color:" + bg + "; -fx-text-fill:" + fg
                    + "; -fx-font-size:12px; -fx-font-weight:bold; -fx-cursor:hand;"
                    + "-fx-padding:7 18; -fx-background-radius:7; -fx-border-width:0;";
        b.setStyle(base);
        return b;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  유틸
    // ══════════════════════════════════════════════════════════════════════
    private String formatTime(Duration d) {
        if (d == null || d.toSeconds() < 0) return "0:00";
        int t = (int) d.toSeconds();
        return String.format("%d:%02d", t / 60, t % 60);
    }

    private String stripExtension(String name) {
        if (name == null) return "";
        String prefix = "";
        if (name.startsWith("URL: ")) { prefix = ""; name = name.substring(5).replaceAll("[/\\\\]", "_"); }
        int sep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String base = sep >= 0 ? name.substring(sep + 1) : name;
        int dot = base.lastIndexOf('.');
        return dot > 0 ? base.substring(0, dot) : base;
    }

    private void loadTitleIcon(Label logo) {
        try {
            InputStream is = getClass().getResourceAsStream("/aurora.png");
            if (is != null) {
                ImageView iv = new ImageView(new Image(is));
                iv.setFitWidth(18); iv.setFitHeight(18); iv.setPreserveRatio(true);
                logo.setGraphic(iv); logo.setContentDisplay(ContentDisplay.LEFT);
            }
        } catch (Exception ignored) {}
    }

    private void loadCustomFont() {
        try {
            InputStream fs = getClass().getResourceAsStream("/NotoSans-Regular.ttf");
            if (fs != null) Font.loadFont(fs, 12);
        } catch (Exception ignored) {}
    }

    private void setApplicationIcon(Stage stage) {
        try {
            InputStream is = getClass().getResourceAsStream("/aurora.png");
            stage.getIcons().add(is != null ? new Image(is) : createDefaultIcon());
        } catch (Exception e) { stage.getIcons().add(createDefaultIcon()); }
    }

    private Image createDefaultIcon() {
        int size = 64;
        WritableImage icon = new WritableImage(size, size);
        PixelWriter pw = icon.getPixelWriter();
        int cx = size/2, cy = size/2, r = size/2 - 4;
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++)
                pw.setColor(x, y, Math.sqrt(Math.pow(x-cx,2)+Math.pow(y-cy,2)) <= r ? Color.web(C_ACCENT) : Color.TRANSPARENT);
        return icon;
    }
}
