# Errors

Every failure on the wire is `{"error":{"code":N,"message":"..."}}`
with the HTTP status set to the same code. Every failure in code is
`InvokeException(code, message)`. Switch on the code, never on the
message text.

## Catalog

| Code | Meaning | When it fires | Retryable? |
|---|---|---|---|
| 400 | Bad args shape | Body is not JSON, not an object, or misses required fields; wrong JSON types | No, the caller is wrong |
| 404 | Unknown method | `dispatch` names a service/method pair with no route | No |
| 404 | Missing item (impl) | Impl throws `InvokeException(404, ...)`, e.g. `item not found: missing` | No |
| 422 | State conflict (impl) | Impl throws `InvokeException(422, ...)`, e.g. `bad state`; passes through untouched | Sometimes, after the state changes |
| 500 | Internal error | Impl threw anything else; message becomes `"internal error"`, the real text never leaks | No |
| 500 | Null result | Non-void impl returned null, or a 200 carried `{"result":null}` for a non-void call | No |
| 500 | Malformed envelope | Reply has neither `result` nor `error`, or the codec cannot encode the result | No |
| 503 | Transport failure | Refused connection, I/O failure, interrupt (thread flag re-set first) | Yes, with backoff |
| 504 | Timeout | Call passed its deadline (default 30s, per-call override via header) | Yes, but the call may have run server-side |

Impl-chosen codes pass through untouched: whatever code the impl
puts in `InvokeException` is the code the client sees. The registry
only invents 404, 400, and 500.

## Shapes on the wire

```json
{"error":{"code":404,"message":"review not found"}}
```

```json
{"error":{"code":500,"message":"internal error"}}
```

A non-200 reply that is not an envelope at all falls back to the raw
status and body (`500` plus `"oops"` in the test). A 200 that is not
JSON, or whose payload will not decode into the result type, throws
500 `"internal error"`.

## Explicit nulls are not a 400

Both of these decode the same — missing key reads as null, and so does
explicit null:

```json
{"id":"x"}
```

```json
{"id":"x","maybe":null}
```

Locked in by `NullableServiceTest.explicitNullDecodesLikeAbsentKey`. The
encoder drops nulls, so compliant traffic never carries an explicit null in
the first place.
