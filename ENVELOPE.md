# Invoke — typed JSON RPC for the JVM (envelope spec v1)

Service = plain interface. Methods take exactly ONE argument (a Java record)
and return one value (record or `void`). No builders, no hand-built requests,
no `JsonObject` in user code.

## Wire

- Transport: HTTP/1.1 POST only.
- Path: `/{ServiceSimpleName}/{methodName}`, e.g. `/ReviewService/resolveReview`.
- Request headers: `Content-Type: application/json`. Optional `X-Invoke-Timeout-Ms`.
- Request body: the args object as a flat JSON object. NO wrapper:
  `{"reviewId":"rv_abc","personId":"p_80af"}`
- Success: HTTP 200, body `{"result":{...}}`. `void` methods return `{"result":null}`.
- Failure: HTTP status = mapped code, body `{"error":{"code":<int>,"message":"<text>"}}`.
  Code mapping: unknown method 404, bad args shape 400, impl throws
  `InvokeException(code, message)` → that code/message, any other impl
  exception → 500 `"internal error"` (message never leaks stack traces).
- Field naming: lowerCamel as written. The codec MUST emit identical bytes
  for identical values (see GOLDEN VECTORS below). No extra envelope fields.
- Null rule: absent key === explicit null, everywhere. The codec DROPS null
  fields on encode, so a nullable field never appears on the wire; on decode
  both a missing key and an explicit JSON null give null. Golden vector 3 is
  therefore a decode-equivalence vector, not a byte-identical one — decoding
  `{"maybe":null}` gives null, and re-encoding that null drops the key again.
- The `@InvokeService` trigger annotation lives in exactly one place:
  `dev.minestom-united.invoke.InvokeService` in invoke-runtime. No module may ship
  its own copy.
- Timeouts: client default 30s, overridable per call via header; server never
  holds a call past its own deadline — on timeout the client raises
  `InvokeException(504, ...)`.

## Codec

One codec, the Minestom United codec library (`StructCodec` on the record).
Records declare `public static final StructCodec<MyArgs> CODEC`. The generated
client encodes via that CODEC and decodes the result the same way, and the
registry decodes args through it, so they always agree by construction.

## GOLDEN VECTORS (byte contract — the codec MUST pass)

Canonical JSON (exact bytes, UTF-8, no whitespace):

1. `{"reviewId":"rv_abc","personId":"p_80af"}`
2. `{"name":"","samples":0,"confirmed":false}`
3. `{"items":["a","b"],"scores":[0.5,0.25],"maybe":null}`
4. Nested: `{"turn":{"speaker":"SPEAKER_01","start":1.5,"end":2.5},"tags":["x"]}`
5. Error: `{"error":{"code":404,"message":"review not found"}}`
6. Void result: `{"result":null}`

Decode each vector → re-encode → byte-identical output, except vector 3, which
is a decode-equivalence vector: its `maybe` key decodes to null and the
re-encode drops the key (see the null rule above). Error/result wrappers: same
rule for vectors 5–6.

## Client contract (generated)

- `static <S> S create(Class<S> service, String baseUrl)` (+ overload taking
  an `InvokeHttp` for tests). One method per interface method, same signature.
- Throws `InvokeException(code, message)` on error envelope / transport failure
  (transport failures map to 503). Never returns null for non-void.
- Thread-safe and sharable: the proxy holds no per-call state.

## Server contract (generated + runtime)

- `InvokeRegistry.register(Object impl)` reflects over implemented interfaces,
  exposes each method at its path. Registration rejects: overloaded method
  names, non-record args, 0 or 2+ params.
- Core registry has zero framework deps; a server maps POST path → registry
  dispatch and writes back the status and body (see `docs/server-guide.md`).

## Gradle plugin (`dev.minestom-united.invoke`)

- Applies the annotation processor, wires generated sources.
- Extension: `invoke { packageName = "..."; clientSuffix = "Client" }`.
- Fails the build on: overloaded service methods, non-record args, missing or
  wrongly-typed CODEC.

## Non-goals (v1)

Streaming calls, auth (callers add headers; issuance lives outside), IDL files,
multi-arg methods, ProtoBuf (JSON only until v2 proves otherwise).
