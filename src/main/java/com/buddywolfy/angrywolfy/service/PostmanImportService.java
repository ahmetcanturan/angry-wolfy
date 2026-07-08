package com.buddywolfy.angrywolfy.service;

import com.buddywolfy.angrywolfy.dto.ImportResult;
import com.buddywolfy.angrywolfy.enums.HttpMethod;
import com.buddywolfy.angrywolfy.enums.TargetType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Imports a Postman Collection (v2.x) into a project, creating one {@code Target}
 * per request. Folders are walked recursively; the request's URL path (not its
 * host) becomes the target path, since the host comes from the environment the
 * target is later run against.
 */
@Service
public class PostmanImportService {

    private static final int MAX_NAME = 255;
    private static final int MAX_PATH = 500;
    private static final int MAX_DESC = 2000;

    private final ObjectMapper objectMapper;
    private final TargetService targetService;

    public PostmanImportService(ObjectMapper objectMapper, TargetService targetService) {
        this.objectMapper = objectMapper;
        this.targetService = targetService;
    }

    /** Parses the collection and creates targets; all-or-nothing within one transaction. */
    @Transactional
    public ImportResult importCollection(Long projectId, String collectionJson) {
        JsonNode root = parse(collectionJson);
        JsonNode items = root.path("item");
        if (!items.isArray()) {
            throw new IllegalArgumentException(
                    "This doesn't look like a Postman collection (no \"item\" array). "
                            + "Export it as Collection v2.1 from Postman and try again.");
        }

        List<String> importedNames = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int[] skipped = {0};
        walk(items, projectId, importedNames, warnings, skipped);

        if (importedNames.isEmpty()) {
            throw new IllegalArgumentException("The collection has no requests to import.");
        }
        return new ImportResult(importedNames.size(), skipped[0], importedNames, warnings);
    }

    private JsonNode parse(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("No collection content was provided.");
        }
        try {
            return objectMapper.readTree(json);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not read the collection as JSON: " + firstLine(e.getMessage()));
        }
    }

    /** Recurse folders (nested "item"); turn leaves (with "request") into targets. */
    private void walk(JsonNode items, Long projectId,
                      List<String> importedNames, List<String> warnings, int[] skipped) {
        for (JsonNode item : items) {
            JsonNode nested = item.path("item");
            if (nested.isArray()) {
                walk(nested, projectId, importedNames, warnings, skipped);
            } else if (item.has("request")) {
                importOne(item, projectId, importedNames, warnings);
            } else {
                skipped[0]++;
            }
        }
    }

    private void importOne(JsonNode item, Long projectId,
                           List<String> importedNames, List<String> warnings) {
        JsonNode request = item.path("request");
        String rawName = item.path("name").asString("").strip();
        HttpMethod method = methodOf(request.path("method").asString(""),
                rawName.isBlank() ? "(unnamed request)" : rawName, warnings);
        String path = trim(extractPath(request.path("url")), MAX_PATH);
        String name = trim(rawName.isBlank() ? method.name() + " " + path : rawName, MAX_NAME);

        Map<String, String> headers = extractHeaders(request.path("header"));
        Body body = extractBody(request.path("body"), name, warnings);
        TargetType type = body.graphql ? TargetType.GRAPHQL : TargetType.REST;
        String description = trim(descriptionOf(request.path("description")), MAX_DESC);

        targetService.create(name, description, projectId, path, method, type, headers, body.content, null);
        importedNames.add(name);
    }

    // ---- URL → path -----------------------------------------------------

    /** The path (+ query) portion only; the host is supplied by the environment. */
    private String extractPath(JsonNode url) {
        if (url == null || url.isMissingNode() || url.isNull()) {
            return "/";
        }
        if (url.isString()) {
            return pathFromRaw(url.asString());
        }
        if (url.isObject()) {
            JsonNode segments = url.path("path");
            if (segments.isArray() && segments.size() > 0) {
                List<String> parts = new ArrayList<>();
                for (JsonNode seg : segments) {
                    String s = seg.isString() ? seg.asString() : seg.path("value").asString("");
                    if (!s.isBlank()) {
                        parts.add(s);
                    }
                }
                String p = "/" + String.join("/", parts);
                String q = queryString(url.path("query"));
                return q.isEmpty() ? p : p + "?" + q;
            }
            if (url.path("raw").isString()) {
                return pathFromRaw(url.path("raw").asString());
            }
        }
        return "/";
    }

    /** Strip protocol/host and any leading {@code {{var}}} base, keeping path + query. */
    private String pathFromRaw(String raw) {
        if (raw == null || raw.isBlank()) {
            return "/";
        }
        String s = raw.strip();
        if (s.startsWith("{{")) {
            int end = s.indexOf("}}");
            if (end >= 0) {
                s = s.substring(end + 2);
            }
        }
        int scheme = s.indexOf("://");
        if (scheme >= 0) {
            int slash = s.indexOf('/', scheme + 3);
            s = slash >= 0 ? s.substring(slash) : "/";
        }
        if (s.isBlank()) {
            return "/";
        }
        return s.startsWith("/") ? s : "/" + s;
    }

    private String queryString(JsonNode query) {
        if (!query.isArray()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode q : query) {
            if (q.path("disabled").asBoolean(false)) {
                continue;
            }
            String key = q.path("key").asString("");
            if (key.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(key).append('=').append(q.path("value").asString(""));
        }
        return sb.toString();
    }

    // ---- headers / body / method ---------------------------------------

    private Map<String, String> extractHeaders(JsonNode headers) {
        Map<String, String> map = new LinkedHashMap<>();
        if (headers.isArray()) {
            for (JsonNode h : headers) {
                if (h.path("disabled").asBoolean(false)) {
                    continue;
                }
                String key = h.path("key").asString("").strip();
                if (!key.isBlank()) {
                    map.put(key, h.path("value").asString(""));
                }
            }
        }
        return map;
    }

    /** A body value plus whether it looked like GraphQL (so we can set the target type). */
    private record Body(String content, boolean graphql) {
        static Body none() {
            return new Body(null, false);
        }
    }

    private Body extractBody(JsonNode body, String name, List<String> warnings) {
        String mode = body.path("mode").asString("");
        switch (mode) {
            case "raw":
                return new Body(blankToNull(body.path("raw").asString("")), false);
            case "graphql":
                return new Body(graphqlBody(body.path("graphql")), true);
            case "urlencoded":
                return new Body(urlencodedBody(body.path("urlencoded")), false);
            case "formdata":
                warnings.add("\"" + name + "\": form-data body wasn't imported (not supported yet).");
                return Body.none();
            case "file":
                warnings.add("\"" + name + "\": file body wasn't imported.");
                return Body.none();
            default:
                return Body.none();
        }
    }

    private String graphqlBody(JsonNode graphql) {
        if (!graphql.isObject()) {
            return null;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("query", graphql.path("query").asString(""));
        String variables = graphql.path("variables").asString("");
        if (!variables.isBlank()) {
            try {
                payload.put("variables", objectMapper.readTree(variables));
            } catch (RuntimeException e) {
                payload.put("variables", variables);
            }
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (RuntimeException e) {
            return graphql.path("query").asString(null);
        }
    }

    private String urlencodedBody(JsonNode fields) {
        if (!fields.isArray()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode f : fields) {
            if (f.path("disabled").asBoolean(false)) {
                continue;
            }
            String key = f.path("key").asString("");
            if (key.isBlank()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(key).append('=').append(f.path("value").asString(""));
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private HttpMethod methodOf(String method, String name, List<String> warnings) {
        if (method == null || method.isBlank()) {
            return HttpMethod.GET;
        }
        try {
            return HttpMethod.valueOf(method.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            warnings.add("\"" + name + "\": method " + method + " isn't supported — imported as GET.");
            return HttpMethod.GET;
        }
    }

    /** Postman description is a string or a {@code {content, type}} object. */
    private String descriptionOf(JsonNode description) {
        if (description.isString()) {
            return blankToNull(description.asString(""));
        }
        if (description.isObject()) {
            return blankToNull(description.path("content").asString(""));
        }
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Jackson parse errors trail a noisy "at [Source: …]" locator — keep just the human part. */
    private static String firstLine(String message) {
        if (message == null || message.isBlank()) {
            return "invalid JSON.";
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
