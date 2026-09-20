package dev.minestomUnited.invoke.examples;

import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

public interface NumbersService {
    record MeasureArgs(int count, long total, double ratio, boolean ok) {
        public static final StructCodec<MeasureArgs> CODEC = StructCodec.struct(
                "count", Codec.INT, MeasureArgs::count,
                "total", Codec.LONG, MeasureArgs::total,
                "ratio", Codec.DOUBLE, MeasureArgs::ratio,
                "ok", Codec.BOOLEAN, MeasureArgs::ok,
                MeasureArgs::new);
    }

    record MeasureResult(long projected, boolean healthy) {
        public static final StructCodec<MeasureResult> CODEC = StructCodec.struct(
                "projected", Codec.LONG, MeasureResult::projected,
                "healthy", Codec.BOOLEAN, MeasureResult::healthy,
                MeasureResult::new);
    }

    MeasureResult measure(MeasureArgs args);

    class Example implements NumbersService {
        @Override
        public MeasureResult measure(MeasureArgs args) {
            long projected = args.total() + (long) (args.count() * args.ratio());
            return new MeasureResult(projected, args.ok() && projected > 0);
        }
    }
}
