package littlejlib.upstashcli.cli;

import module java.base;
import picocli.CommandLine.*;

@Command(name = "join", description = "Connect to a shared session using its id and one-time password.")
public final class JoinCmd implements Callable<Integer> {

    @Mixin CommonOpts opts;

    @Parameters(index = "0", description = "session id (nine digits, spaces ignored)")
    String sessionId;

    @Option(names = {"-p", "--password"}, required = true, description = "the one-time password")
    String password;

    @Override
    public Integer call() {
        var r = opts.client().call("join", Map.of("sessionId", sessionId, "password", password), Duration.ofMinutes(2));
        if (opts.json) {
            Out.json(r);
            return 0;
        }
        var status = r.path("status");
        Out.line("Joined " + r.path("sessionId").asText() + " - " + status.path("detail").asText());
        if (status.hasNonNull("hostName")) {
            Out.line("Host " + status.path("hostName").asText() + " running " + status.path("shell").asText("a shell"));
        }
        return status.path("usable").asBoolean(false) ? 0 : 4;
    }
}
