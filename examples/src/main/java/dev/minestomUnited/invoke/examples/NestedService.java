package dev.minestomUnited.invoke.examples;

import java.util.List;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

public interface NestedService {
    record Turn(String speaker, double start, double end) {
        public static final StructCodec<Turn> CODEC = StructCodec.struct(
                "speaker", Codec.STRING, Turn::speaker,
                "start", Codec.DOUBLE, Turn::start,
                "end", Codec.DOUBLE, Turn::end,
                Turn::new);
    }

    record CallArgs(Turn turn, List<String> tags) {
        public static final StructCodec<CallArgs> CODEC = StructCodec.struct(
                "turn", Turn.CODEC, CallArgs::turn,
                "tags", Codec.STRING.list(), CallArgs::tags,
                CallArgs::new);
    }

    record CallResult(String summary) {
        public static final StructCodec<CallResult> CODEC = StructCodec.struct(
                "summary", Codec.STRING, CallResult::summary,
                CallResult::new);
    }

    CallResult describe(CallArgs args);

    class Example implements NestedService {
        @Override
        public CallResult describe(CallArgs args) {
            Turn turn = args.turn();
            return new CallResult(turn.speaker() + " " + turn.start() + "-" + turn.end()
                    + " " + String.join(",", args.tags()));
        }
    }
}
