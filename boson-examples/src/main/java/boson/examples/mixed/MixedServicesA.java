package boson.examples.mixed;

import boson.Futures;
import boson.Utils;
import boson.examples.services.HelloService;
import boson.examples.services.MathService;
import boson.examples.services.SimpleHelloService;
import boson.examples.services.SimpleMathService;
import boson.services.Services;
import boson.transport.ServiceBusConfig;
import boson.transport.http.HttpTransportBindings;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Uses Boson to activate instances of the HelloService and MathService to run in the same process using HTTP transports.
 */
public class MixedServicesA
{
    private static Logger logger = Utils.logger(MixedServicesA.class);

    public static void main(String [] args) throws Exception
    {
        logger.info("Starting MixedServicesA");
        Services services = new Services();

        // Activate both services using HTTP bindings
        Futures.await(CompletableFuture.allOf(
            services.implement(
                HelloService.class,
                new SimpleHelloService(),
                new HttpTransportBindings<>(),
                new ServiceBusConfig().uri("http://localhost:5678")),

            services.implement(
                MathService.class,
                new SimpleMathService(),
                new HttpTransportBindings<>(),
                new ServiceBusConfig().uri("http://localhost:5679"))));

        logger.info("HelloService and MathService up and running over HTTP. Press ENTER to quit.");
        System.console().readLine();
        Futures.await(services.disconnectAll());
    }
}
