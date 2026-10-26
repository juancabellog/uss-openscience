package cl.uss.ca.proxy;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Reverse proxy fijo a Liberty local. El destino no es configurable por request.
 */
public class ApiProxyServlet extends HttpServlet {

    private static final Set<String> SKIP_REQUEST = new HashSet<String>(Arrays.asList(
            "connection", "content-length", "host", "keep-alive", "proxy-connection",
            "transfer-encoding", "te", "trailer", "upgrade"
    ));
    private static final Set<String> SKIP_RESPONSE = new HashSet<String>(Arrays.asList(
            "connection", "keep-alive", "proxy-connection", "transfer-encoding",
            "te", "trailer", "upgrade"
    ));

    private String targetBase = "http://127.0.0.1:9080/api";

    @Override
    public void init() {
        String configured = getServletConfig().getInitParameter("targetBase");
        if (configured != null && !configured.trim().isEmpty()) {
            targetBase = configured.trim().replaceAll("/$", "");
        }
        log("OPENSCIENCE API proxy → " + targetBase);
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String path = request.getPathInfo() == null ? "" : request.getPathInfo();
        String query = request.getQueryString();
        String target = targetBase + path + (query == null ? "" : "?" + query);

        HttpURLConnection remote = (HttpURLConnection) new URL(target).openConnection();
        remote.setInstanceFollowRedirects(false);
        remote.setConnectTimeout(10_000);
        remote.setReadTimeout(600_000);
        remote.setRequestMethod(request.getMethod());
        remote.setDoInput(true);

        Collections.list(request.getHeaderNames()).forEach(name -> {
            if (SKIP_REQUEST.contains(name.toLowerCase())) {
                return;
            }
            Collections.list(request.getHeaders(name)).forEach(value ->
                    remote.addRequestProperty(name, value));
        });

        boolean hasBody = request.getContentLength() > 0
                || request.getContentLengthLong() > 0
                || request.getHeader("Transfer-Encoding") != null;
        if (hasBody && !"GET".equalsIgnoreCase(request.getMethod())
                && !"HEAD".equalsIgnoreCase(request.getMethod())) {
            remote.setDoOutput(true);
            try (InputStream in = request.getInputStream();
                 OutputStream out = remote.getOutputStream()) {
                copy(in, out);
            }
        }

        int status;
        try {
            status = remote.getResponseCode();
        } catch (IOException first) {
            InputStream err = remote.getErrorStream();
            if (err == null) {
                throw first;
            }
            status = remote.getResponseCode();
        }

        response.setStatus(status);
        remote.getHeaderFields().forEach((name, values) -> {
            if (name == null || SKIP_RESPONSE.contains(name.toLowerCase())) {
                return;
            }
            for (String value : values) {
                response.addHeader(name, value);
            }
        });

        InputStream body = status >= 400 ? remote.getErrorStream() : remote.getInputStream();
        if (body != null) {
            try (InputStream in = body;
                 OutputStream out = response.getOutputStream()) {
                copy(in, out);
            }
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
    }
}
