package littlejlib.upstashcli.cli;

import module java.base;

public final class Self {

    /** Where this cli is running from, so it can start a node with the same code. */
    public static Path jar() {
        try {
            var src = Self.class.getProtectionDomain().getCodeSource();
            if (src == null) throw new IllegalStateException("cannot locate the upstashcli jar");
            return Paths.get(src.getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("cannot locate the upstashcli jar", e);
        }
    }

    private Self() {}
}
