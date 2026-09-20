package dev.minestomUnited.invoke.examples;

import java.util.HashSet;
import java.util.Set;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

public interface VoidService {
    record PingArgs(String id) {
        public static final StructCodec<PingArgs> CODEC = StructCodec.struct(
                "id", Codec.STRING, PingArgs::id,
                PingArgs::new);
    }

    void ping(PingArgs args);

    class Example implements VoidService {
        private final Set<String> seen = new HashSet<>();

        @Override
        public void ping(PingArgs args) {
            seen.add(args.id());
        }

        public boolean seen(String id) {
            return seen.contains(id);
        }
    }
}
