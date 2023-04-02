package cis5550.webserver;

import java.io.IOException;

public interface Response {

    // The methods below are used to set the body, either as a string or as an array of bytes
    // (if the application wants to return something binary - say, an image file). Your server
    // should send back the following in the body of the response:
    //   * If write() has been called, ignore both the return value of Route.handle() and
    //     any calls to body() and bodyAsBytes().
    //   * If write() has not been called and Route.handle() returns something other than null,
    //     call the toString() method on that object and send the result.
    //   * If write() has not been called and Route.handle returns null(), use the value from
    //     the most recent body() or bodyAsBytes() call.
    //   * If none of write(), body(), and bodyRaw() have been called and Route.handle returns null,
    //     do not send a body in the response.
    default void body(String body) {
        bodyAsBytes(body.getBytes());
    }

    void bodyAsBytes(byte[] bodyArg);

    boolean isManualWrite();

    // This method adds a header. For instance, header("Cookie", "abc=def") should cause your
    // server to eventually send a header line "Cookie: abc=def". This method can be called
    // multiple times with the same header name; the result should be multiple header lines.
    // type(X) should be the same as header("Content-Type", X). If write() has been called,
    // these methods should have no effect.
    void header(String name, String value);

    void type(String contentType);

    // This method sets the status code and the reason phrase. If it is called more than once,
    // use the latest values. If it is never called, use 200 and "OK". If write() has been
    // called, status() should have no effect.
    void status(int statusCode, String reasonPhrase);

    void status(Status status);

    // This method can be used to send data directly to the connection, without buffering it
    // in an object in memory. The first time write() is called, it should 'commit' the
    // response by sending out the status code/reason phrase and any headers that have been
    // set so far. Your server should 1) add a 'Connection: close' header, and it should
    // 2) NOT add a Content-Length header in this case. Then, and in any subsequent calls,
    // it should simply write the provided bytes directly to the connection.
    void write(byte[] b) throws Exception;

    // Your server should send back the following in the body of the response:
    //   * If write() has been called, ignore both the return value of Route.handle() and
    //     any calls to body() and bodyAsBytes().
    //   * If write() has not been called and Route.handle() returns something other than null,
    //     call the toString() method on that object and send the result.
    //   * If write() has not been called and Route.handle returns null(), use the value from
    //     the most recent body() or bodyAsBytes() call.
    //   * If none of write(), body(), and bodyRaw() have been called and Route.handle returns null,
    //     do not send a body in the response.
    void commit(Object overrideBody) throws IOException;

    void writeHead() throws IOException;

    // EXTRA CREDIT ONLY - please see the handout for details. If you are not doing the extra
    // credit, please implement this with a dummy method that does nothing.
    void redirect(String url, int responseCode);

    // EXTRA CREDIT ONLY - please see the handout for details. If you are not doing the extra
    // credit, please implement this with a dummy method that does nothing.
    void halt(int statusCode, String reasonPhrase);

    boolean isHalted();

    class Status {

        public static final Status OK = new Status(200, "OK");
        public static final Status PARTIAL_CONTENT = new Status(206, "Partial Content");
        public static final Status MULTIPLE_CHOICE = new Status(300, "Multiple Choice");
        public static final Status MOVED_PERMANENTLY = new Status(301, "Moved Permanently");
        public static final Status FOUND = new Status(302, "Found");
        public static final Status SEE_OTHER = new Status(303, "See Other");
        public static final Status NOT_MODIFIED = new Status(304, "Not Modified");
        public static final Status TEMPORARY_REDIRECT = new Status(307, "Temporary Redirect");
        public static final Status PERMANENT_REDIRECT = new Status(308, "Permanent Redirect");
        public static final Status BAD_REQUEST = new Status(400, "Bad Request");
        public static final Status FORBIDDEN = new Status(403, "Forbidden");
        public static final Status NOT_FOUND = new Status(404, "Not Found");
        public static final Status NOT_ALLOWED = new Status(405, "Not Allowed");
        public static final Status RANGE_NOT_SATISFIABLE = new Status(416, "Range Not Satisfiable");
        public static final Status INTERNAL_SERVER_ERROR = new Status(500, "Internal Server Error");
        public static final Status NOT_IMPLEMENTED = new Status(501, "Not Implemented");
        public static final Status HTTP_VERSION_NOT_SUPPORTED = new Status(505,
            "HTTP Version Not Supported");
        public final int code;
        public final String reason;

        public Status(int code, String reason) {
            this.code = code;
            this.reason = reason;
        }

        public static Status valueOf(int code) {
            switch (code) {
                case 200 -> {
                    return OK;
                }
                case 206 -> {
                    return PARTIAL_CONTENT;
                }
                case 300 -> {
                    return MULTIPLE_CHOICE;
                }
                case 301 -> {
                    return MOVED_PERMANENTLY;
                }
                case 302 -> {
                    return FOUND;
                }
                case 303 -> {
                    return SEE_OTHER;
                }
                case 304 -> {
                    return NOT_MODIFIED;
                }
                case 307 -> {
                    return TEMPORARY_REDIRECT;
                }
                case 308 -> {
                    return PERMANENT_REDIRECT;
                }
                case 400 -> {
                    return BAD_REQUEST;
                }
                case 403 -> {
                    return FORBIDDEN;
                }
                case 404 -> {
                    return NOT_FOUND;
                }
                case 405 -> {
                    return NOT_ALLOWED;
                }
                case 416 -> {
                    return RANGE_NOT_SATISFIABLE;
                }
                case 500 -> {
                    return INTERNAL_SERVER_ERROR;
                }
                case 501 -> {
                    return NOT_IMPLEMENTED;
                }
                case 505 -> {
                    return HTTP_VERSION_NOT_SUPPORTED;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return code + " " + reason;
        }
    }
}
