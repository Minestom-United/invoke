package dev.minestomUnited.invoke.examples;

import java.util.List;
import java.util.Map;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

public interface CollectionService {
    record BagArgs(List<String> items, List<Double> scores, Map<String, Integer> counts) {
        public static final StructCodec<BagArgs> CODEC = StructCodec.struct(
                "items", Codec.STRING.list(), BagArgs::items,
                "scores", Codec.DOUBLE.list(), BagArgs::scores,
                "counts", Codec.STRING.mapValue(Codec.INT), BagArgs::counts,
                BagArgs::new);
    }

    record BagResult(int total, double mean) {
        public static final StructCodec<BagResult> CODEC = StructCodec.struct(
                "total", Codec.INT, BagResult::total,
                "mean", Codec.DOUBLE, BagResult::mean,
                BagResult::new);
    }

    BagResult summarize(BagArgs args);

    class Example implements CollectionService {
        @Override
        public BagResult summarize(BagArgs args) {
            int total = args.items().size() + args.counts().size();
            double mean = args.scores().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            return new BagResult(total, mean);
        }
    }
}
