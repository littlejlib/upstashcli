package littlejlib.upstashcli.cli;

import module java.base;
import littlejlib.upstashcli.node.NodeClient;
import picocli.CommandLine.Option;

public final class CommonOpts {

    @Option(names = "--node", description = "which local node to talk to (default: ${DEFAULT-VALUE})")
    public String node = "default";

    @Option(names = "--json", description = "print the node's answer as JSON")
    public boolean json;

    @Option(names = "--no-start", description = "fail instead of starting a node that is not running ('status' never starts one)")
    public boolean noStart;

    /** On the mixin rather than on each command, so every verb answers -h with its own usage.
     *  The tool as a whole is explained by "upstashcli guide". */
    @Option(names = {"-h", "--help"}, usageHelp = true, description = "usage for this verb; see 'upstashcli guide' for the manual")
    public boolean help;

    public NodeClient client() {
        var c = new NodeClient(node);
        if (!c.running()) {
            if (noStart) throw new IllegalStateException("no node '" + node + "' is running, and --no-start was given");
            c.ensureRunning(null, Self.jar());
        }
        return c;
    }

    public NodeClient existing() {
        return new NodeClient(node);
    }
}
