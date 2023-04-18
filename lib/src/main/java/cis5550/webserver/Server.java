package cis5550.webserver;

import cis5550.tools.Logger;
import cis5550.webserver.HTTPMessage.HTTPVersion;
import cis5550.webserver.Request.Method;
import cis5550.webserver.Response.Status;
import cis5550.webserver.model.RouteTable;
import cis5550.webserver.model.Session;
import cis5550.webserver.model.StaticFileRequestHandler;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.net.ServerSocketFactory;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class Server implements AutoCloseable {

    private static final int NUM_WORKERS = 100;
    private static final Logger logger = Logger.getLogger(Server.class);
    private static final Map<String, Map<Method, RouteTable>> hostMethodRouteTables = new HashMap<>();
    private static Server serverInstance;
    private static int portConfig = 80;
    private static int securePortConfig = 443;
    private static String host = null;
    private static Route staticRoute = Route.returnStatus(Status.NOT_FOUND);
    private static Function<Route, Route> runBefore = rt -> (req, res) -> res.isHalted() ? null
        : rt.handle(req, res);
    private static Function<Route, Route> runAfter = Function.identity();
    private final Executor threadPool = new ScheduledThreadPoolExecutor(16) {{
        setMaximumPoolSize(NUM_WORKERS);
    }};
    private final byte[] CLRF = {13, 10};
    private int port = portConfig;
    private int securePort = securePortConfig;
    private ServerSocket ssock;
    private ServerSocket secureSsock;
    private final Map<String, cis5550.webserver.Session> sessions = new ConcurrentHashMap<>();
    private final Executor sessionCleanupPool = new ScheduledThreadPoolExecutor(1) {{
        setMaximumPoolSize(8);
        scheduleAtFixedRate(() -> sessions.values().removeIf(v -> !v.isValid()), 30, 30,
            TimeUnit.SECONDS);
    }};

    private Server(int port, int securePort) {
        this.port = port;
        this.securePort = securePort;
    }

    public static void before(Filter ft) {
        Function<Route, Route> runBeforeCapture = runBefore;
        runBefore = rt0 -> (req, res) -> {
            ft.handle(req, res);
            return runBeforeCapture.apply(rt0).handle(req, res);
        };
    }

    public static void after(Filter ft) {
        Function<Route, Route> runAfterCapture = runAfter;
        runAfter = rt0 -> (req, res) -> {
            Object retHandle = runAfterCapture.apply(rt0).handle(req, res);
            ft.handle(req, res);
            return retHandle;
        };
    }

    public static void get(String pattern, Route route) throws Exception {
        insertRoute(Method.GET, pattern, route);
        if (!isRunning()) {
            run();
        }
    }

    protected static void insertRoute(Method method, String pattern,
        Route route) {
        synchronized (getInstance()) {
            hostMethodRouteTables.compute(host, (h, methodRouteTables) -> {
                if (methodRouteTables == null) {
                    methodRouteTables = new EnumMap<>(Method.class);
                }
                methodRouteTables.compute(method, (m, routeTable) -> {
                    if (routeTable == null) {
                        routeTable = new RouteTable();
                    }
                    routeTable.insert(pattern,
                        (request, response) -> runBefore.apply(runAfter.apply(route))
                            .handle(request, response));
                    return routeTable;
                });
                return methodRouteTables;
            });
        }
    }

    public static boolean isRunning() {
        return serverInstance != null && (serverInstance._isRunning()
            || serverInstance._isRunningSecure());
    }

    public static void run()
        throws IOException, UnrecoverableKeyException, CertificateException, NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        Server instance = getInstance();
        instance.start();
        new Thread(() -> instance.listen(instance.ssock)).start();
        new Thread(() -> instance.listen(instance.secureSsock)).start();
    }

    private static Server getInstance() {
        if (serverInstance == null) {
            serverInstance = new Server(portConfig, securePortConfig);
        }
        return serverInstance;
    }

    public static void host(String host) {
        Server.host = host;
    }

    public static void host(String host, String keyStore, String pass) {
        // TODO
        Server.host = host;
    }

    public static void post(String pattern, Route route) throws Exception {
        insertRoute(Method.POST, pattern, route);
        if (!isRunning()) {
            run();
        }
    }

    public static void put(String pattern, Route route) throws Exception {
        insertRoute(Method.PUT, pattern, route);
        if (!isRunning()) {
            run();
        }
    }


    public static void port(int port) {
        portConfig = port;
    }

    public static void securePort(int port) {
        securePortConfig = port;
    }

    private static void stop() throws IOException {
        if (isRunning()) {
            getInstance().close();
        }
        serverInstance = null;
    }


    @Override
    public void close() throws IOException {
        if (_isRunning()) {
            ssock.close();
        }
        if (_isRunningSecure()) {
            secureSsock.close();
        }
    }

    private boolean _isRunning() {
        return ssock != null && !ssock.isClosed();
    }

    private boolean _isRunningSecure() {
        return secureSsock != null && !secureSsock.isClosed();
    }

    private void start() throws IOException {
        if (_isRunning() || _isRunningSecure()) {
            throw new IllegalStateException("Server already started");
        }
        this.ssock = new ServerSocket(this.port);
        logger.info("Server Started on port " + this.port + "(HTTP)");

        try {
            String pwd = "secret";
            KeyStore keyStore = KeyStore.getInstance("JKS");
            keyStore.load(new FileInputStream("keystore.jks"), pwd.toCharArray());
            KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance("SunX509");
            keyManagerFactory.init(keyStore, pwd.toCharArray());
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(keyManagerFactory.getKeyManagers(), null, null);
            ServerSocketFactory factory = sslContext.getServerSocketFactory();
            this.secureSsock = factory.createServerSocket(this.securePort);
            logger.info("Server Started on port " + this.securePort + "(HTTPS)");
        } catch (Exception e) {
            // just so it does not clog up console
            logger.warn("Could not start HTTPS server");
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (isRunning()) {
                    serverInstance.close();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    private void listen(ServerSocket ssock) {
        this.listen(ssock, -1);
    }

    private void listen(ServerSocket ssock, int maxRetry) {

        // https socket could be null if it throws an exception
        if (ssock == null)
            return;

        logger.info("Listening on " + ssock.getInetAddress() + ":" + ssock.getLocalPort());
        int ex = 0;
        while (maxRetry < 0 || ex <= maxRetry) {
            try {
                Socket sock = ssock.accept();
                String clientAddr = sock.getRemoteSocketAddress().toString();
                logger.info("Incoming connection from " + clientAddr);
                threadPool.execute(() -> {
                    try {
                        handleRequests(sock);
                    } catch (IOException e) {
                        logger.error(e.getLocalizedMessage(), e);
                    } catch (Exception e) {
                        logger.fatal(e.getLocalizedMessage(), e);
                    } finally {
                        try {
                            logger.info("Closing connection from " + sock.getRemoteSocketAddress());
                            sock.close();
                        } catch (IOException e) {
                            logger.fatal(e.getLocalizedMessage(), e);
                        }
                    }
                });
            } catch (IOException e) {
                logger.error(e.getLocalizedMessage(), e);
                ex++;
            } catch (Exception e) {
                logger.fatal(e.getLocalizedMessage(), e);
                throw e;
            }
        }
    }

    private void handleRequests(Socket clientSocket) throws Exception {
        boolean connectionOpen = true;
        InputStream inputStream = new BufferedInputStream(clientSocket.getInputStream());
        OutputStream outputStream = new BufferedOutputStream(clientSocket.getOutputStream());
        while (connectionOpen && !clientSocket.isInputShutdown()) {
            String[] head = readHead(inputStream).split(new String(CLRF), 2);

            logger.info(clientSocket.getRemoteSocketAddress() + ": " + head[0]);
            String[] req = head[0].split("\\s+");

            Map<String, String> headers = HTTPMessage.parseHeaders(head[1]);

            byte[] body = inputStream.readNBytes(
                Integer.parseInt(Objects.requireNonNullElse(headers.get("Content-Length"), "0")));

            Request request = null;
            Route rt;
            ResponseImpl response = new ResponseImpl(HTTPVersion.HTTP1_1, Status.OK, null, null) {
                @Override
                public void write(byte[] b) throws Exception {
                    if (!isHalted()) {
                        OutputStream outputStream = clientSocket.getOutputStream();
                        if (!manualWrite) {
                            this.header("Connection", "close");
                            this.headers.remove("Content-Length");
                            writeHead();
                        }
                        this.manualWrite = true;
                        outputStream.write(b);
                    }
                }

                @Override
                public void commit(Object overrideBody) throws IOException {
                    if (!isManualWrite()) {
                        OutputStream outputStream = clientSocket.getOutputStream();
                        if (overrideBody != null) {
                            setBody(overrideBody.toString().getBytes(StandardCharsets.UTF_8));
                        }
                        if (body == null) {
                            setBody(new byte[0]);
                        }
                        writeHead();
                        outputStream.write(this.body);
                    }
                }

                public void writeHead() throws IOException {
                    if (!isManualWrite()) {
                        OutputStream outputStream = clientSocket.getOutputStream();
                        outputStream.write(getHead().getBytes(StandardCharsets.UTF_8));
                    }
                }
            };
            if (req.length < 3) {
                rt = Route.returnStatus(Status.BAD_REQUEST);
                connectionOpen = false;
            } else {
                String[] url = req[1].split("\\?");
                Map<String, String> queryParams;
                List<String> queryStrings = new ArrayList<>();
                if (url.length > 1) {
                    queryStrings.add(url[1]);
                }
                if ("application/x-www-form-urlencoded".equalsIgnoreCase(
                    headers.get("Content-Type"))) {
                    queryStrings.add(new String(body, StandardCharsets.UTF_8));
                }
                queryParams = queryStrings.stream().flatMap(qs -> Arrays.stream(qs.split("&")))
                    .filter(Predicate.not(String::isBlank)).map(kv -> kv.split("=")).collect(
                        Collectors.toMap(kv -> URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                            kv -> URLDecoder.decode(kv[1], StandardCharsets.UTF_8)));
                request = new RequestImpl(Method.valueOf(req[0].toUpperCase()), url[0], req[2],
                    headers, queryParams, new HashMap<>(),
                    (InetSocketAddress) clientSocket.getRemoteSocketAddress(), body) {
                    @Override
                    public cis5550.webserver.Session session() {
                        String id = cookie("SessionID");
                        cis5550.webserver.Session session = sessions.compute(
                            id != null ? id : UUID.randomUUID().toString(), (k, sesh) -> {
                                if (sesh == null || !sesh.isValid()) {
                                    response.header("Set-Cookie", "SessionID=" + k);
                                    sesh = new Session(k);
                                }
                                return sesh;
                            });
                        this.cookies.forEach(session::attribute);
                        return session;
                    }
                };
                rt = getRoute(Method.valueOf(request.requestMethod().toUpperCase()),
                    request.headers("Host").split(":")[0], request.url());
            }

            Object responseBodyOverride;
            try {
                responseBodyOverride = rt.handle(request, response);
                connectionOpen = connectionOpen && !response.isManualWrite();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                responseBodyOverride = Route.returnStatus(Status.INTERNAL_SERVER_ERROR)
                    .handle(request, response);
                connectionOpen = false;
            }
            if (Method.HEAD.toString().equalsIgnoreCase(request.requestMethod())) {
                response.writeHead();
            } else {
                response.commit(responseBodyOverride);
            }
            outputStream.flush();

            connectionOpen = connectionOpen && !"close".equalsIgnoreCase(headers.get("Connection"));
            connectionOpen = connectionOpen && !clientSocket.isClosed();
        }
    }

    private String readHead(InputStream inputStream) throws IOException {
        int BUF_SIZE = 1024;
        ByteBuffer buffer = ByteBuffer.allocate(BUF_SIZE);
        int match = 0, read = 0;
        StringBuilder head = new StringBuilder();
        while (match < 4) {
            byte b = (byte) inputStream.read();
            read++;
            if (b == CLRF[match % CLRF.length]) {
                match++;
            } else {
                match = 0;
            }
            buffer.put(b);
            if (match > 0 && match % 2 == 0 || read >= BUF_SIZE) {
                buffer.flip();
                byte[] buf = new byte[read];
                buffer.get(buf);
                head.append(new String(buf));
                buffer.clear();
                read = 0;
            }
            if (b == -1) {
                buffer.clear();
                throw new IOException("Stream closed before end of header");
            }
        }
        return head.toString();
    }

    private Route getRoute(Method method, String host, String path) {
        Map<Method, RouteTable> hostTable = hostMethodRouteTables.get(
            host);
        if (hostTable == null) {
            hostTable = hostMethodRouteTables.get(null);
        }
        Map.Entry<List<String>, Route> routeEntry =
            hostTable != null ? hostTable.get(method).getRoute(path) : null;
        return routeEntry != null ? routeEntry.getValue() : staticRoute;
    }

    public static class staticFiles {

        public static void location(String s) throws Exception {
            if (s == null) {
                staticRoute = Route.returnStatus(Status.NOT_FOUND);
            } else {
                staticRoute = (req, res) -> StaticFileRequestHandler.serve(req, res, Path.of(s));
            }
            if (!isRunning()) {
                run();
            }
        }
    }
}
