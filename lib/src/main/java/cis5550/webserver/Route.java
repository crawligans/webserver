package cis5550.webserver;

@FunctionalInterface
public interface Route {

  static Route returnStatus(Response.Status status) {
    return (req, res) -> {
      res.status(status);
      res.body(status.toString());
      return null;
    };
  }

  Object handle(Request request, Response response) throws Exception;
}
