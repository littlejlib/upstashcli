package littlejlib.upstashcli.relay;

import module java.base;

public record TransportChoice(RelayTransport transport, String description, List<String> notes) {}
