package dev.minestomUnited.invoke.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minestom.server.codec.Result;
import net.minestom.server.codec.StructCodec;
import net.minestom.server.codec.Transcoder;

/**
 * In-process service registry and dispatcher. register() reflects over the impl's interfaces and exposes each method at
 * ServiceSimpleName/methodName. dispatch() decodes the flat args object, invokes the impl, and wraps the outcome in a
 * result or error envelope. Registration is all-or-nothing: a bad method rejects the whole register() call and leaves
 * existing routes untouched.
 */
public final class InvokeRegistry {

    /**
     * Outcome of one dispatch: an HTTP-style status plus the full response body. Status mirrors the envelope inside, so
     * 200 always pairs with a result body and anything else pairs with an error body.
     *
     * @param status HTTP-style status code (200, 400, 404, or 500)
     * @param body   the complete JSON response body, never null
     */
    public record DispatchResult(int status, String body) {

        public DispatchResult {
            Objects.requireNonNull(body, "body");
        }
    }

    private record Entry(
        Object impl,
        Method method,
        StructCodec<Record> argCodec,
        StructCodec<Record> resultCodec,
        boolean returnsVoid) {

    }

    private final Map<String, Entry> routes = new ConcurrentHashMap<>();

    /**
     * Exposes every service method found on the impl's interfaces. Rejects the whole call with IllegalArgumentException
     * on overloaded names, zero or multiple parameters, non-record args, non-record non-void returns, or a missing
     * static CODEC field. Interfaces inherited through superclasses count.
     *
     * @param impl the service implementation, must implement at least one interface
     * @throws IllegalArgumentException if any method breaks the service contract
     */
    public void register(Object impl) {
        Objects.requireNonNull(impl, "impl");
        Set<Class<?>> interfaces = new HashSet<>();
        collectInterfaces(impl.getClass(), interfaces);
        if (interfaces.isEmpty()) {
            throw new IllegalArgumentException("impl implements no interfaces");
        }
        Map<String, Entry> pending = new HashMap<>();
        for (Class<?> service : interfaces) {
            Map<String, Method> byName = new HashMap<>();
            for (Method method : service.getMethods()) {
                if (method.getDeclaringClass() == Object.class) {
                    continue;
                }
                if (byName.containsKey(method.getName())) {
                    throw new IllegalArgumentException(
                        "overloaded method not allowed: " + service.getSimpleName() + "/" + method.getName());
                }
                byName.put(method.getName(), method);
            }
            for (Method method : byName.values()) {
                validateMethod(service, method);
                Entry entry = new Entry(
                    impl,
                    method,
                    argCodec(method.getParameterTypes()[0]),
                    resultCodec(method),
                    method.getReturnType() == void.class);
                String key = routeKey(service.getSimpleName(), method.getName());
                if (pending.containsKey(key)) {
                    throw new IllegalArgumentException("duplicate route: " + key);
                }
                pending.put(key, entry);
            }
        }
        routes.putAll(pending);
    }

    /**
     * Runs one call: unknown routes yield 404, undecodable args yield 400, an impl-thrown InvokeException keeps its
     * code, any other impl failure yields 500 with "internal error" (the real message never leaks). A null return from
     * a non-void method is also a 500. Never throws for bad input.
     *
     * @param service  the service simple name, as in the route path
     * @param method   the method name, as in the route path
     * @param bodyJson the flat args object, no envelope wrapper
     * @return status plus the full envelope body
     */
    public DispatchResult dispatch(String service, String method, String bodyJson) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(method, "method");
        Objects.requireNonNull(bodyJson, "bodyJson");
        Entry entry = routes.get(routeKey(service, method));
        if (entry == null) {
            return error(404, "unknown method: " + service + "/" + method);
        }
        Record args;
        try {
            JsonElement element = JsonParser.parseString(bodyJson);
            Result<Record> decoded = entry.argCodec().decode(Transcoder.JSON, element);
            if (decoded instanceof Result.Ok<Record>(Record value)) {
                args = value;
            } else {
                return error(400, "bad args shape");
            }
        } catch (Exception e) {
            return error(400, "bad args shape");
        }
        Object returned;
        try {
            returned = entry.method().invoke(entry.impl(), args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InvokeException invoke) {
                return error(invoke.code(), invoke.getMessage());
            }
            return error(500, "internal error");
        } catch (Exception e) {
            return error(500, "internal error");
        }
        if (entry.returnsVoid()) {
            return new DispatchResult(200, InvokeEnvelopes.encodeVoidResult());
        }
        if (returned == null) {
            return error(500, "internal error");
        }
        Result<JsonElement> encoded = entry.resultCodec().encode(Transcoder.JSON, (Record) returned);
        if (encoded instanceof Result.Ok<JsonElement>(JsonElement value)) {
            return new DispatchResult(200, InvokeEnvelopes.encodeResult(value.toString()));
        }
        return error(500, "internal error");
    }

    private static DispatchResult error(int code, String message) {
        return new DispatchResult(code, InvokeEnvelopes.encodeError(code, message));
    }

    private static String routeKey(String service, String method) {
        return service + "/" + method;
    }

    private static void validateMethod(Class<?> service, Method method) {
        String where = service.getSimpleName() + "/" + method.getName();
        if (method.getParameterCount() != 1) {
            throw new IllegalArgumentException(
                "method must take exactly one argument: " + where);
        }
        if (!method.getParameterTypes()[0].isRecord()) {
            throw new IllegalArgumentException(
                "method argument must be a record: " + where);
        }
        Class<?> returns = method.getReturnType();
        if (returns != void.class && !returns.isRecord()) {
            throw new IllegalArgumentException(
                "method return must be a record or void: " + where);
        }
    }

    @SuppressWarnings("unchecked")
    private static StructCodec<Record> argCodec(Class<?> argType) {
        return (StructCodec<Record>) loadCodec(argType, "argument");
    }

    private static StructCodec<Record> resultCodec(Method method) {
        if (method.getReturnType() == void.class) {
            return null;
        }
        @SuppressWarnings("unchecked")
        StructCodec<Record> codec = (StructCodec<Record>) loadCodec(method.getReturnType(), "return");
        return codec;
    }

    private static StructCodec<?> loadCodec(Class<?> type, String kind) {
        Field field;
        try {
            field = type.getDeclaredField("CODEC");
        } catch (NoSuchFieldException e) {
            throw new IllegalArgumentException(
                "record " + type.getSimpleName() + " is missing CODEC (" + kind + ")");
        }
        if (!Modifier.isStatic(field.getModifiers()) || !StructCodec.class.isAssignableFrom(field.getType())) {
            throw new IllegalArgumentException(
                "record " + type.getSimpleName() + " CODEC must be a static StructCodec (" + kind + ")");
        }
        field.setAccessible(true);
        try {
            Object codec = field.get(null);
            if (codec == null) {
                throw new IllegalArgumentException(
                    "record " + type.getSimpleName() + " CODEC is null (" + kind + ")");
            }
            return (StructCodec<?>) codec;
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(
                "record " + type.getSimpleName() + " CODEC is not accessible (" + kind + ")", e);
        }
    }

    private static void collectInterfaces(Class<?> type, Set<Class<?>> into) {
        if (type == null || type == Object.class) {
            return;
        }
        into.addAll(Arrays.asList(type.getInterfaces()));
        collectInterfaces(type.getSuperclass(), into);
        for (Class<?> candidate : type.getInterfaces()) {
            collectInterfaces(candidate, into);
        }
    }
}
