package cis5550.webserver.model;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class Session implements cis5550.webserver.Session {

  private final long creationTime;
  private final Map<String, Object> attributes = new ConcurrentHashMap<>();
  private long lastAccessedTime;
  private final String id;
  private int maxActiveInterval = 300;


  public Session(String id) {
    this.id = id != null ? id : UUID.randomUUID().toString();
    this.creationTime = System.currentTimeMillis();
    this.lastAccessedTime = System.currentTimeMillis();
  }

  public Session() {
    this(UUID.randomUUID().toString());
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public long creationTime() {
    return creationTime;
  }

  @Override
  public long lastAccessedTime() {
    return lastAccessedTime;
  }

  public void maxActiveInterval(int seconds) {
    this.maxActiveInterval = seconds;
  }

  @Override
  public void invalidate() {
    maxActiveInterval = Integer.MIN_VALUE;
  }

  @Override
  public Object attribute(String name) {
    this.lastAccessedTime = System.currentTimeMillis();
    return attributes.get(name);
  }

  @Override
  public void attribute(String name, Object value) {
    this.lastAccessedTime = System.currentTimeMillis();
    attributes.put(name, value);
  }

  public boolean isValid() {
    return maxActiveInterval > 0
        && System.currentTimeMillis() - lastAccessedTime <= maxActiveInterval * 1000L;
  }
}
