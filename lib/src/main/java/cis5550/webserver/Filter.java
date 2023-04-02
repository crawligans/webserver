package cis5550.webserver;

@FunctionalInterface
public interface Filter {

  static Filter haltWithStatus(Response.Status status) {
    return (req, res) -> res.halt(status.code, status.reason);
  }

  void handle(Request request, Response response) throws Exception;
}
