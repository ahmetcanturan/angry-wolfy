package com.buddywolfy.angrywolfy.web.view;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Sends stray page navigations — a typo'd or stale URL that maps to nothing — to
 * the dashboard instead of a whitelabel 404.
 *
 * <p>Only browser-style 404s are redirected. {@code /api/**} and {@code /assets/**}
 * keep their real 404 so API clients still get a correct error and a missing image
 * doesn't silently resolve to an HTML page. Any non-404 (e.g. a 500) is passed
 * through with its original status.
 */
@Controller
public class CustomErrorController implements ErrorController {

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request) {
        int status = statusCode(request);
        String path = requestedPath(request);

        boolean apiOrAsset = path.startsWith("/api/") || path.startsWith("/assets/");
        if (status == HttpStatus.NOT_FOUND.value() && !apiOrAsset) {
            return "redirect:/overview";
        }
        return ResponseEntity.status(status == 0 ? HttpStatus.INTERNAL_SERVER_ERROR.value() : status).build();
    }

    private static int statusCode(HttpServletRequest request) {
        Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        return code != null ? Integer.parseInt(code.toString()) : 0;
    }

    private static String requestedPath(HttpServletRequest request) {
        Object uri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        return uri != null ? uri.toString() : "";
    }
}
