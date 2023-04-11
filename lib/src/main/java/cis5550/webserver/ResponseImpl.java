package cis5550.webserver;

import java.util.Map;

abstract public class ResponseImpl extends HTTPMessage implements Response {

    public final HTTPVersion version;
    protected Status status;
    protected boolean manualWrite = false;
    private boolean halted;

    public ResponseImpl(HTTPVersion version, Status status, Map<String, String> headers, byte[] body) {
        super(version.toString() + ' ' + status, headers, body);
        this.version = version;
        this.status = status;
    }

    @Override
    public String getInitialLine() {
        return this.version.versionString + ' ' + this.status;
    }

    @Override
    public void bodyAsBytes(byte[] bodyArg) {
        if (!isManualWrite() && !isHalted()) {
            setBody(bodyArg);
        }
    }

    public boolean isManualWrite() {
        return manualWrite;
    }

    @Override
    public void type(String contentType) {
        this.header("Content-Type", contentType);
    }

    @Override
    public void header(String name, String value) {
        this.headers.put(name, value);
    }

    @Override
    public void status(int statusCode, String reasonPhrase) {
        this.status = new Status(statusCode, reasonPhrase);
    }

    public void status(Status status) {
        this.status = status;
    }


    @Override
    public void redirect(String url, int responseCode) {
        this.status(Status.valueOf(responseCode));
        this.header("Location", url);
    }

    public boolean isHalted() {
        return halted;
    }

    @Override
    public void halt(int statusCode, String reasonPhrase) {
        this.status(statusCode, reasonPhrase);
        this.body(status.toString());
        this.halted = true;
    }
}
