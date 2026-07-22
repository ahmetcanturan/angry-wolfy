package com.buddywolfy.angrywolfy.service;

import com.buddywolfy.angrywolfy.dto.ImportResult;
import com.buddywolfy.angrywolfy.enums.HttpMethod;
import com.buddywolfy.angrywolfy.enums.TargetType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imports an OpenAPI 3.x / Swagger 2.0 spec (JSON or YAML) into a project,
 * creating one {@code Target} per operation under {@code paths}. The spec's
 * server/host becomes part of the target path when it's concrete, so
 * {@code TargetService} lifts it into the target's base-URL override — the same
 * treatment an absolute Postman request URL gets.
 */
@Service
public class OpenApiImportService {

    private static final int MAX_NAME = 255;
    private static final int MAX_PATH = 500;
    private static final int MAX_DESC = 2000;

    /** Operation keys under a path item, per the spec. TRACE is rejected later (not a supported method). */
    private static final List<String> OPERATION_KEYS =
            List.of("get", "put", "post", "delete", "patch", "head", "options", "trace");

    private final ObjectMapper objectMapper;
    private final TargetService targetService;

    public OpenApiImportService(ObjectMapper objectMapper, TargetService targetService) {
        this.objectMapper = objectMapper;
        this.targetService = targetService;
    }

    /** Parses the spec and creates targets; all-or-nothing within one transaction. */
    @Transactional
    public ImportResult importSpec(Long projectId, String specContent) {
        JsonNode root = parse(specContent);
        JsonNode paths = root.path("paths");
        if (!paths.isObject()) {
            throw new IllegalArgumentException(
                    "This doesn't look like an OpenAPI/Swagger spec (no \"paths\" object). "
                            + "Provide an OpenAPI 3.x or Swagger 2.0 document as JSON or YAML.");
        }

        List<String> importedNames = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;
        int templated = 0;

        String base = baseUrlOf(root, warnings);
        for (Map.Entry<String, JsonNode> entry : paths.properties()) {
            String rawPath = entry.getKey();
            JsonNode pathItem = entry.getValue();
            if (rawPath.startsWith("x-") || !pathItem.isObject()) {
                skipped++;
                continue;
            }
            if (pathItem.has("$ref")) {
                warnings.add("\"" + rawPath + "\": referenced path item ($ref) wasn't imported.");
                skipped++;
                continue;
            }
            for (String key : OPERATION_KEYS) {
                JsonNode op = pathItem.path(key);
                if (!op.isObject()) {
                    continue;
                }
                if (key.equals("trace")) {
                    warnings.add("\"" + rawPath + "\": TRACE isn't supported — skipped.");
                    skipped++;
                    continue;
                }
                importOne(op, key, rawPath, base, projectId, importedNames, warnings);
                if (rawPath.contains("{")) {
                    templated++;
                }
            }
        }

        if (importedNames.isEmpty()) {
            throw new IllegalArgumentException("The spec has no operations to import.");
        }
        if (templated > 0) {
            warnings.add(templated + (templated == 1 ? " imported path contains" : " imported paths contain")
                    + " {placeholders} — replace them with real values before running.");
        }
        return new ImportResult(importedNames.size(), skipped, importedNames, warnings);
    }

    private JsonNode parse(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("No spec content was provided.");
        }
        try {
            if (content.strip().startsWith("{")) {
                return objectMapper.readTree(content);
            }
            // SafeConstructor: plain maps/lists/scalars only — never instantiates arbitrary types.
            Object yaml = new Yaml(new SafeConstructor(new LoaderOptions())).load(content);
            if (!(yaml instanceof Map)) {
                throw new IllegalArgumentException("the document isn't a YAML/JSON object.");
            }
            return objectMapper.valueToTree(yaml);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not read the spec: " + firstLine(e.getMessage()));
        }
    }

    private void importOne(JsonNode op, String methodKey, String rawPath, String base,
                           Long projectId, List<String> importedNames, List<String> warnings) {
        HttpMethod method = HttpMethod.valueOf(methodKey.toUpperCase(Locale.ROOT));
        String path = trim(joinBase(base, rawPath), MAX_PATH);

        String summary = op.path("summary").asString("").strip();
        String operationId = op.path("operationId").asString("").strip();
        String name = trim(!summary.isBlank() ? summary
                : (!operationId.isBlank() ? operationId : method.name() + " " + rawPath), MAX_NAME);
        String description = trim(blankToNull(op.path("description").asString("")), MAX_DESC);

        Map<String, String> headers = new LinkedHashMap<>();
        String body = extractBody(op, name, headers, warnings);

        targetService.create(name, description, projectId, path, method, TargetType.REST, headers, body, null);
        importedNames.add(name);
    }

    // ---- base URL (servers / host) ---------------------------------------

    /**
     * The URL prefix every path is joined to. OpenAPI 3: the first server's URL
     * (absolute → later lifted into the base-URL override; relative → kept as a
     * path prefix). Swagger 2: {@code schemes[0]://host + basePath}. A templated
     * server URL can't become a concrete host, so paths stay relative.
     */
    private String baseUrlOf(JsonNode root, List<String> warnings) {
        JsonNode servers = root.path("servers");
        if (servers.isArray() && servers.size() > 0) {
            String url = servers.get(0).path("url").asString("").strip();
            if (url.contains("{")) {
                warnings.add("Server URL \"" + url + "\" is templated — paths were imported relative; "
                        + "point an environment (or the targets' base-URL override) at the real host.");
                return "";
            }
            return stripTrailingSlashes(url);
        }

        String host = root.path("host").asString("").strip();
        String basePath = stripTrailingSlashes(root.path("basePath").asString("").strip());
        if (!host.isBlank()) {
            JsonNode schemes = root.path("schemes");
            String scheme = schemes.isArray() && schemes.size() > 0
                    ? schemes.get(0).asString("https") : "https";
            return scheme + "://" + host + basePath;
        }
        return basePath;
    }

    private String joinBase(String base, String path) {
        String p = path.startsWith("/") ? path : "/" + path;
        return base.isEmpty() ? p : base + p;
    }

    // ---- request body (example, if the spec carries one) -----------------

    /**
     * Pulls an example request body out of the operation: OpenAPI 3
     * {@code requestBody.content}, or a Swagger 2 {@code in: body} parameter.
     * When one is found, the media type also lands in {@code headers} as
     * Content-Type. Schema-only bodies aren't synthesized — just flagged.
     */
    private String extractBody(JsonNode op, String name, Map<String, String> headers, List<String> warnings) {
        JsonNode requestBody = op.path("requestBody");
        if (requestBody.isObject()) {
            if (requestBody.has("$ref")) {
                warnings.add("\"" + name + "\": request body is a $ref — add a body in Request config.");
                return null;
            }
            JsonNode content = requestBody.path("content");
            String mediaType = pickMediaType(content);
            if (mediaType == null) {
                return null;
            }
            String body = exampleOf(content.path(mediaType));
            if (body == null) {
                warnings.add("\"" + name + "\": has a request body but the spec carries no example — "
                        + "add one in Request config.");
                return null;
            }
            headers.put("Content-Type", mediaType);
            return body;
        }

        // Swagger 2: body lives in parameters[in=body].schema.
        JsonNode parameters = op.path("parameters");
        if (parameters.isArray()) {
            for (JsonNode param : parameters) {
                if (!"body".equals(param.path("in").asString(""))) {
                    continue;
                }
                String body = stringify(param.path("schema").path("example"));
                if (body == null) {
                    warnings.add("\"" + name + "\": has a request body but the spec carries no example — "
                            + "add one in Request config.");
                    return null;
                }
                JsonNode consumes = op.path("consumes");
                headers.put("Content-Type", consumes.isArray() && consumes.size() > 0
                        ? consumes.get(0).asString("application/json") : "application/json");
                return body;
            }
        }
        return null;
    }

    /** Prefer JSON; otherwise take the first media type the spec offers. */
    private String pickMediaType(JsonNode content) {
        if (!content.isObject() || content.isEmpty()) {
            return null;
        }
        if (content.has("application/json")) {
            return "application/json";
        }
        return content.properties().iterator().next().getKey();
    }

    /** Media-type example: {@code example}, first of {@code examples}, or {@code schema.example}. */
    private String exampleOf(JsonNode media) {
        String direct = stringify(media.path("example"));
        if (direct != null) {
            return direct;
        }
        JsonNode examples = media.path("examples");
        if (examples.isObject() && !examples.isEmpty()) {
            String named = stringify(examples.properties().iterator().next().getValue().path("value"));
            if (named != null) {
                return named;
            }
        }
        return stringify(media.path("schema").path("example"));
    }

    /** A scalar example is used verbatim; an object/array example is re-serialized as JSON. */
    private String stringify(JsonNode example) {
        if (example.isMissingNode() || example.isNull()) {
            return null;
        }
        if (example.isString()) {
            return blankToNull(example.asString(""));
        }
        try {
            return objectMapper.writeValueAsString(example);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- small helpers ----------------------------------------------------

    private static String stripTrailingSlashes(String s) {
        String out = s;
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Parser errors trail noisy locators — keep just the human part. */
    private static String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "invalid document.";
        }
        int at = message.indexOf(" at [Source");
        if (at < 0) {
            at = message.indexOf('\n');
        }
        return (at >= 0 ? message.substring(0, at) : message).strip();
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
