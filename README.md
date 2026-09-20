# Invoke

Typed JSON RPC for the JVM. You write a plain interface, one argument in and
one value out, and invoke handles the HTTP, the JSON, and the error mapping.
No builders, no hand-built requests, no `JsonObject` in your code.

```java
@InvokeService
public interface ReviewService {
  record ResolveArgs(String reviewId, String personId) {
    public static final StructCodec<ResolveArgs> CODEC = StructCodec.struct(
      "reviewId", Codec.STRING, ResolveArgs::reviewId,
      "personId", Codec.STRING, ResolveArgs::personId, ResolveArgs::new);
  }
  record ResolveResult(String status) {
    public static final StructCodec<ResolveResult> CODEC = StructCodec.struct(
      "status", Codec.STRING, ResolveResult::status, ResolveResult::new);
  }
  ResolveResult resolveReview(ResolveArgs args);
}

InvokeRegistry registry = new InvokeRegistry();
registry.register(new ReviewServiceImpl());
ReviewService client =
  InvokeClientFactory.create(ReviewService.class, "http://localhost:8080");
ResolveResult out = client.resolveReview(new ResolveArgs("rv_abc", "p_80af"));
```

Annotate the interface, register an impl on the server, create a client. The
`dev.minestom-united.invoke` Gradle plugin goes one step further and generates a
`ReviewServiceClient` with identical signatures at compile time, so callers
never see the factory.

## The envelope

Every call is `POST /{ServiceSimpleName}/{methodName}` with the args object as
a flat JSON body, no wrapper. Success returns `{"result":{...}}` (or
`{"result":null}` for void methods). Failure returns
`{"error":{"code":N,"message":"..."}}` with the HTTP status set to the same
code: unknown method is 404, bad args shape is 400, an impl-thrown
`InvokeException` keeps its code, and any other impl crash becomes a 500 with
the message `"internal error"`. Field names stay lowerCamel as written. The
full contract, including the golden byte vectors the codec must match, lives
in [ENVELOPE.md](ENVELOPE.md).

## The null rule

Absent key and explicit null mean the same thing everywhere. In practice:

- Java records declare optional fields with `Codec.STRING.optional()` (or any
  `Codec.X.optional()`). Encoding a null drops the key; decoding a missing key
  or an explicit JSON null gives null.
- Since the encoder drops nulls, compliant traffic never carries explicit
  nulls anyway. `examples/` locks all of this in with passing tests.

## Modules

| Module | What it holds |
|---|---|
| `invoke-runtime` | Java core: `InvokeRegistry`, `InvokeClientFactory`, envelopes, transport, `InvokeException`, and the canonical `@InvokeService` |
| `invoke-processor` | Java annotation processor: generates `ServiceNameClient` for `@InvokeService` interfaces |
| `invoke-gradle-plugin` | The `dev.minestomUnited.invoke` plugin: wires the processor, forwards `packageName` and `clientSuffix` |
| `examples` | One service per file per shape (void, nested, lists/maps, nullable, numbers, errors, multi-method), each a passing test |

## Requirements

Java 25, no exceptions. The MU codec artifact ships JVM 25 class files,
so anything older fails before your code even loads. The build enforces
`release = 25`.

## Building and testing

```sh
./gradlew test          # full suite, every module
./gradlew :examples:test  # shapes and interop only
./gradlew build         # assemble plus all checks
```

## Docs

- [Getting started](docs/getting-started.md): install, first service in five minutes, running the examples.
- [Authoring services](docs/authoring-services.md): the one-argument rule, the CODEC field, void, nesting, collections, nullables, error codes, build-time rejections.
- [Client guide](docs/client-guide.md): factory versus generated clients, timeouts, error codes and retryability, sharing, testing without a server.
- [Server guide](docs/server-guide.md): registry, paths, validation, the 500 no-leak rule.
- [Errors](docs/errors.md): the code catalog, wire shapes, explicit nulls.
- [Cookbook](docs/cookbook.md): one section per service in `examples/`, with the code and the lesson.

## License

MIT, see [LICENSE](LICENSE).
