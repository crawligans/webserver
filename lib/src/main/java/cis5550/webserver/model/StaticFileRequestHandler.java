package cis5550.webserver.model;

import cis5550.webserver.HTTPMessage;
import cis5550.webserver.Request;
import cis5550.webserver.ResponseImpl;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class StaticFileRequestHandler {

    public static Object serve(Request request, cis5550.webserver.Response response, Path root) {
        ResponseImpl.Status responseStatus = validate(request, root);
        byte[] body;
        response.header("Server", "CIS5550/HW1 Spring 2023 Zhiyuan Wu");
        if (200 <= responseStatus.code && responseStatus.code < 300) {
            try {
                Path requestedFile = root.resolve("./" + request.url());
                body = Files.readAllBytes(requestedFile);
                if (responseStatus == ResponseImpl.Status.PARTIAL_CONTENT) {
                    final byte[] file = body;
                    final int len = body.length;
                    List<int[]> ranges =
                        Arrays.stream(request.headers("Range").split("=", 2)[1].trim().split(","))
                            .map(r -> r.split("-", 2)).map(r -> {
                                int start, end;
                                try {
                                    start = !r[0].isBlank() ? Integer.parseInt(r[0].trim()) : 0;
                                    end = !r[1].isBlank() ? Integer.parseInt(r[1].trim()) : len;
                                } catch (NumberFormatException e) {
                                    start = -1;
                                    end = -1;
                                }
                                return new int[]{start, end};
                            }).toList();
                    if (ranges.stream().anyMatch(
                        r -> r[0] < 0 || r[1] < 0 || r[0] > len || r[1] > len || r[0] > r[1])) {
                        throw new IndexOutOfBoundsException();
                    }
                    body = new byte[ranges.stream().mapToInt(r -> r[1] - r[0]).sum()];
                    ByteBuffer buf = ByteBuffer.wrap(body);
                    ranges.forEach(r -> buf.put(Arrays.copyOfRange(file, r[0], r[1])));
                }
                String ext = requestedFile.getFileName().toString();
                int extIdx = ext.lastIndexOf('.');
                ext = extIdx >= 0 ? ext.substring(extIdx).toLowerCase() : "";
                switch (ext) {
                    case ".jpg", ".jpeg" -> response.header("Content-Type", "image/jpeg");
                    case ".txt" -> response.header("Content-Type", "text/plain");
                    case ".htm", ".html" -> response.header("Content-Type", "text/html");
                    default -> response.header("Content-Type", "application/octet-stream");
                }
            } catch (IOException e) {
                // should not be reachable - already checked in `validate`
                responseStatus = ResponseImpl.Status.FORBIDDEN;
                body = responseStatus.toString().getBytes();
            } catch (IndexOutOfBoundsException e) {
                responseStatus = ResponseImpl.Status.RANGE_NOT_SATISFIABLE;
                body = responseStatus.toString().getBytes();
            }
        } else {
            body = responseStatus.toString().getBytes();
        }
        response.status(responseStatus);
        response.bodyAsBytes(body);
        return null;
    }

    public static ResponseImpl.Status validate(Request request, Path root) {
        if (request.requestMethod() == null || request.url() == null || request.protocol() == null
            || !request.headers().contains("Host")) {
            return ResponseImpl.Status.BAD_REQUEST;
        }
        if (!request.protocol().equalsIgnoreCase(HTTPMessage.HTTPVersion.HTTP1_1.versionString)) {
            return ResponseImpl.Status.HTTP_VERSION_NOT_SUPPORTED;
        }
        if (!List.of("GET", "HEAD", "POST", "PUT").contains(request.requestMethod())) {
            return ResponseImpl.Status.NOT_IMPLEMENTED;
        }
        if (List.of("POST", "PUT").contains(request.requestMethod())) {
            return ResponseImpl.Status.NOT_ALLOWED;
        }
        Path requestedFile = root.resolve("./" + request.url());
        if (!Files.exists(requestedFile)) {
            return ResponseImpl.Status.NOT_FOUND;
        }
        if (request.url().contains("..") || !Files.isReadable(requestedFile)) {
            return ResponseImpl.Status.FORBIDDEN;
        }
        try {
            String ifModifiedSince = request.headers("If-Modified-Since");
            if (ifModifiedSince != null &&
                Files.getLastModifiedTime(requestedFile).toInstant().compareTo(
                    Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(ifModifiedSince)))
                    < 0) {
                return ResponseImpl.Status.NOT_MODIFIED;
            }
        } catch (IOException e) {
            return ResponseImpl.Status.FORBIDDEN;
        }
        String range = request.headers("Range");
        if (range != null && !range.split("=", 2)[0].trim().equals("bytes")) {
            return ResponseImpl.Status.PARTIAL_CONTENT;
        }
        return ResponseImpl.Status.OK;
    }
}
