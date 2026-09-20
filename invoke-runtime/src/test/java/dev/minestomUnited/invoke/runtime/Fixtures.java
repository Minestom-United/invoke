package dev.minestomUnited.invoke.runtime;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

import java.util.List;

final class Fixtures {
    private Fixtures() {
    }

    record ReviewArgs(String reviewId, String personId) {
        public static final StructCodec<ReviewArgs> CODEC = StructCodec.struct(
                "reviewId", Codec.STRING, ReviewArgs::reviewId,
                "personId", Codec.STRING, ReviewArgs::personId,
                ReviewArgs::new);
    }

    record ReviewResult(String status) {
        public static final StructCodec<ReviewResult> CODEC = StructCodec.struct(
                "status", Codec.STRING, ReviewResult::status,
                ReviewResult::new);
    }

    record SampleArgs(String name, int samples, boolean confirmed) {
        public static final StructCodec<SampleArgs> CODEC = StructCodec.struct(
                "name", Codec.STRING, SampleArgs::name,
                "samples", Codec.INT, SampleArgs::samples,
                "confirmed", Codec.BOOLEAN, SampleArgs::confirmed,
                SampleArgs::new);
    }

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

    interface ReviewService {
        ReviewResult resolveReview(ReviewArgs args);

        void pingReview(ReviewArgs args);
    }

    interface OverloadedService {
        ReviewResult go(ReviewArgs args);

        ReviewResult go(SampleArgs args);
    }

    interface ZeroArgService {
        ReviewResult go();
    }

    interface TwoArgService {
        ReviewResult go(ReviewArgs a, ReviewArgs b);
    }

    interface NonRecordArgService {
        ReviewResult go(String raw);
    }

    interface NonRecordReturnService {
        String go(ReviewArgs args);
    }

    record NoCodecArgs(String value) {
    }

    interface MissingCodecService {
        ReviewResult go(NoCodecArgs args);
    }
}
