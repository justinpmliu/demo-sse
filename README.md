# Notes
1. This repository has 4 modules:
   - demo-sse-mvc-server (port: 8080): Use Spring MVC SseEmitter to implement SSE server.
   - demo-sse-jaxrs-client (port: 8082): Use Apache CXF to implement SSE client.
   - demo-sse-flux-server (port: 8080): Use Spring WebFlux and ServerSentEvent entity to implement SSE server.
   - demo-sse-flux-client (port: 8083): Use Spring WebFlux WebClient to implement SSE client. 
     - This approach is not recommended, because when SSE server is restarted and the client reconnects the SSE server, "last-event-id" is missing in the request header.
2. The SSE server (demo-sse-mvc-server / demo-sse-flux-server) exposes an endpoint **(GET) /sse-server/subscribe?name={name}&lastEventId={lastEventId}** for client to subscribe. "name" is the event name. "lastEventId" is the id that the client last received. "lastEventId" is mainly used for client restart. 
3. The SSE server stores the sent events in H2 database, persistent in "./demodb" folder.
4. The SSE client (demo-sse-jaxrs-client / demo-sse-flux-client) stores the last event id in H2 database, persistent in "./demodb" folder.
5. When a client connects to SSE server, the events whose id > {lastEventId} will be resent to the client. if lastEventId is null, all the events will be sent to the client.
6. The sent events stored in SSE server will be cleaned after 4 hours, which is configurable.
7. The SSE server exposes an endpoint **(POST) /sse-server/publish** for other application to publish events to SSE server. The request body is JSON like:
  ```json
  {
      "name": "custom-event",
      "data": ["Hello World 1", "Hello World 2"]
  }
  ```
8. H2 database uses a cache of 32 entries for sequences. To avoid the event id incremented by 32 after SSE server restarts, please gracefully shutdown SSE server, e.g. using "Exit" button in IntelliJ.

# Reference
1. [Server-Sent Events in Spring | Baeldung](https://www.baeldung.com/spring-server-sent-events) 
2. [Spring MVC Streaming and SSE Request Processing | Baeldung](https://www.baeldung.com/spring-mvc-sse-streams)
3. [Server-Sent Events (SSE) in JAX-RS](https://www.baeldung.com/java-ee-jax-rs-sse)
4. [Server-Sent Events (SSE) in Spring 5 with Web MVC and Web Flux](https://liakh-aliaksandr.medium.com/server-sent-events-sse-in-spring-5-with-web-mvc-and-web-flux-44c926b59f36)
5. [Server-Sent Events Using Spring | DZone](https://dzone.com/articles/server-sent-events-using-spring)
6. [Spring SseEmitter.complete() trigger an EventSource reconnect - how to close connection | stackoverflow](https://stackoverflow.com/questions/55287474/should-spring-sseemitter-complete-trigger-an-eventsource-reconnect-how-to-cl)
7. [Chapter 16. Server-Sent Events (SSE) | Oreilly](https://www.oreilly.com/library/view/high-performance-browser/9781449344757/ch16.html)
