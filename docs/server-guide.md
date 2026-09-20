# Server guide

The server is two parts: `InvokeRegistry`, which owns every route and
has zero framework dependencies, and a thin adapter that maps HTTP
POSTs onto it. The adapter is yours to write; the snippet at the
bottom of this page is the shape, not shipped code.

## Register

```java
InvokeRegistry registry = new InvokeRegistry();
registry.register(new LookupService.Example(Map.of("a", "alpha")));
```

`register` reflects over the impl's interfaces, including ones
inherited through superclasses, and exposes each method at
`ServiceSimpleName/methodName`. Registration is all-or-nothing: a bad
method rejects the whole call with `IllegalArgumentException` and
leaves existing routes untouched. Interfaces that only extend
`Object` methods are skipped. Registering needs no annotation; the
`@InvokeService` trigger exists for codegen only.

## Paths

Every call is `POST /{ServiceSimpleName}/{methodName}` with the args
object as a flat JSON body, no wrapper:

```json
{"reviewId":"rv_abc","personId":"p_80af"}
```

Success is HTTP 200 with `{"result":{...}}`, or `{"result":null}`
for void methods. Failure sets the HTTP status to the error code
with `{"error":{"code":N,"message":"..."}}`. Field names stay
lowerCamel as written, and the codec must emit identical bytes for
identical values (golden vectors in `ENVELOPE.md`).

## Dispatch and validation

`dispatch(service, method, bodyJson)` never throws for bad input. It
returns a `DispatchResult(status, body)` where the status always
mirrors the envelope inside. In order:

1. Unknown route yields 404 `unknown method: Svc/m`.
2. Undecodable args yield 400 `bad args shape`. Missing required
   fields, wrong JSON types, and non-object bodies (`[1,2]`, `null`,
   `not json`) all land here. Explicit nulls for optional fields are
   fine — they decode like missing keys.
3. An impl-thrown `InvokeException` keeps its code and message.
4. Any other impl failure yields 500 `"internal error"`.
5. A null return from a non-void method yields 500. Void methods
   return `{"result":null}` without touching a result codec.
6. A result the codec cannot encode yields 500.

`DispatchResult` is a record with non-null body, safe to write
straight to the HTTP response.

## The 500 no-leak rule

Any impl crash becomes `{"error":{"code":500,"message":"internal
error"}}`. The real message never crosses the wire. Locked in by
test (`RegistryTest.unexpectedExceptionMapsTo500WithoutLeak`):

```java
@Override
public Fixtures.ReviewResult resolveReview(Fixtures.ReviewArgs args) {
    if (args.reviewId().equals("rv_missing")) {
        throw new InvokeException(404, "review not found");
    }
    if (args.reviewId().equals("rv_boom")) {
        throw new IllegalStateException("db connection pool exhausted");
    }
    return new Fixtures.ReviewResult("ok:" + args.personId());
}
```

Dispatching `rv_boom` answers 500 `"internal error"` with no trace
of the pool message. Log the cause server-side; the envelope carries
none of it. The same rule covers malformed envelopes on the client
side: a reply that is not an envelope throws `InvokeException(500,
"internal error")` or falls back to the raw status and body for
non-200 responses.

## Serving the registry

There is no server adapter module; the registry is transport-agnostic on
purpose. Serve it from whatever HTTP layer you already use with two moves:
pass the path segments and the raw body to `dispatch`, then write the status
and body back untouched.

```java
@Post("/" + service + "/" + method)
public Response invoke(@RequestBody String body) {
    DispatchResult r = registry.dispatch(service, method, body);
    return Response.status(r.status()).body(r.body());
}
```

That is the whole contract. `dispatch` never throws for bad input, so the
adapter has no error handling of its own to get wrong.
