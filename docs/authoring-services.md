# Authoring services

invoke is a Java library, so a service is a plain Java interface: one
argument in, one value out. The argument is a Java record, the return
is a record or `void`.

```java
ItemResult lookup(KeyArgs args);
```

One rule shapes everything — every method takes exactly one argument
and returns exactly one value. No overloads, no second parameter, no
in-band context.

## The CODEC field

Every record used as an argument or a result carries its own codec, a
`public static final StructCodec` field named `CODEC`:

```java
record KeyArgs(String id) {
    public static final StructCodec<KeyArgs> CODEC = StructCodec.struct(
            "id", Codec.STRING, KeyArgs::id,
            KeyArgs::new);
}
```

The field name and type are fixed: `CODEC`, static, a `StructCodec` of
the record itself. The generated client encodes through that CODEC and
the registry decodes through it, so they always agree.

Miss the field, or give it the wrong type, and registration fails with
`record <Name> is missing CODEC` (or `record <Name> CODEC must be a
static StructCodec`). The annotation processor fails the build the
same way at compile time with `declares no static CODEC field`.

## Collections

Collections nest by composing codecs, and the collection helpers come
from `Codec`:

```java
record BagArgs(List<String> items, List<Double> scores, Map<String, Integer> counts) {
    public static final StructCodec<BagArgs> CODEC = StructCodec.struct(
            "items", Codec.STRING.list(), BagArgs::items,
            "scores", Codec.DOUBLE.list(), BagArgs::scores,
            "counts", Codec.STRING.mapValue(Codec.INT), BagArgs::counts,
            BagArgs::new);
}
```

That is `CollectionService` in `examples/`.

## Nesting

A nested record is just another field, and it reuses its own CODEC
rather than being spelled out twice:

```java
record CallArgs(Turn turn, List<String> tags) {
    public static final StructCodec<CallArgs> CODEC = StructCodec.struct(
            "turn", Turn.CODEC, CallArgs::turn,
            "tags", Codec.STRING.list(), CallArgs::tags,
            CallArgs::new);
}
```

That is `NestedService` in `examples/`, where `Turn` declares its own
`CODEC` next to it.

## Numbers

Scalar fields use `Codec.INT`, `Codec.LONG`, `Codec.DOUBLE`, and
`Codec.BOOLEAN`:

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

That is `NumbersService` in `examples/`.

## Void

A void method answers `{"result":null}` and nothing else:

```java
void ping(PingArgs args);
```

That is `VoidService` in `examples/`.

A null return from a non-void method is a 500 on the server, and the
client throws 500 on a null payload for a non-void call. Never return
null to mean empty; throw or return a value.

## Nullables and the null rule

A field is optional when its codec says so, with `.optional()`:

```java
record NoteArgs(String id, String maybe) {
    public static final StructCodec<NoteArgs> CODEC = StructCodec.struct(
            "id", Codec.STRING, NoteArgs::id,
            "maybe", Codec.STRING.optional(), NoteArgs::maybe,
            NoteArgs::new);
}
```

That is `NullableService` in `examples/`.

The rule is three parts: encoding a null drops the key, so `maybe`
null encodes as `{"id":"x"}`; decoding a missing key gives null; and
decoding an explicit `"maybe":null` gives null too. A missing key and
an explicit null mean the same thing, so both shapes read the same
the same way, and compliant traffic never carries explicit nulls on the
wire in the first place. Full detail in [errors.md](errors.md).

## Errors from the impl

Throw `InvokeException(code, message)` to set the error code.
Anything else the impl throws becomes a 500 `"internal error"` and
the message never crosses the wire. From `examples/`:

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

Use codes the way HTTP does: 404 for a missing item, 400 for a bad
value the shape check cannot see, 422 for a state conflict. The
catalog is in [errors.md](errors.md).

## What registration and the build reject

`InvokeRegistry.register` is all-or-nothing: one bad method rejects
the whole call and leaves existing routes untouched. It rejects
overloaded method names, zero or two-plus parameters, non-record
args, non-record non-void returns, and a missing or wrongly-typed
`CODEC`. The messages name the route, for example `overloaded method
not allowed: Svc/go` and `method must take exactly one argument:
Svc/go`.

The Gradle plugin (`dev.minestom-united.invoke`, `invoke { packageName = ... }`)
moves the same checks to compile time. The Java annotation processor
reports `service methods must take exactly 1 parameter`, `is not a
record`, `declares no static CODEC field`, and `is not void or a
record`. The plugin also fails the build when `packageName` is blank.

A rejected service produces no client at all, so a broken interface
fails the build instead of limping along at runtime.
