package dev.minestomUnited.invoke.runtime;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Objects;

import net.minestom.server.codec.StructCodec;

/**
 * Builds runtime clients for service interfaces. The returned proxy encodes the single record argument with its static
 * CODEC, POSTs to ServiceSimpleName/ methodName, and decodes the result the same way. Error envelopes surface as
 * InvokeException with the server code. Proxies are thread-safe and sharable.
 */
public final class InvokeClientFactory {

    private InvokeClientFactory() {
    }

    /**
     * Creates a client with the default transport (30-second timeout).
     *
     * @param service the service interface, methods take one record and return a record or void
     * @param baseUrl server root, a trailing slash is stripped
     * @param <S>     the service type
     * @return a thread-safe proxy implementing the service
     * @throws IllegalArgumentException if service is not an interface
     */
    public static <S> S create(Class<S> service, String baseUrl) {
        return create(service, baseUrl, new InvokeHttp());
    }

    /**
     * Creates a client with an explicit transport. Tests pass a stub here to capture request bytes or replay canned
     * envelopes without a server.
     *
     * @param service the service interface, methods take one record and return a record or void
     * @param baseUrl server root, a trailing slash is stripped
     * @param http    the transport used for every call
     * @param <S>     the service type
     * @return a thread-safe proxy implementing the service
     * @throws IllegalArgumentException if service is not an interface
     */
    public static <S> S create(Class<S> service, String baseUrl, InvokeHttp http) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(baseUrl, "baseUrl");
        Objects.requireNonNull(http, "http");
        if (!service.isInterface()) {
            throw new IllegalArgumentException("service must be an interface");
        }
        String root = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        Object[] self = new Object[1];
        Object proxy = Proxy.newProxyInstance(
            service.getClassLoader(),
            new Class<?>[]{service},
            (_, method, args) -> invoke(service, root, http, self[0], method, args));
        self[0] = proxy;
        return service.cast(proxy);
    }

    private static Object invoke(
        Class<?> service, String root, InvokeHttp http, Object self, Method method, Object[] args) {
        if (method.getDeclaringClass() == Object.class) {
            return switch (method.getName()) {
                case "toString" -> "InvokeClient(" + service.getSimpleName() + "@" + root + ")";
                case "hashCode" -> System.identityHashCode(self);
                case "equals" -> self == (args != null && args.length == 1 ? args[0] : null);
                default -> throw new InvokeException(500, "unsupported Object method: " + method.getName());
            };
        }
        if (args == null || args.length != 1 || !(args[0] instanceof Record arg)) {
            throw new InvokeException(400, "client methods take exactly one record argument");
        }
        StructCodec<?> argCodec = loadCodec(arg.getClass());
        String body = InvokeEnvelopes.encodeRecord(cast(argCodec), arg);
        String url = root + "/" + service.getSimpleName() + "/" + method.getName();
        InvokeHttp.Response response = http.post(url, body);
        InvokeEnvelopes.Envelope envelope = InvokeEnvelopes.decode(response.body());
        if (envelope instanceof InvokeEnvelopes.Envelope.Error(int code, String message)) {
            throw new InvokeException(code, message);
        }
        String payload = ((InvokeEnvelopes.Envelope.Result) envelope).payloadJson();
        if (method.getReturnType() == void.class) {
            return null;
        }
        if (!(method.getReturnType().isRecord())) {
            throw new InvokeException(500, "client methods must return a record or void");
        }
        if (payload.equals("null")) {
            throw new InvokeException(500, "null result for non-void method");
        }
        StructCodec<?> resultCodec = loadCodec(method.getReturnType());
        return InvokeEnvelopes.decodeRecord(cast(resultCodec), payload);
    }

    private static StructCodec<?> loadCodec(Class<?> type) {
        Field field;
        try {
            field = type.getDeclaredField("CODEC");
        } catch (NoSuchFieldException e) {
            throw new InvokeException(500, "record " + type.getSimpleName() + " is missing CODEC");
        }
        if (!Modifier.isStatic(field.getModifiers()) || !StructCodec.class.isAssignableFrom(field.getType())) {
            throw new InvokeException(500, "record " + type.getSimpleName() + " CODEC must be a static StructCodec");
        }
        field.setAccessible(true);
        try {
            Object codec = field.get(null);
            if (codec == null) {
                throw new InvokeException(500, "record " + type.getSimpleName() + " CODEC is null");
            }
            return (StructCodec<?>) codec;
        } catch (IllegalAccessException e) {
            throw new InvokeException(500, "record " + type.getSimpleName() + " CODEC is not accessible");
        }
    }

    @SuppressWarnings("unchecked")
    private static StructCodec<Record> cast(StructCodec<?> codec) {
        return (StructCodec<Record>) codec;
    }
}
