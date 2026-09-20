package dev.minestomUnited.invoke.examples;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

public interface NullableService {
    record NoteArgs(String id, String maybe) {
        public static final StructCodec<NoteArgs> CODEC = StructCodec.struct(
                "id", Codec.STRING, NoteArgs::id,
                "maybe", Codec.STRING.optional(), NoteArgs::maybe,
                NoteArgs::new);
    }

    record NoteResult(String id, String maybeEcho) {
        public static final StructCodec<NoteResult> CODEC = StructCodec.struct(
                "id", Codec.STRING, NoteResult::id,
                "maybeEcho", Codec.STRING.optional(), NoteResult::maybeEcho,
                NoteResult::new);
    }

    NoteResult save(NoteArgs args);

    class Example implements NullableService {
        @Override
        public NoteResult save(NoteArgs args) {
            return new NoteResult(args.id(), args.maybe());
        }
    }
}
