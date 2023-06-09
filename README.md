# Modules
  1. demo-sse-mvc-server: Use Spring MVC SseEmitter to implement SSE server.
  2. demo-sse-jaxrs-client: Use Apache CXF to implement SSE client.
  3. demo-sse-flux-server: Use Spring WebFlux and ServerSentEvent entity to implement SSE server.
  4. demo-sse-flux-client: Use Spring WebFlux WebClient to implement SSE client. 
     - This approach is not recommended, because when SSE server is restarted and the client reconnects the SSE server, "last-event-id" is missing in the request header.

# Usage
  1. Launch consumer
     - demo-sse-jaxrs-client:
     ```
     (GET) http://localhost:8082/sse-client/consumer?name={name}
     ```
     - demo-sse-flux-client
     ```
     (GET) http://localhost:8083/sse-client/consumer?name={name}
     ```

     Example:
     ```
     (GET) http://localhost:8082/sse-client/consumer?name=custom-event
     ```
  2. Publish event to SSE server
     ```
     (POST) http://localhost:8080/sse-server/publish
     ```
     Request body:
     ```json
     {
        "name": "custom-event",
        "data": "Hello World!"
     }
     ```
# Reference
1. [Server-Sent Events in Spring | Baeldung](https://www.baeldung.com/spring-server-sent-events) 
2. [Spring MVC Streaming and SSE Request Processing | Baeldung](https://www.baeldung.com/spring-mvc-sse-streams)
3. [Server-Sent Events (SSE) in JAX-RS](https://www.baeldung.com/java-ee-jax-rs-sse)
4. [Server-Sent Events (SSE) in Spring 5 with Web MVC and Web Flux](https://liakh-aliaksandr.medium.com/server-sent-events-sse-in-spring-5-with-web-mvc-and-web-flux-44c926b59f36)
5. [Server-Sent Events Using Spring | DZone](https://dzone.com/articles/server-sent-events-using-spring)
6. [Spring SseEmitter.complete() trigger an EventSource reconnect - how to close connection | stackoverflow](https://stackoverflow.com/questions/55287474/should-spring-sseemitter-complete-trigger-an-eventsource-reconnect-how-to-cl)
7. [Chapter 16. Server-Sent Events (SSE) | Oreilly](https://www.oreilly.com/library/view/high-performance-browser/9781449344757/ch16.html)
