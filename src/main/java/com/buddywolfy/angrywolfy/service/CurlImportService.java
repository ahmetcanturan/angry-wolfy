package com.buddywolfy.angrywolfy.service;

import com.buddywolfy.angrywolfy.dto.ImportResult;
import com.buddywolfy.angrywolfy.enums.HttpMethod;
import com.buddywolfy.angrywolfy.enums.TargetType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Imports one or more pasted {@code curl} commands into a project, creating one
 * {@code Target} per command. Understands the bash-flavoured output of
 * "Copy as cURL" (quoting, {@code $'…'} strings, backslash line continuations)
 * plus the flags that matter for a load test: method, URL, headers, body, and
 * basic auth. An absolute URL's origin is lifted into the target's base-URL
 * override by {@code TargetService}, same as the other importers.
 */
@Service
public class CurlImportService {

    private static final int MAX_NAME = 255;
    private static final int MAX_PATH = 500;

    /** Short flags that take a value; {@code -XPOST}-style attachment is split off these. */
    private static final String ARG_SHORT = "XHdubAeowmxFT";

    /** Short flags that take no argument and can be ignored (also combined, e.g. {@code -sSL}). */
    private static final Set<Character> NO_ARG_SHORT = Set.of('s', 'S', 'v', 'k', 'L', 'i', 'f', 'g', '#', '4', '6');

    /** Long flags that take no argument and can be ignored. */
    private static final Set<String> NO_ARG_LONG = Set.of(
            "--silent", "--verbose", "--insecure", "--location", "--include", "--fail", "--globoff",
            "--compressed", "--no-progress-meter", "--show-error", "--http1.1", "--http2",
            "--http2-prior-knowledge", "--tlsv1.2", "--tlsv1.3", "--ssl-no-revoke");

    /** Long flags whose argument is irrelevant here — consumed and dropped. */
    private static final Set<String> IGNORED_ARG_LONG = Set.of(
            "--output", "--write-out", "--connect-timeout", "--max-time", "--retry", "--retry-delay",
            "--retry-max-time", "--cacert", "--capath", "--cert", "--key", "--ciphers", "--proxy",
            "--limit-rate", "--max-redirs", "--range", "--interface", "--resolve", "--unix-socket");

    private final TargetService targetService;

    public CurlImportService(TargetService targetService) {
        this.targetService = targetService;
    }

    /** Parses the command(s) and creates targets; all-or-nothing within one transaction. */
    @Transactional
    public ImportResult importCommands(Long projectId, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("No curl command was provided.");
        }
        List<List<String>> commands = splitCommands(tokenize(text));
        if (commands.isEmpty()) {
            throw new IllegalArgumentException(
                    "No curl command found — paste one or more commands starting with \"curl\".");
        }

        List<String> importedNames = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int skipped = 0;
        for (List<String> command : commands) {
            if (!importOne(command, projectId, importedNames, warnings)) {
                skipped++;
            }
        }
        if (importedNames.isEmpty()) {
            throw new IllegalArgumentException("None of the curl commands could be imported."
                    + (warnings.isEmpty() ? "" : " " + String.join(" ", warnings)));
        }
        return new ImportResult(importedNames.size(), skipped, importedNames, warnings);
    }

    // ---- one command → one target ----------------------------------------

    /** Parses a single tokenized command; returns false when it had no usable URL. */
    private boolean importOne(List<String> command, Long projectId,
                              List<String> importedNames, List<String> warnings) {
        Deque<String> q = new ArrayDeque<>(command.subList(1, command.size()));
        String url = null;
        String methodFlag = null;
        String user = null;
        boolean head = false;
        boolean get = false;
        boolean json = false;
        Map<String, String> headers = new LinkedHashMap<>();
        List<String> dataParts = new ArrayList<>();

        while (!q.isEmpty()) {
            String t = q.poll();
            String flag = t;
            String inline = null;
            if (t.startsWith("--")) {
                int eq = t.indexOf('=');
                if (eq > 0) {                                    // GNU-style --flag=value
                    flag = t.substring(0, eq);
                    inline = t.substring(eq + 1);
                }
            } else if (t.length() > 2 && t.charAt(0) == '-' && ARG_SHORT.indexOf(t.charAt(1)) >= 0) {
                flag = t.substring(0, 2);                        // attached value: -XPOST, -Hkey:v
                inline = t.substring(2);
            }

            switch (flag) {
                case "-X", "--request" -> methodFlag = value(inline, q, flag, warnings);
                case "-H", "--header" -> putHeader(headers, value(inline, q, flag, warnings));
                case "-d", "--data", "--data-raw", "--data-binary", "--data-ascii", "--data-urlencode" ->
                        addData(dataParts, value(inline, q, flag, warnings), warnings);
                case "--json" -> {
                    addData(dataParts, value(inline, q, flag, warnings), warnings);
                    json = true;
                }
                case "-u", "--user" -> user = value(inline, q, flag, warnings);
                case "-A", "--user-agent" -> headers.put("User-Agent", nullToEmpty(value(inline, q, flag, warnings)));
                case "-e", "--referer" -> headers.put("Referer", nullToEmpty(value(inline, q, flag, warnings)));
                case "-b", "--cookie" -> {
                    String cookie = value(inline, q, flag, warnings);
                    if (cookie != null && cookie.contains("=")) {
                        headers.put("Cookie", cookie);
                    } else {
                        warnings.add("Cookie file \"" + cookie + "\" wasn't imported — paste the cookie value instead.");
                    }
                }
                case "--url" -> url = value(inline, q, flag, warnings);
                case "-G", "--get" -> get = true;
                case "-I", "--head" -> head = true;
                case "-F", "--form", "--form-string" -> {
                    value(inline, q, flag, warnings);
                    warnings.add("multipart form-data (-F) isn't supported — add the body in Request config.");
                }
                case "-T", "--upload-file" -> {
                    value(inline, q, flag, warnings);
                    warnings.add("file upload (-T) wasn't imported.");
                }
                case "-o", "-w", "-m", "-x" -> value(inline, q, flag, warnings);
                default -> {
                    if (NO_ARG_LONG.contains(flag)) {
                        break;
                    }
                    if (IGNORED_ARG_LONG.contains(flag)) {
                        value(inline, q, flag, warnings);
                        break;
                    }
                    Cluster cluster = readCluster(t, q, warnings);
                    if (cluster != null) {
                        head |= cluster.head;
                        if (cluster.method != null) {
                            methodFlag = cluster.method;
                        }
                    } else if (t.startsWith("-") && t.length() > 1) {
                        warnings.add("Flag \"" + t + "\" was ignored.");
                    } else if (url == null) {
                        url = t;
                    } else {
                        warnings.add("Extra argument \"" + t + "\" was ignored.");
                    }
                }
            }
        }

        if (url == null || url.isBlank()) {
            warnings.add("A curl command without a URL was skipped.");
            return false;
        }

        String body = dataParts.isEmpty() ? null : String.join("&", dataParts);
        if (get && body != null) {
            url = url + (url.contains("?") ? "&" : "?") + body;
            body = null;
        }
        HttpMethod method = resolveMethod(methodFlag, head, body != null, url, warnings);
        if (body != null && !json && headerMissing(headers, "Content-Type")) {
            // What curl itself sends for -d when no Content-Type is given.
            headers.put("Content-Type", "application/x-www-form-urlencoded");
        }
        if (json) {
            if (headerMissing(headers, "Content-Type")) {
                headers.put("Content-Type", "application/json");
            }
            if (headerMissing(headers, "Accept")) {
                headers.put("Accept", "application/json");
            }
        }
        if (user != null && headerMissing(headers, "Authorization")) {
            headers.put("Authorization", "Basic "
                    + Base64.getEncoder().encodeToString(user.getBytes(StandardCharsets.UTF_8)));
        }

        String name = trim(method.name() + " " + TargetUrlSplitter.split(url, null).path(), MAX_NAME);
        targetService.create(name, null, projectId, trim(url, MAX_PATH), method, TargetType.REST,
                headers, body, null);
        importedNames.add(name);
        return true;
    }

    // ---- flag plumbing ----------------------------------------------------

    /** The flag's value: inline ({@code --flag=v} / {@code -Xv}) or the next token. */
    private String value(String inline, Deque<String> q, String flag, List<String> warnings) {
        if (inline != null) {
            return inline;
        }
        if (!q.isEmpty()) {
            return q.poll();
        }
        warnings.add("Flag \"" + flag + "\" is missing its value.");
        return null;
    }

    /** What a combined short-flag token ({@code -sSL}, {@code -sI}, {@code -sXPOST}) amounts to. */
    private record Cluster(boolean head, String method) {
    }

    /** Reads {@code t} as a cluster of no-arg short flags; null when it isn't one. */
    private Cluster readCluster(String t, Deque<String> q, List<String> warnings) {
        if (t.startsWith("--") || !t.startsWith("-") || t.length() < 2) {
            return null;
        }
        boolean head = false;
        for (int i = 1; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == 'I') {
                head = true;
            } else if (c == 'X') {
                // "-sXPOST" carries the method; "-sX" takes it from the next token.
                String method = i < t.length() - 1 ? t.substring(i + 1) : value(null, q, "-X", warnings);
                return new Cluster(head, method);
            } else if (!NO_ARG_SHORT.contains(c)) {
                return null;
            }
        }
        return new Cluster(head, null);
    }

    private void putHeader(Map<String, String> headers, String raw) {
        if (raw == null) {
            return;
        }
        int colon = raw.indexOf(':');
        if (colon <= 0) {
            return;
        }
        String key = raw.substring(0, colon).strip();
        if (!key.isBlank()) {
            headers.put(key, raw.substring(colon + 1).strip());
        }
    }

    private void addData(List<String> dataParts, String data, List<String> warnings) {
        if (data == null) {
            return;
        }
        if (data.startsWith("@")) {
            warnings.add("File body \"" + data + "\" wasn't imported — paste the content instead.");
            return;
        }
        dataParts.add(data);
    }

    private HttpMethod resolveMethod(String flag, boolean head, boolean hasBody, String url,
                                     List<String> warnings) {
        if (flag != null && !flag.isBlank()) {
            try {
                return HttpMethod.valueOf(flag.strip().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                warnings.add("\"" + url + "\": method " + flag + " isn't supported — imported as GET.");
                return HttpMethod.GET;
            }
        }
        if (head) {
            return HttpMethod.HEAD;
        }
        return hasBody ? HttpMethod.POST : HttpMethod.GET;
    }

    private boolean headerMissing(Map<String, String> headers, String name) {
        return headers.keySet().stream().noneMatch(k -> k.equalsIgnoreCase(name));
    }

    // ---- command splitting & shell-style tokenizing -----------------------

    /** Each sub-list starts at a {@code curl} token; anything before the first one is dropped. */
    private List<List<String>> splitCommands(List<String> tokens) {
        List<List<String>> commands = new ArrayList<>();
        List<String> current = null;
        for (String t : tokens) {
            if (t.equals("curl") || t.endsWith("/curl")) {
                current = new ArrayList<>();
                current.add("curl");
                commands.add(current);
            } else if (current != null) {
                current.add(t);
            }
        }
        return commands;
    }

    /**
     * Bash-flavoured word splitting: single/double/{@code $'…'} quotes, backslash
     * escapes, backslash-newline continuations. Unquoted {@code ; & |} end a
     * command just like whitespace does.
     */
    private static List<String> tokenize(String input) {
        String text = input.replace("\r\n", "\n");
        List<String> tokens = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean has = false;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '\\') {
                if (i + 1 < n && text.charAt(i + 1) == '\n') {
                    i += 2;                       // line continuation — vanishes entirely
                } else if (i + 1 < n) {
                    cur.append(text.charAt(i + 1));
                    has = true;
                    i += 2;
                } else {
                    i++;
                }
            } else if (c == '\'') {
                i = readSingle(text, i + 1, cur);
                has = true;
            } else if (c == '"') {
                i = readDouble(text, i + 1, cur);
                has = true;
            } else if (c == '$' && i + 1 < n && text.charAt(i + 1) == '\'') {
                i = readAnsi(text, i + 2, cur);
                has = true;
            } else if (Character.isWhitespace(c) || c == ';' || c == '&' || c == '|') {
                if (has) {
                    tokens.add(cur.toString());
                    cur.setLength(0);
                    has = false;
                }
                i++;
            } else {
                cur.append(c);
                has = true;
                i++;
            }
        }
        if (has) {
            tokens.add(cur.toString());
        }
        return tokens;
    }

    /** '…' — literal until the closing quote; no escapes exist inside. */
    private static int readSingle(String s, int i, StringBuilder out) {
        while (i < s.length() && s.charAt(i) != '\'') {
            out.append(s.charAt(i++));
        }
        return Math.min(i + 1, s.length());
    }

    /** "…" — backslash only escapes the characters bash lets it escape. */
    private static int readDouble(String s, int i, StringBuilder out) {
        while (i < s.length() && s.charAt(i) != '"') {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char nxt = s.charAt(i + 1);
                if (nxt == '"' || nxt == '\\' || nxt == '$' || nxt == '`') {
                    out.append(nxt);
                    i += 2;
                    continue;
                }
            }
            out.append(c);
            i++;
        }
        return Math.min(i + 1, s.length());
    }

    /** $'…' — ANSI-C quoting (what Chrome's "Copy as cURL" emits for bodies with newlines). */
    private static int readAnsi(String s, int i, StringBuilder out) {
        while (i < s.length() && s.charAt(i) != '\'') {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char nxt = s.charAt(i + 1);
                switch (nxt) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case '\'' -> out.append('\'');
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    default -> out.append(c).append(nxt);
                }
                i += 2;
                continue;
            }
            out.append(c);
            i++;
        }
        return Math.min(i + 1, s.length());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String trim(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
