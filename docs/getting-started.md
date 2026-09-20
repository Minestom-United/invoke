# Getting started

Five minutes from an empty project to a typed call. You write a plain
interface, register an implementation, and call it through a client.
No builders, no hand-built requests.

Requires Java 25. The Minestom codec artifact ships JVM 25 class files,
so anything older fails before your code loads.

## Install

Group is `dev.minestom-united.invoke`, current version `0.1.0-SNAPSHOT`
(see `version=` in `gradle.properties`). Pick the modules you need:

```groovy
dependencies {
    implementation "dev.minestom-united.invoke:invoke-runtime:0.1.0-SNAPSHOT"
    implementation "dev.minestom-united:codec:0.1.0"
}
```

What each piece is for:

- `invoke-runtime` — the Java core: `InvokeRegistry`, `InvokeClientFactory`,
  envelopes, the JDK transport, `InvokeException`, and the canonical
  `@InvokeService` annotation.
- `dev.minestom-united:codec` — the Minestom codec, used to describe
  Java records field by field. Every argument and result record carries
  a `StructCodec` built with it.

To generate clients at compile time instead of using the factory,
apply the plugin (id `dev.minestom-united.invoke`, lives in `invoke-gradle-plugin`)
and set the one required option:

```groovy
plugins {
    id("dev.minestom-united.invoke") version "0.1.0-SNAPSHOT"
}

invoke {
    packageName = "com.example.shop"
}
```

`clientSuffix` defaults to `"Client"` and only needs setting when the
default clashes with an existing class. The plugin wires the Java
annotation processor that reads `@InvokeService` and writes the client
classes, and it fails the build if `packageName` is blank.

## Your first service, end to end

This is the `LookupService` from `examples/`, trimmed. One argument in,
one value out.

Step 1, the records. Each one carries its own codec:

```java
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
}
```

Step 2, the implementation. Plain class, no base type. In `examples/`
the demo impl lives nested in the interface as `LookupService.Example`:

```java
import java.util.Map;

// inside interface LookupService, as in examples/LookupService.java:
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
```

(`InvokeException` is `dev.minestom-united.invoke.runtime.InvokeException`. Throwing it
with a code is how the server learns the error code. More in
[errors.md](errors.md).)

Step 3, register on the server and call through a client:

```java
import dev.minestomUnited.invoke.runtime.InvokeClientFactory;
import dev.minestomUnited.invoke.runtime.InvokeRegistry;

InvokeRegistry registry = new InvokeRegistry();
registry.register(new LookupService.Example(Map.of("a", "alpha")));

LookupService client =
        InvokeClientFactory.create(LookupService.class, "http://localhost:8080");
LookupService.ItemResult out =
        client.lookup(new LookupService.KeyArgs("a"));
```

Over real HTTP, the client POSTs this to `/LookupService/lookup`:

```json
{"id":"a"}
```

and the server answers:

```json
{"result":{"id":"a","label":"alpha"}}
```

Missing key, same call:

```java
client.lookup(new LookupService.KeyArgs("missing"));
// throws InvokeException(404, "item not found: missing")
```

`@InvokeService` on the interface is only needed for client generation.
The registry and the factory work without it.

## Running the sample

`examples/` holds one service per file per shape (void, nested,
lists and maps, nullable, numbers, errors, multi-method), each one a
passing test. Run them all:

```sh
./gradlew :examples:test
```

Full suite, every module:

```sh
./gradlew test
```

To watch a single shape end to end, open its test. `VoidServiceTest`
is the shortest: it registers the impl, calls `ping` through the
factory client, and asserts the exact request bytes plus the raw
dispatch result:

```java
InvokeRegistry registry = new InvokeRegistry();
VoidService.Example impl = new VoidService.Example();
registry.register(impl);
LoopbackHttp http = new LoopbackHttp(registry);
VoidService client = InvokeClientFactory.create(VoidService.class, "http://localhost:8080", http);

client.ping(new VoidService.PingArgs("p1"));

assertTrue(impl.seen("p1"));
assertEquals("http://localhost:8080/VoidService/ping", http.lastUrl);
assertEquals("{\"id\":\"p1\"}", http.lastBody);
InvokeRegistry.DispatchResult raw =
        registry.dispatch("VoidService", "ping", "{\"id\":\"p1\"}");
assertEquals(200, raw.status());
assertEquals("{\"result\":null}", raw.body());
```

`LoopbackHttp` is a test transport in `examples/` that routes client
calls straight into the registry instead of over the network. Steal
it for your own tests. Details in [client-guide.md](client-guide.md).

Next: [authoring-services.md](authoring-services.md) for the rules
every service follows, or [ENVELOPE.md](../ENVELOPE.md) for the wire
contract behind them.