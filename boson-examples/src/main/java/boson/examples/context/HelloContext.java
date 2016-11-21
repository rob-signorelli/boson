package boson.examples.context;

import boson.Futures;
import boson.Utils;
import boson.examples.services.HelloService;
import boson.services.Services;
import boson.transport.ServiceBusConfig;
import boson.transport.http.HttpTransportBindings;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * This is a slight variant of the standard HelloHttp client/consumer code that still performs a handful of operations
 * using the HTTP transport, but also passes some user context information across to the service so your identity is
 * carried along to all other operations.
 */
public class HelloContext
{
    private static Logger logger = Utils.logger(HelloContext.class);

    public static void main(String [] args) throws Exception
    {
        logger.info("ContextPassing Starting");

        // By default Boson applies a 'ThreadLocal' provider so add our context to the current thread, but you can
        // swap your custom implementation in here as needed. This, however, is sufficient for servlet-based apps.
        Services services = new Services();
        HelloService service = Futures.await(services.consume(
            HelloService.class,
            new HttpTransportBindings<>(),
            new ServiceBusConfig().uri("http://localhost:5454")));

        // Execute a series of service calls as one user in Thread A
        CompletableFuture<Void> bob = CompletableFuture.runAsync(() -> {
            services.getContextProvider().setContext(createContext("12345", "Bob", "bob@example.com"));
            Futures.awaitAll(
                service.say("Hello World").thenAccept(logger::info),
                service.say("Doc says hello").thenAccept(logger::info));
        });

        // Execute a series of service calls as a completely separate user in Thread B
        CompletableFuture<Void> jane = CompletableFuture.runAsync(() -> {
            services.getContextProvider().setContext(createContext("ABCDE", "Jane", "jane@example.com"));
            Futures.awaitAll(
                service.say("Hello World").thenAccept(logger::info),
                service.say("Doc says hello").thenAccept(logger::info));
        });

        // Make sure that both Bob and Jane's tasks have completed
        Futures.awaitAll(bob, jane);
        logger.info("All tasks completed. Shutting down service connections.");
        Futures.await(services.disconnectAll());
    }

    /**
     * Create a dummy object that can be passed to the remote service implementation to represent the user making the
     * given request. In reality this would be something like a Spring SecurityContext or a JSON Web Token or whatever
     * represents your temporary "state" information.
     * @return The map of user details
     */
    protected static Map<String, String> createContext(String id, String name, String email)
    {
        Map<String, String> context = new HashMap<>();
        context.put("id", id);
        context.put("name", name);
        context.put("email", email);
        return context;
    }
}
