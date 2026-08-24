package littlejlib.upstashcli.app;

import module java.base;
import javafx.application.Platform;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Stage;
import littlejlib.upstashcli.node.HostSession;
import littlejlib.upstashcli.relay.Ids;
import luvjfx.FxButton;
import luvjfx.FxLabel;

import static luvjfx.Fx.*;

/** The machine being shared, as its owner sees it. One shell, and the remote end is a second pair
 *  of hands on it rather than a copy of it.
 *  <p>
 *  Every action is a function key with no modifier, and every button wears its key. Letters,
 *  digits, Esc and Tab all belong to the terminal - which is a text input box by any other name -
 *  so they can never be shortcuts here. F11 hands the function keys to the shell for the times
 *  something running in there wants them. */
public final class HostWindow {

    final Stage stage;
    final NodeHost host;
    final HostSession session;
    final TerminalView view;
    final AtomicBoolean ownsFunctionKeys = new AtomicBoolean(true);

    final FxLabel state = Ui.pill("waiting", "pill-wait");
    final FxLabel where = label("").styleClass("card-detail");
    final FxLabel flags = Ui.pill("", "pill-locked").visible(false);
    final FxButton lock = button("F2  Lock remote");
    final FxButton viewOnly = button("F3  View only");
    final FxLabel legendTail = label("").styleClass("label");
    final FxLabel logHint = label("").styleClass("label", "hint");
    final LogPane log = new LogPane();
    final javafx.scene.control.SplitPane split = new javafx.scene.control.SplitPane();

    public HostWindow(Stage stage, NodeHost host) {
        this.stage = stage;
        this.host = host;
        this.session = host.service().host();
        this.view = new TerminalView(new HostTty(session), session.columns(), session.rows(), Fonts.remembered(host));
        session.snapshot(view::screen);
        session.onChange(() -> Platform.runLater(this::refresh));
        session.onActivity(line -> Platform.runLater(() -> {
            log.add(line);
            refresh();
        }));
        build();
    }

    void build() {
        lock.onAction(e -> session.locked(!session.locked()));
        viewOnly.onAction(e -> session.viewOnly(!session.viewOnly()));

        var bar = hbox($ -> $.spacing(10)).styleClass("bar").nodes(
                state,
                label("session " + Ids.prettySessionId(session.sessionId())).styleClass("mono"),
                where, flags);
        bar.node.getChildren().add(Ui.spacer());
        bar.nodes(
                button("F1  Details").onAction(e -> details()),
                lock, viewOnly,
                button("F9  End").styleClass("danger").onAction(e -> endAndClose()));

        var legend = Ui.legend("F4", "copy id", "F5", "copy password", "F6 F7", "font size",
                "F8", "clear this view", "F12", "activity log", "F11", "keys to shell");
        legend.add(legendTail);
        legend.add(logHint);

        split.setOrientation(javafx.geometry.Orientation.VERTICAL);
        split.getItems().add(view.pane());
        var scene = scene(borderPane().top(bar).center(split).bottom(legend));
        Ui.dress(scene);
        Keys.functionKeys(scene, ownsFunctionKeys::get, Map.of(
                KeyCode.F1, this::details,
                KeyCode.F2, () -> session.locked(!session.locked()),
                KeyCode.F3, () -> session.viewOnly(!session.viewOnly()),
                KeyCode.F4, () -> Ui.copy(session.sessionId()),
                KeyCode.F5, () -> Ui.copy(session.password()),
                KeyCode.F6, () -> Fonts.step(host, view, -TerminalView.FONT_STEP, this::refresh),
                KeyCode.F7, () -> Fonts.step(host, view, TerminalView.FONT_STEP, this::refresh),
                KeyCode.F8, view::clear,
                KeyCode.F9, this::endAndClose,
                KeyCode.F12, this::toggleLog));
        // F11 sits outside that map: it is the one key that must still work when the map is off.
        scene.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (!Keys.plain(e, KeyCode.F11)) return;
            e.consume();
            ownsFunctionKeys.set(!ownsFunctionKeys.get());
            refresh();
        });

        Ui.icons(stage, Icons.HOST_ACCENT);
        stage.setTitle("upstashcli - sharing " + session.pty().command() + "  ·  " + host.node());
        stage.setScene(scene);
        stage.setOnCloseRequest(e -> {
            e.consume();
            endAndClose();
        });
        refresh();
        stage.show();
        view.focus();
        Diagnostics.maybeDumpCells(view, "host", Duration.ofSeconds(6));
    }

    /** A split rather than a tab, so the shell stays in sight while the log is open - the point of
     *  reading it is usually to see what happened to what is on the screen. */
    void toggleLog() {
        if (log.showing()) {
            split.getItems().remove(log.node());
            log.showing(false);
        } else {
            split.getItems().add(log.node());
            log.showing(true);
            split.setDividerPositions(0.72);
        }
        refresh();
        view.focus();
    }

    void details() {
        SessionDetails.show(stage, session.sessionId(), session.password(),
                host.service().dispatch("status", null).path("transport").asText("unknown"),
                host.node(), host.port(), Icons.HOST_ACCENT);
    }

    void refresh() {
        var connected = session.connected();
        state.text(connected ? "live" : "waiting").styleClass("pill", connected ? "pill-live" : "pill-wait");
        where.text(session.pty().command() + "  ·  " + view.columns() + "x" + view.rows()
                   + "  ·  " + Math.round(view.fontSize()) + "pt  ·  " + waitingFor(connected));
        lock.text(session.locked() ? "F2  Unlock remote" : "F2  Lock remote");
        viewOnly.text(session.viewOnly() ? "F3  Allow typing" : "F3  View only");
        var restricted = session.locked() ? "remote locked out" : session.viewOnly() ? "remote is view only" : null;
        flags.visible(restricted != null).text(restricted == null ? "" : restricted)
                .styleClass("pill", session.locked() ? "pill-locked" : "pill-view");
        legendTail.text(ownsFunctionKeys.get()
                ? "closing this window ends the session"
                : "the shell has the function keys - F11 takes them back");
        logHint.text(log.hint()).styleClass("label", log.loud() ? "hint-loud" : "hint");
    }

    /** A local-only console is not announced anywhere off this machine, so telling the person to
     *  read out an id and a password would be telling them to do something that cannot work. */
    String waitingFor(boolean connected) {
        if (connected) return "one viewer connected";
        return session.localOnly()
                ? "this machine only - nothing is announced anywhere"
                : "read out the id and password to connect";
    }

    void endAndClose() {
        if (session.connected()) {
            var answer = Dialogs.choose(stage, "End this session?",
                    "A viewer is connected. Ending stops the sharing and locks them out; the shell "
                    + "itself keeps running until this window closes.",
                    List.of("Keep sharing", "End the session"), Icons.HOST_ACCENT);
            if (answer != 1) return;
        }
        Shutdown.now(host, view);
    }
}
