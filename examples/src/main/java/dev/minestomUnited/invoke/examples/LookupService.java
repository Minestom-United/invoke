package dev.minestomUnited.invoke.examples;

import dev.minestomUnited.invoke.runtime.InvokeException;
import java.util.Map;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

public interface LookupService {
    record KeyArgs(String id) {
        public static final StructCodec<KeyArgs> CODEC = StructCodec.struct(
                "id", Codec.STRING, KeyArgs::id,
                KeyArgs::new);
    }

    record ItemResult(String id, String label) {
        public static final StructCodec<ItemResult> CODEC = StructCodec.struct(
                "id", Codec.STRING, ItemResult::id,
                "label", Codec.STRING, ItemResult::label,
                ItemResult::new);
    }

    ItemResult lookup(KeyArgs args);

    class Example implements LookupService {
        private final Map<String, String> items;

        public Example(Map<String, String> items) {
            this.items = Map.copyOf(items);
        }

        @Override
        public ItemResult lookup(KeyArgs args) {
            String label = items.get(args.id());
            if (label == null) {
                throw new InvokeException(404, "item not found: " + args.id());
            }
            return new ItemResult(args.id(), label);
        }
    }
}
