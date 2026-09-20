# Cookbook

One section per service in `examples/`. Each one is a passing test;
run them with `./gradlew :examples:test`. Code below is quoted from
the repo, trimmed only where marked.

## Lookup: impl errors become typed client errors

`LookupService.java`. The shape to copy for keyed fetches.

```java
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
```

```java
@Override
public ItemResult lookup(KeyArgs args) {
    String label = items.get(args.id());
    if (label == null) {
        throw new InvokeException(404, "item not found: " + args.id());
    }
    return new ItemResult(args.id(), label);
}
```

Lesson: the impl picks the code. An `InvokeException` carries its code
and message straight through the envelope, so a missing key is a 404
with the key in the message and the client re-raises the same code —
callers switch on `e.code()`. The dispatch result is byte-exact:
`{"error":{"code":404,"message":"item not found: missing"}}`. Any other
exception type becomes a 500 `internal error`, and the real message
never crosses the wire.

## Void: fire, then confirm the side effect

`VoidService.java` plus `VoidServiceTest.java`.

```java
record PingArgs(String id) {
    public static final StructCodec<PingArgs> CODEC = StructCodec.struct(
            "id", Codec.STRING, PingArgs::id,
            PingArgs::new);
}

void ping(PingArgs args);
```

```java
public void ping(PingArgs args) {
    seen.add(args.id());
}
```

Lesson: void means `{"result":null}` and nothing to decode. The test
asserts the side effect (`impl.seen("p1")`), the exact request bytes
(`{"id":"p1"}`), and the raw dispatch (200, `{"result":null}`).
Handing that same `{"result":null}` to a non-void call is a 500 at the
client, and an impl returning null from a non-void method is a 500 at
the server. Never return null to mean empty.

## Nullable: optional fields with the null rule

`NullableService.java`.

```java
record NoteArgs(String id, String maybe) {
    public static final StructCodec<NoteArgs> CODEC = StructCodec.struct(
            "id", Codec.STRING, NoteArgs::id,
            "maybe", Codec.STRING.optional(), NoteArgs::maybe,
            NoteArgs::new);
}
```

Lesson: mark the field `.optional()` and let it be null. Encoding a null
drops the key, so `new NoteArgs("x", null)` goes out as `{"id":"x"}`.
Decoding a missing key and decoding an explicit JSON null both give you
null — absent and null are the same thing, so
`{"id":"x","maybe":null}` is a valid request that dispatches 200 with
`{"result":{"id":"x"}}`. Because encode drops nulls, a null cannot
survive a decode-then-re-encode pass; that is what keeps a
null-carrying payload a decode-equivalence vector rather than a
byte-identical one. The echo impl is one line
(`return new NoteResult(args.id(), args.maybe())`) and null round-trips
in both directions.

## Nested: compose codecs by referencing them

`NestedService.java`.

```java
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
```

Lesson: a nested record contributes its own `CODEC` — the field is just
`"turn", Turn.CODEC` — so the inner shape is declared once and reused
for both directions. Lists use `Codec.STRING.list()`. Golden vector 4
pins the bytes:
`{"turn":{"speaker":"SPEAKER_01","start":1.5,"end":2.5},"tags":["x"]}`,
and the test decodes that vector, re-encodes it, and compares the
strings.

## Collections: lists and maps

`CollectionService.java`.

```java
record BagArgs(List<String> items, List<Double> scores, Map<String, Integer> counts) {
    public static final StructCodec<BagArgs> CODEC = StructCodec.struct(
            "items", Codec.STRING.list(), BagArgs::items,
            "scores", Codec.DOUBLE.list(), BagArgs::scores,
            "counts", Codec.STRING.mapValue(Codec.INT), BagArgs::counts,
            BagArgs::new);
}
```

```java
@Override
public BagResult summarize(BagArgs args) {
    int total = args.items().size() + args.counts().size();
    double mean = args.scores().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    return new BagResult(total, mean);
}
```

Lesson: lists are `Codec.STRING.list()`, and a map names its key codec
plus its value codec — `Codec.STRING.mapValue(Codec.INT)`, keyed by
string. Collection fields are required: pass empty, never null, unless
absence genuinely carries a meaning, in which case see Nullable above.

## Numbers: every primitive in one place

`NumbersService.java`.

```java
record MeasureArgs(int count, long total, double ratio, boolean ok) {
    public static final StructCodec<MeasureArgs> CODEC = StructCodec.struct(
            "count", Codec.INT, MeasureArgs::count,
            "total", Codec.LONG, MeasureArgs::total,
            "ratio", Codec.DOUBLE, MeasureArgs::ratio,
            "ok", Codec.BOOLEAN, MeasureArgs::ok,
            MeasureArgs::new);
}
```

Lesson: `INT`, `LONG`, `DOUBLE`, and `BOLEAN` map straight onto `int`,
`long`, `double`, and `boolean`, and width survives the trip —
`new MeasureArgs(4, 9_000_000_000L, 1.5, true)` posts
`{"count":4,"total":9000000000,"ratio":1.5,"ok":true}`, so
`9000000000` stays intact well past int range. Golden vector 2 pins the
falsy edge, `{"name":"","samples":0,"confirmed":false}`, and this
service pins the same edge as `{"count":0,"total":0,"ratio":0.0,"ok":false}`.
Empty string, zero, and false are values, not absence; the null rule
never drops them.

## Multi-method: one interface, several routes

`MultiService.java`.

```java
GetResult get(GetArgs args);

PutResult put(PutArgs args);

void clear(ClearArgs args);
```

Lesson: each method is its own route (`/MultiService/get`,
`/MultiService/put`, `/MultiService/clear`) with its own args and result
records, including a void method alongside value methods. One
`register` call exposes all three. Registration is all-or-nothing: a
method that breaks the contract rejects the whole call and leaves
already-registered routes untouched. Overloaded method names are
rejected for the same reason — the route path is just the service name
plus the method name.

## Generated clients: @InvokeService end to end

`GeneratedService.java`, tested by `GeneratedServiceTest.java`.
Everything above hand-wires a client; this is the real path — annotate
the interface and let the build generate one.

```java
@InvokeService
public interface GeneratedService {
    Greeting greet(GreetArgs args);
    void forget(GreetArgs args);
}

GeneratedServiceClient client =
        new GeneratedServiceClient("http://localhost:8080", http);
assertEquals(new GeneratedService.Greeting("hello, Ada"),
        client.greet(new GeneratedService.GreetArgs("Ada")));
```

Lesson: the annotation is codegen-only. The build emits
`GeneratedServiceClient` in the same package, implementing the interface
with identical signatures, and gives it constructors plus static
`create` factories taking a base URL and optionally an `InvokeHttp` for
tests. The registry and `InvokeClientFactory` behave identically with or
without the annotation: it buys you a named class whose signatures are
checked at compile time instead of a runtime proxy, and nothing more.
