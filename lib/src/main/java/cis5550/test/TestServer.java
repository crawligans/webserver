package cis5550.test;

import static cis5550.webserver.Server.get;
import static cis5550.webserver.Server.securePort;

public class TestServer {

  public static void main(String[] args) throws Exception {
    securePort(443);
    get("/", (req, res) -> "Hello World - this is Zed Wu");
  }
}
