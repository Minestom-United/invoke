package dev.minestomUnited.invoke.examples;

import java.util.HashMap;
import java.util.Map;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

public interface MultiService {
    record GetArgs(String key) {
        public static final StructCodec<GetArgs> CODEC = StructCodec.struct(
                "key", Codec.STRING, GetArgs::key,
                GetArgs::new);
    }

    record GetResult(String value) {
        public static final StructCodec<GetResult> CODEC = StructCodec.struct(
                "value", Codec.STRING, GetResult::value,
                GetResult::new);
    }

    record PutArgs(String key, String value) {
        public static final StructCodec<PutArgs> CODEC = StructCodec.struct(
                "key", Codec.STRING, PutArgs::key,
                "value", Codec.STRING, PutArgs::value,
                PutArgs::new);
    }

    record PutResult(boolean replaced) {
        public static final StructCodec<PutResult> CODEC = StructCodec.struct(
                "replaced", Codec.BOOLEAN, PutResult::replaced,
                PutResult::new);
    }

    record ClearArgs(String prefix) {
        public static final StructCodec<ClearArgs> CODEC = StructCodec.struct(
                "prefix", Codec.STRING, ClearArgs::prefix,
                ClearArgs::new);
    }

    GetResult get(GetArgs args);

    PutResult put(PutArgs args);

    void clear(ClearArgs args);

    class Example implements MultiService {
        private final Map<String, String> store = new HashMap<>();

        @Override
        public GetResult get(GetArgs args) {
            return new GetResult(store.getOrDefault(args.key(), ""));
        }

        @Override
        public PutResult put(PutArgs args) {
            return new PutResult(store.put(args.key(), args.value()) != null);
        }

        @Override
        public void clear(ClearArgs args) {
            store.keySet().removeIf(key -> key.startsWith(args.prefix()));
        }
    }
}
