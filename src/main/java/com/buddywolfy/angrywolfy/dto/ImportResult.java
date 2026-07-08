package com.buddywolfy.angrywolfy.dto;

import java.util.List;

/**
 * Outcome of importing an external API definition (e.g. a Postman collection)
 * into a project as targets.
 *
 * @param imported     how many targets were created
 * @param skipped      how many collection entries were skipped (no request, etc.)
 * @param importedNames the names of the created targets, in order
 * @param warnings     human-readable notes about anything imperfect in the import
 */
public record ImportResult(
        int imported,
        int skipped,
        List<String> importedNames,
        List<String> warnings) {
}
