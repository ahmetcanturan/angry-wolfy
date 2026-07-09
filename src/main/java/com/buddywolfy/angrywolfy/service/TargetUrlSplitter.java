package com.buddywolfy.angrywolfy.service;

/**
 * Accepts a target "path" that may actually be a full URL and splits it into a
 * base URL (scheme + host + port) and the remaining path (+ query).
 *
 * <p>Targets normally store only a path, with the host coming from the
 * environment they run against. But paths often arrive with a domain attached —
 * pasted into the New-target form, or imported from a collection where the
 * request URL is absolute. Rather than reject or silently mangle those, we
 * accept them: the domain becomes the target's {@code baseUrlOverride} and the
 * rest becomes its {@code path}.
 *
 * <p>A relative path ({@code /v1/foo}) is left untouched with no override. A
 * leading Postman-style {@code {{var}}} base is treated as a variable host and
 * stripped to a relative path (we can't turn a variable into a concrete host).
 */
public final class TargetUrlSplitter {

    /** A base URL (may be null) and the path portion (never null, always starts with "/"). */
    public record Split(String baseUrl, String path) {
    }

    private TargetUrlSplitter() {
    }

    /**
     * Splits {@code rawPath} into (baseUrl, path). If {@code explicitOverride} is
     * already set it wins and the raw value is treated as a plain path; otherwise
     * an absolute URL's origin is lifted out into the base.
     */
    public static Split split(String rawPath, String explicitOverride) {
        String path = rawPath == null ? "" : rawPath.strip();

        // A caller-supplied override always wins; don't second-guess it.
        if (explicitOverride != null && !explicitOverride.isBlank()) {
            return new Split(explicitOverride.strip(), ensureLeadingSlash(stripOrigin(path).path()));
        }

        // A {{var}} base can't become a concrete host — drop it to a relative path.
        if (path.startsWith("{{")) {
            int end = path.indexOf("}}");
            if (end >= 0) {
                path = path.substring(end + 2);
            }
            return new Split(null, ensureLeadingSlash(path));
        }

        Split origin = stripOrigin(path);
        return new Split(origin.baseUrl(), ensureLeadingSlash(origin.path()));
    }

    /**
     * Pulls {@code scheme://host[:port]} off the front, returning it as the base
     * and the remainder as the path. Returns a null base for a relative input.
     */
    private static Split stripOrigin(String s) {
        int scheme = s.indexOf("://");
        if (scheme < 0) {
            return new Split(null, s);
        }
        int slash = s.indexOf('/', scheme + 3);
        int query = s.indexOf('?', scheme + 3);
        // The origin ends at the first '/' or '?' after the host, whichever comes first.
        int cut = firstNonNegative(slash, query);
        if (cut < 0) {
            // Whole thing is just an origin, e.g. "https://api.example.com".
            return new Split(stripTrailingSlashes(s), "/");
        }
        String base = stripTrailingSlashes(s.substring(0, cut));
        String rest = s.substring(cut);
        return new Split(base, rest);
    }

    private static int firstNonNegative(int a, int b) {
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }

    private static String stripTrailingSlashes(String s) {
        String out = s;
        while (out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static String ensureLeadingSlash(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }
}
