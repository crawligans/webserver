package cis5550.webserver;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

// Provided as part of the framework code

public abstract class RequestImpl extends HTTPMessage implements cis5550.webserver.Request {

    final Method method;
    final String url;
    final String protocol;
    final InetSocketAddress remoteAddr;
    final Map<String, String> queryParams;
    protected Map<String, String> cookies;
    Map<String, String> params;


    protected RequestImpl(Method method, String url, String protocol, Map<String, String> headers,
        Map<String, String> queryParams, Map<String, String> params, InetSocketAddress remoteAddr,
        byte[] bodyRaw) {
        super(method.toString() + ' ' + url + ' ' + protocol, headers, bodyRaw);
        this.method = method;
        this.url = url;
        this.remoteAddr = remoteAddr;
        this.protocol = protocol;
        this.queryParams = queryParams;
        this.params = params != null ? params : new HashMap<>();
    }

    private void parseCookies() {
        String cookies = this.headers("Cookie");
        this.cookies = cookies != null ? Arrays.stream(cookies.split(";\\s*"))
            .map(kv -> kv.split("=")).collect(
                Collectors.toMap(kv -> kv[0], kv -> kv[1], (v1, v2) -> v1 + ";" + v2,
                    () -> new TreeMap<>(String.CASE_INSENSITIVE_ORDER)))
            : new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    }

    public String cookie(String key) {
        if (cookies == null) {
            parseCookies();
        }
        return cookies.get(key);
    }

    @Override
    public String getInitialLine() {
        return requestMethod() + ' ' + url() + ' ' + protocol();
    }

    public String requestMethod() {
        return method.toString();
    }

    public String url() {
        return url;
    }

    public String protocol() {
        return protocol;
    }

    public Method method() {
        return method;
    }

    public void setParams(Map<String, String> paramsArg) {
        params = paramsArg;
    }

    public int port() {
        return remoteAddr.getPort();
    }

    public String contentType() {
        return headers.get("content-type");
    }

    public String ip() {
        return remoteAddr.getAddress().getHostAddress();
    }

    public String body() {
        return new String(body, StandardCharsets.UTF_8);
    }

    public byte[] bodyAsBytes() {
        return body;
    }

    public int contentLength() {
        return body.length;
    }

    public String headers(String name) {
        return headers.get(name.toLowerCase());
    }

    public Set<String> headers() {
        return headers.keySet();
    }

    public String queryParams(String param) {
        return queryParams.get(param);
    }

    public Set<String> queryParams() {
        return queryParams.keySet();
    }

    public String params(String param) {
        return params.get(param);
    }

    public Map<String, String> params() {
        return params;
    }

}
