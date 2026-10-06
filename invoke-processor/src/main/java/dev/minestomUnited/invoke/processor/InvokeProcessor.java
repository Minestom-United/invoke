package dev.minestomUnited.invoke.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.minestomUnited.invoke.InvokeService;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

/**
 * Java annotation processor that turns @InvokeService interfaces into typed HTTP clients. Each method keeps its exact
 * signature, so the generated client implements the service interface and callers never touch JSON. The CODEC check is
 * name-based (a static StructCodec field), which keeps this jar free of the codec dependency.
 *
 * <p>Two options steer the generated type, both supplied by the {@code dev.minestom-united.invoke} Gradle plugin:
 * {@code -Ainvoke.packageName} picks the package the clients land in, and {@code -Ainvoke.clientSuffix} renames the
 * generated type (default {@code Client}). Without {@code packageName}, clients land beside their service interface.
 * Because the generated source references the service and runtime types by fully qualified name, a client in a
 * different package needs no imports.
 */
@SupportedAnnotationTypes({
    InvokeProcessor.SERVICE_ANNOTATION,
    InvokeProcessor.RUNTIME_SERVICE_ANNOTATION
})
@SupportedOptions({"invoke.packageName", "invoke.clientSuffix"})
@SupportedSourceVersion(SourceVersion.RELEASE_25)
public class InvokeProcessor extends AbstractProcessor {

    static final String SERVICE_ANNOTATION = "dev.minestomUnited.invoke.InvokeService";
    static final String RUNTIME_SERVICE_ANNOTATION = "dev.minestomUnited.invoke.runtime.InvokeService";
    static final String STRUCT_CODEC = "net.minestom.server.codec.StructCodec";
    static final String INVOKE_HTTP = "dev.minestomUnited.invoke.runtime.InvokeHttp";
    static final String INVOKE_EXCEPTION = "dev.minestomUnited.invoke.runtime.InvokeException";
    static final String INVOKE_ENVELOPES = "dev.minestomUnited.invoke.runtime.InvokeEnvelopes";

    static final String OPTION_PACKAGE_NAME = "invoke.packageName";
    static final String OPTION_CLIENT_SUFFIX = "invoke.clientSuffix";
    static final String DEFAULT_CLIENT_SUFFIX = "Client";

    private String clientPackageName;
    private String clientSuffix;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.clientPackageName = processingEnv.getOptions().get(OPTION_PACKAGE_NAME);
        this.clientSuffix = processingEnv.getOptions().get(OPTION_CLIENT_SUFFIX);
        if (this.clientSuffix == null || this.clientSuffix.isBlank()) {
            this.clientSuffix = DEFAULT_CLIENT_SUFFIX;
        }
        if (this.clientPackageName != null && this.clientPackageName.isBlank()) {
            this.clientPackageName = null;
        }
    }

    /**
     * Scans each round for @InvokeService interfaces and emits one ServiceNameClient per valid interface. Invalid
     * services (overloads, wrong arity, non-record args, missing CODEC) report compile errors and produce no client, so
     * a broken service fails the build instead of limping along. Static, private, and default methods are skipped, not
     * validated.
     *
     * @param annotations the annotations present this round
     * @param roundEnv    access to the annotated elements
     * @return true, this processor claims the annotation
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Elements elements = processingEnv.getElementUtils();
        List<Element> services = new ArrayList<>(roundEnv.getElementsAnnotatedWith(InvokeService.class));
        TypeElement runtimeAnnotation = elements.getTypeElement(RUNTIME_SERVICE_ANNOTATION);
        if (runtimeAnnotation != null) {
            for (Element e : roundEnv.getElementsAnnotatedWith(runtimeAnnotation)) {
                if (!services.contains(e)) {
                    services.add(e);
                }
            }
        }
        for (Element service : services) {
            processService(service);
        }
        return true;
    }

    private void processService(Element service) {
        Messager messager = processingEnv.getMessager();
        if (service.getKind() != ElementKind.INTERFACE || !(service instanceof TypeElement serviceType)) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@InvokeService can only be applied to an interface, found " + service.getKind()
                    + " " + service.getSimpleName(), service);
            return;
        }
        String serviceName = serviceType.getQualifiedName().toString();

        List<ExecutableElement> methods = new ArrayList<>();
        for (Element enclosed : service.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.METHOD) {
                continue;
            }
            ExecutableElement method = (ExecutableElement) enclosed;
            Set<Modifier> modifiers = method.getModifiers();
            if (modifiers.contains(Modifier.STATIC) || modifiers.contains(Modifier.PRIVATE) || method.isDefault()) {
                continue;
            }
            methods.add(method);
        }

        boolean failed = false;
        Map<String, List<ExecutableElement>> byName = new LinkedHashMap<>();
        for (ExecutableElement method : methods) {
            byName.computeIfAbsent(method.getSimpleName().toString(), _ -> new ArrayList<>()).add(method);
        }
        for (Map.Entry<String, List<ExecutableElement>> entry : byName.entrySet()) {
            if (entry.getValue().size() > 1) {
                failed = true;
                for (ExecutableElement method : entry.getValue()) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                        "@InvokeService " + serviceName + ": overloaded method name '" + entry.getKey()
                            + "' (service methods must have unique names)", method);
                }
            }
        }

        List<MethodModel> models = new ArrayList<>();
        for (ExecutableElement method : methods) {
            MethodModel model = validateMethod(serviceName, method);
            if (model == null) {
                failed = true;
            } else if (byName.get(method.getSimpleName().toString()).size() == 1) {
                models.add(model);
            } else {
                failed = true;
            }
        }
        if (failed) {
            return;
        }
        try {
            generateClient(serviceType, models);
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                "@InvokeService " + serviceName + ": failed to generate client: " + e.getMessage(), service);
        }
    }

    private MethodModel validateMethod(String serviceName, ExecutableElement method) {
        Messager messager = processingEnv.getMessager();
        String label = "@InvokeService " + serviceName + "." + method.getSimpleName();
        List<? extends VariableElement> params = method.getParameters();
        if (params.size() != 1) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                label + ": service methods must take exactly 1 parameter, found " + params.size(), method);
            return null;
        }
        TypeMirror argType = params.getFirst().asType();
        String argName = params.getFirst().getSimpleName().toString();
        TypeElement argElement = asTypeElement(argType);
        if (argElement == null || argElement.getKind() != ElementKind.RECORD) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                label + ": parameter type " + argType + " is not a record"
                    + " (service args must be records with a static CODEC field)", method);
            return null;
        }
        if (!hasStructCodec(argElement)) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                label + ": record " + argElement.getQualifiedName()
                    + " declares no static CODEC field of type " + STRUCT_CODEC
                    + " (missing CODEC field)", method);
            return null;
        }
        TypeMirror returnType = method.getReturnType();
        boolean returnsVoid = returnType.getKind() == TypeKind.VOID;
        if (!returnsVoid) {
            TypeElement returnElement = asTypeElement(returnType);
            if (returnElement == null || returnElement.getKind() != ElementKind.RECORD) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    label + ": return type " + returnType + " is not void or a record"
                        + " (service results must be void or records with a static CODEC field)", method);
                return null;
            }
            if (!hasStructCodec(returnElement)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                    label + ": record " + returnElement.getQualifiedName()
                        + " declares no static CODEC field of type " + STRUCT_CODEC
                        + " (missing CODEC field)", method);
                return null;
            }
        }
        return new MethodModel(method.getSimpleName().toString(), argType.toString(), argName,
            returnType.toString(), returnsVoid);
    }

    private TypeElement asTypeElement(TypeMirror mirror) {
        Types types = processingEnv.getTypeUtils();
        try {
            Element element = types.asElement(mirror);
            return element instanceof TypeElement ? (TypeElement) element : null;
        } catch (IllegalArgumentException expected) {
            return null;
        }
    }

    private boolean hasStructCodec(TypeElement recordElement) {
        Elements elements = processingEnv.getElementUtils();
        Types types = processingEnv.getTypeUtils();
        TypeElement structCodec = elements.getTypeElement(STRUCT_CODEC);
        if (structCodec == null) {
            return false;
        }
        TypeMirror structErasure = types.erasure(structCodec.asType());
        for (Element enclosed : recordElement.getEnclosedElements()) {
            if (enclosed.getKind() != ElementKind.FIELD
                || !enclosed.getSimpleName().contentEquals("CODEC")
                || !enclosed.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            try {
                if (types.isSubtype(types.erasure(enclosed.asType()), structErasure)) {
                    return true;
                }
            } catch (IllegalArgumentException expected) {
                return false;
            }
        }
        return false;
    }

    private void generateClient(TypeElement serviceType, List<MethodModel> methods) throws IOException {
        Elements elements = processingEnv.getElementUtils();
        Filer filer = processingEnv.getFiler();
        String packageName = clientPackageName != null
            ? clientPackageName
            : elements.getPackageOf(serviceType).getQualifiedName().toString();
        String serviceSimpleName = serviceType.getSimpleName().toString();
        String clientName = serviceSimpleName + clientSuffix;
        String source = renderClient(packageName, serviceType.getQualifiedName().toString(), clientName, methods);
        JavaFileObject file = filer.createSourceFile(packageName + "." + clientName, serviceType);
        try (Writer writer = file.openWriter()) {
            writer.write(source);
        }
    }

    private String renderClient(String packageName, String serviceCanonical, String clientName,
        List<MethodModel> methods) {
        StringBuilder out = new StringBuilder();
        out.append("package ").append(packageName).append(";\n\n");
        out.append("public final class ").append(clientName).append(" implements ").append(serviceCanonical)
            .append(" {\n");
        out.append("    private final String baseUrl;\n");
        out.append("    private final ").append(INVOKE_HTTP).append(" http;\n\n");
        out.append("    public ").append(clientName).append("(String baseUrl) {\n");
        out.append("        this(baseUrl, new ").append(INVOKE_HTTP).append("());\n");
        out.append("    }\n\n");
        out.append("    public ").append(clientName).append("(String baseUrl, ").append(INVOKE_HTTP)
            .append(" http) {\n");
        out.append(
            "        this.baseUrl = stripTrailingSlash(java.util.Objects.requireNonNull(baseUrl, \"baseUrl\"));\n");
        out.append("        this.http = java.util.Objects.requireNonNull(http, \"http\");\n");
        out.append("    }\n\n");
        out.append("    public static ").append(clientName).append(" create(String baseUrl) {\n");
        out.append("        return new ").append(clientName).append("(baseUrl);\n");
        out.append("    }\n\n");
        out.append("    public static ").append(clientName).append(" create(String baseUrl, ").append(INVOKE_HTTP)
            .append(" http) {\n");
        out.append("        return new ").append(clientName).append("(baseUrl, http);\n");
        out.append("    }\n");
        for (MethodModel method : methods) {
            out.append("\n    @Override\n");
            out.append("    public ").append(method.returnType).append(" ").append(method.name)
                .append("(").append(method.argType).append(" ").append(method.argName).append(") {\n");
            out.append("        String path = \"/").append(serviceSimpleNameOf(serviceCanonical))
                .append("/").append(method.name).append("\";\n");
            out.append("        String requestBody = ").append(INVOKE_ENVELOPES).append(".encodeRecord(")
                .append(method.argType).append(".CODEC, ").append(method.argName).append(");\n");
            out.append("        ").append(INVOKE_HTTP)
                .append(".Response response = this.http.post(this.baseUrl + path, requestBody);\n");
            out.append("        ").append(INVOKE_ENVELOPES).append(".Envelope envelope = ")
                .append(INVOKE_ENVELOPES).append(".decode(response.body());\n");
            out.append("        if (envelope instanceof ").append(INVOKE_ENVELOPES)
                .append(".Envelope.Error error) {\n");
            out.append("            throw new ").append(INVOKE_EXCEPTION).append("(error.code(), error.message());\n");
            out.append("        }\n");
            if (method.returnsVoid) {
                out.append("        return;\n");
            } else {
                out.append("        String payload = ((").append(INVOKE_ENVELOPES)
                    .append(".Envelope.Result) envelope).payloadJson();\n");
                out.append("        if (payload == null || \"null\".equals(payload)) {\n");
                out.append("            throw new ").append(INVOKE_EXCEPTION)
                    .append("(500, \"invoke: null result for \" + path);\n");
                out.append("        }\n");
                out.append("        try {\n");
                out.append("            return ").append(INVOKE_ENVELOPES).append(".decodeRecord(")
                    .append(method.returnType).append(".CODEC, payload);\n");
                out.append("        } catch (").append(INVOKE_EXCEPTION).append(" e) {\n");
                out.append("            throw new ").append(INVOKE_EXCEPTION)
                    .append("(500, \"invoke: failed to decode result for \" + path);\n");
                out.append("        }\n");
            }
            out.append("    }\n");
        }
        out.append("\n    private static String stripTrailingSlash(String baseUrl) {\n");
        out.append("        return baseUrl.endsWith(\"/\") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;\n");
        out.append("    }\n");
        out.append("}\n");
        return out.toString();
    }

    private static String serviceSimpleNameOf(String serviceCanonical) {
        int dot = serviceCanonical.lastIndexOf('.');
        return dot < 0 ? serviceCanonical : serviceCanonical.substring(dot + 1);
    }

    private record MethodModel(String name, String argType, String argName, String returnType, boolean returnsVoid) {

    }
}
