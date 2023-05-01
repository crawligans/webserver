package cis5550.webserver.model;

import cis5550.webserver.Request;
import cis5550.webserver.Response;
import cis5550.webserver.Route;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Vector;

public class RouteTable {

    private final Map<String, RouteTable> staticRouteTable = new HashMap<>();
    private final List<Map.Entry<String, RouteTable>> paramRouteTable = new ArrayList<>();
    private Route rootRoute;
    private RouteTable fallbackRouteTable;

    public void insert(String path, Route route) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        this.insert(Arrays.stream(path.split("/")).toList(), route);
    }

    public void insert(List<String> path, Route route) {
        if (path == null || path.size() == 0) {
            this.rootRoute = route;
        } else if (path.get(0).equals("*")) {
            if (fallbackRouteTable == null) {
                this.fallbackRouteTable = new RouteTable();
            }
            fallbackRouteTable.insert(path.subList(1, path.size()), route);
        } else if (path.get(0).startsWith(":")) {
            String paramName = path.get(0).substring(1);
            RouteTable tb = new RouteTable();
            tb.insert(path.subList(1, path.size()), route);
            paramRouteTable.add(Map.entry(paramName, tb));
        } else {
            staticRouteTable.compute(path.get(0), (k, tb) -> {
                if (tb == null) {
                    tb = new RouteTable();
                }
                tb.insert(path.subList(1, path.size()), route);
                return tb;
            });
        }
    }

    public Map.Entry<List<String>, Route> getRoute(String path) {
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return this.getRoute(Arrays.stream(path.split("/")).toList());
    }

    public Map.Entry<List<String>, Route> getRoute(List<String> path) {
        RouteTable tb;
        if (path == null || path.size() == 0) {
            return Map.entry(new Vector<>(), rootRoute);
        } else if ((tb = staticRouteTable.get(path.get(0))) != null) {
            Map.Entry<List<String>, Route> e = tb.getRoute(path.subList(1, path.size()));
            e.getKey().add(0, path.get(0));
            return e;
        } else {
            List<Map.Entry<List<String>, Route>> paramRoutes =
                this.paramRouteTable.stream()
                    .map(e -> {
                        Map.Entry<List<String>, Route> ert = e.getValue()
                            .getRoute(path.subList(1, path.size()));
                        if (ert != null) {
                            ert.getKey().add(0, ":" + e.getKey());
                        }
                        return ert;
                    }).filter(Objects::nonNull)
                    .filter(e -> Objects.nonNull(e.getValue())).toList();
            return
                paramRoutes.stream().mapToInt(e -> e.getKey().size()).max()
                    .stream()
                    .mapToObj(i -> paramRoutes.stream().filter(e -> e.getKey().size() == i))
                    .flatMap(s -> s.map(e -> {
                        String key = e.getKey().get(0).substring(1);
                        return Map.entry(e.getKey(), (Route) (Request req, Response res) -> {
                            req.params().put(key, path.get(0));
                            return e.getValue().handle(req, res);
                        });
                    })).reduce((first, second) -> second)
                    .orElseGet(() ->
                        fallbackRouteTable != null ?
                            fallbackRouteTable.getRoute(path.subList(1, path.size())) : null);
        }
    }
}
