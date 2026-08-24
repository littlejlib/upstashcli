package littlejlib.upstashcli.app;

import module java.base;
import java.util.function.Consumer;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import littlejlib.upstashcli.relay.SettingsStore;
import luvjfx.FxTextField;

import static luvjfx.Fx.*;

/** What a person sees when they double-click the exe: which end am I?
 *  <p>
 *  This window has text boxes in it, so every letter and digit belongs to typing and the two
 *  actions get function keys. Enter commits whichever form the focus is in. */
public final class LauncherWindow {

    static final List<String> SHELLS = List.of("cmd.exe", "powershell.exe", "pwsh.exe");

    final Stage stage;
    final AppArgs args;
    final Consumer<AppArgs> go;

    final FxTextField nodeField;
    final FxTextField sessionField = textField($ -> $.promptText("123 456 789").prefColumnCount(12));
    final luvjfx.FxPasswordField passwordField = passwordField($ -> $.promptText("one-time password"));
    final luvjfx.FxComboBox<String> shellBox = comboBox(String.class);

    public LauncherWindow(Stage stage, AppArgs args, Consumer<AppArgs> go) {
        this.stage = stage;
        this.args = args;
        this.go = go;
        this.nodeField = textField($ -> $.text(NodeHost.freeName(args.node())).prefColumnCount(12));
        build();
    }

    void build() {
        var settings = SettingsStore.load();
        shellBox.node.getItems().setAll(SHELLS);
        shellBox.node.setEditable(true);
        shellBox.node.setValue(args.shell() != null ? args.shell()
                : settings.defaultShell() == null ? SHELLS.getFirst() : settings.defaultShell());
        if (args.sessionId() != null) sessionField.text(args.sessionId());
        if (args.password() != null) passwordField.text(args.password());

        var share = vbox($ -> $.spacing(7).padding(14).styleClass("bar")).nodes(
                label("Share this machine").styleClass("card-title"),
                label("Runs a shell here and gives you an id and a one-time password to read out.")
                        .styleClass("card-detail"),
                hbox($ -> $.spacing(8)).nodes(
                        label("shell").styleClass("subtitle"),
                        shellBox,
                        button("F2  Start sharing").styleClass("primary").onAction(e -> host())));

        var connect = vbox($ -> $.spacing(7).padding(14).styleClass("bar")).nodes(
                label("Connect to a shared machine").styleClass("card-title"),
                label("Mirrors the far shell here. You can type into it, and so can claude-code "
                      + "through the cli.").styleClass("card-detail"),
                hbox($ -> $.spacing(8)).nodes(
                        label("id").styleClass("subtitle"), sessionField,
                        label("password").styleClass("subtitle"), passwordField,
                        button("F3  Connect").styleClass("primary").onAction(e -> join())));

        var nodeRow = hbox($ -> $.spacing(8)).nodes(
                label("node name").styleClass("subtitle"),
                nodeField,
                label("this is the name claude-code passes to --node").styleClass("card-detail"));

        var root = vbox($ -> $.spacing(12).padding(16)).nodes(
                label("upstashcli").styleClass("title"),
                label("A shared terminal across the internet, with no inbound port and no admin rights.")
                        .styleClass("subtitle"),
                share, connect, nodeRow);

        var scene = scene(root);
        Ui.dress(scene);
        Keys.functionKeys(scene, () -> true, Map.of(
                KeyCode.F2, this::host,
                KeyCode.F3, this::join,
                KeyCode.F4, this::manager));
        Keys.onEscape(scene, stage::close);
        sessionField.node.setOnAction(e -> join());
        passwordField.node.setOnAction(e -> join());

        Ui.icons(stage, Icons.TRAY_ACCENT);
        stage.setTitle("upstashcli");
        stage.setScene(scene);
        var legend = Ui.legend("F2", "share this machine", "F3", "connect to one",
                "F4", "manager", "Esc", "close");
        ((javafx.scene.layout.VBox) scene.getRoot()).getChildren().add(legend.node);
        stage.show();
    }

    void host() {
        var shell = shellBox.node.getValue();
        go.accept(new AppArgs(AppRole.HOST, node(), shell == null || shell.isBlank() ? null : shell.trim(), null, null, false));
    }

    void join() {
        var id = littlejlib.upstashcli.relay.Ids.normalise(sessionField.node.getText());
        var pw = littlejlib.upstashcli.relay.Ids.normalise(passwordField.node.getText());
        if (!littlejlib.upstashcli.relay.Ids.isSessionId(id)) {
            Dialogs.error(stage, "That is not a session id",
                    "A session id is nine digits, the way the host window shows it. Spaces are fine.");
            sessionField.node.requestFocus();
            return;
        }
        if (pw == null || pw.isBlank()) {
            Dialogs.error(stage, "The password is missing",
                    "The host window shows a one-time password next to the id. It works once, for this session.");
            passwordField.node.requestFocus();
            return;
        }
        go.accept(new AppArgs(AppRole.JOIN, node(), null, id, pw, false));
    }

    void manager() {
        go.accept(new AppArgs(AppRole.TRAY, node(), null, null, null, false));
    }

    String node() {
        var n = nodeField.node.getText();
        return n == null || n.isBlank() ? AppArgs.DEFAULT_NODE : n.trim();
    }
}
