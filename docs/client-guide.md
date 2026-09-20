# Client guide

Two ways to call: the runtime factory, which builds a proxy over your
interface today, and the generated client, which the Gradle plugin
emits at compile time. Same signatures, same envelope, same errors.

## Factory (Java)

```java
LookupService client =
        InvokeClientFactory.create(LookupService.class, "http://localhost:8080");
LookupService.ItemResult out =
        client.lookup(new LookupService.KeyArgs("a"));
```

`create` takes the service interface and the server root. A trailing
slash on the root is stripped, so both forms give
`http://localhost:8080/LookupService/lookup`. The proxy encodes the
single record argument with its static `CODEC`, POSTs the flat JSON
body, and decodes the result the same way. Methods on the interface
keep their exact shape, including `void`:

```java
client.save(new LookupService.KeyArgs("a"));
```

The three-arg overload takes an `InvokeHttp` transport. Tests pass a
stub there to capture request bytes or replay canned envelopes
without a server. The proxy handles `toString`, `hashCode`, and
`equals` itself and rejects anything else declared on `Object`.

Both arities validate the interface: `create` throws
`IllegalArgumentException` when `service` is a class, and null
arguments fail fast.

## Generated clients

Apply the plugin (id `dev.minestom-united.invoke`) and set the one required
option, `invoke { packageName = "com.example.shop" }`, optionally with
`clientSuffix = "Client"`. The plugin wires up the annotation processor
and forwards both as `-Ainvoke.packageName` and `-Ainvoke.clientSuffix`
compiler args.

Codegen emits `<ServiceName>Client` into that package with one method
per interface method and identical signatures, so it implements the
service interface:

```java
LookupServiceClient client = LookupServiceClient.create("http://localhost:8080");
LookupService.ItemResult out = client.lookup(new LookupService.KeyArgs("a"));
```

`create(baseUrl)` uses the default transport; `create(baseUrl, http)`
takes your own `InvokeHttp`. Constructors take the same two shapes.
`clientSuffix` defaults to `"Client"` and only needs setting on a clash.

A service interface needs `@InvokeService` for codegen to pick it up;
the factory and the registry work without it. The processor validates
what it emits: one parameter, a record arg with a static `CODEC`, a
`void` or record return. Overloads or violations report a compile error
and emit no client, so a broken service fails the build instead of
limping along.

## Timeouts

The default deadline is 30 seconds (`Duration.ofSeconds(30)` in
`InvokeHttp`, used by both `create` overloads and by every generated
`create`). Pass your own deadline at the transport level:

```java
InvokeHttp http = new InvokeHttp(HttpClient.newHttpClient(), Duration.ofSeconds(5));
LookupService client = InvokeClientFactory.create(LookupService.class, "http://localhost:8080", http);
```

Every call sends its deadline as the `X-Invoke-Timeout-Ms` header next
to the JDK request timeout. On timeout the client raises
`InvokeException(504, ...)`. Transport failures (refused connection,
interrupts) raise 503; an interrupt re-sets the thread flag before
throwing.

## Errors and retryability

Every failure arrives as `InvokeException(code, message)`. Switch on
`code()`, never on message text:

```java
try {
    client.lookup(new LookupService.KeyArgs("missing"));
} catch (InvokeException e) {
    if (e.code() == 404) { /* no point retrying */ }
}
```

| Code | Retry? |
|---|---|
| 400 bad args shape | No, the caller is wrong |
| 404 unknown method / missing item | No |
| 422 state conflict from the impl | Sometimes, after the state changes |
| 500 internal error / malformed envelope | No, unless you know the server was fixed |
| 503 transport failure | Yes, with backoff |
| 504 timeout | Yes, but the call may have run server-side |

The full catalog, with when-each-fires, is in [errors.md](errors.md).

## Sharing and thread-safety

The proxy is thread-safe and sharable: it holds no per-call state, only
the service type, the stripped root, and the transport. Create one per
service root and pass it around. Generated clients are the same shape —
a `final` class of final fields. The JDK `HttpClient` inside
`InvokeHttp` owns and pools its connections, so there is nothing to
close and nothing to guard.

## Testing without a server

Subclass the transport and route straight into the registry.
`LoopbackHttp` in `examples/` is the pattern to steal:

```java
final class LoopbackHttp extends InvokeHttp {
    private final InvokeRegistry registry;
    String lastUrl;
    String lastBody;

    LoopbackHttp(InvokeRegistry registry) {
        super(HttpClient.newHttpClient(), Duration.ofSeconds(5));
        this.registry = registry;
    }

    @Override
    public Response post(String url, String jsonBody) {
        lastUrl = url;
        lastBody = jsonBody;
        int slash = url.lastIndexOf('/');
        int prev = url.lastIndexOf('/', slash - 1);
        String service = url.substring(prev + 1, slash);
        String method = url.substring(slash + 1);
        InvokeRegistry.DispatchResult out = registry.dispatch(service, method, jsonBody);
        return new Response(out.status(), out.body());
    }
}
```

Use it like this (`VoidServiceTest` in `examples/`):

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
```

Keep a list of requests when one test makes several calls, since
`lastUrl` and `lastBody` only hold the most recent one. To exercise
error paths, return a status and body straight from `post` instead of
dispatching — `new Response(404, "{\"error\":{\"code\":404,\"message\":\"missing\"}}")`
— and assert on the `InvokeException` code.