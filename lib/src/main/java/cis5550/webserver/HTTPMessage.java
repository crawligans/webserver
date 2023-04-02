package cis5550.webserver;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class HTTPMessage {

    protected final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final String initialLine;
    protected byte[] body;

    HTTPMessage(String initialLine, Map<String, String> headers, byte[] body) {
        this.initialLine = initialLine;
        this.headers.put("Date", DateTimeFormatter.RFC_1123_DATE_TIME.format(OffsetDateTime.now()));
        if (headers != null) {
            this.headers.putAll(headers);
        }
        this.body = body;
    }

    public static Map<String, String> parseHeaders(String headers) {
        return parseHeaders(Arrays.stream(headers.split("\r\n")));
    }

    private static Map<String, String> parseHeaders(Stream<String> headers) {
        return headers.takeWhile(s -> !s.isBlank())
            .map(s -> s.split(":\\s*"))
            .collect(Collectors.toMap(
                kv -> kv[0].trim(),
                kv -> kv[1],
                (v1, v2) -> v1 + ',' + v2,
                () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)
            ));
    }

    @Override
    public String toString() {
        return getHead() + new String(body) + "\r\n\r\n";
    }

    public String getHead() {
        return getInitialLine() + "\r\n"
            + headers.entrySet().stream().map(e -> e.getKey() + ':' + e.getValue())
            .collect(Collectors.joining("\r\n")) + "\r\n\r\n";
    }

    public String getInitialLine() {
        return initialLine;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
        this.headers.put("Content-Length", String.valueOf(body.length));
    }

    // HW1 - Just 1.1
    public enum HTTPVersion {
        HTTP1_1("HTTP/1.1");
        public final String versionString;

        HTTPVersion(String versionString) {
            this.versionString = versionString;
        }

        @Override
        public String toString() {
            return versionString;
        }
    }
}
