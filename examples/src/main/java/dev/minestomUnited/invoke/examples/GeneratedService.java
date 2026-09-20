package dev.minestomUnited.invoke.examples;

import dev.minestomUnited.invoke.InvokeService;
import net.minestom.server.codec.Codec;
import net.minestom.server.codec.StructCodec;

@InvokeService
public interface GeneratedService {
    record GreetArgs(String name) {
        public static final StructCodec<GreetArgs> CODEC = StructCodec.struct(
                "name", Codec.STRING, GreetArgs::name,
                GreetArgs::new);
    }

    record Greeting(String message) {
        public static final StructCodec<Greeting> CODEC = StructCodec.struct(
                "message", Codec.STRING, Greeting::message,
                Greeting::new);
    }

    Greeting greet(GreetArgs args);

    void forget(GreetArgs args);

    class Example implements GeneratedService {
        @Override
        public Greeting greet(GreetArgs args) {
            return new Greeting("hello, " + args.name());
        }

        @Override
        public void forget(GreetArgs args) {
        }
    }
}
