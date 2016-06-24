package boson.examples.helloworld.local;

import boson.Futures;
import boson.Utils;
import boson.examples.services.HelloService;
import boson.examples.services.SimpleHelloService;
import boson.services.Services;
import boson.transport.ServiceBusConfig;
import boson.transport.local.LocalTransportBindings;
import org.slf4j.Logger;

/**
 * This shows the basic code required to fire up a Boson service and loosely interact with it. In this case, the service
 * is simply another object in the same VM, but the caller doesn't need to know that or care about that. The code will
 * send the phrase "Hello X" to the service and it will respond back to the caller with "Goodbye X" in the most
 * naive, non-error-checking way possible.
 */
public class HelloLocal
{
    private static Logger logger = Utils.logger(HelloLocal.class);

    public static void main(String [] args) throws Exception
    {
        logger.info("Starting");
        Services services = new Services();
        Futures.await(services.implement(
            HelloService.class,
            new SimpleHelloService(),
            new LocalTransportBindings<>(),
            new ServiceBusConfig()));

        HelloService service = Futures.await(services.consume(
            HelloService.class,
            new LocalTransportBindings<>(),
            new ServiceBusConfig()));

        Futures.awaitAll(
            service.say("Hello World").thenAccept(logger::info),
            service.say("hello world").thenAccept(logger::info),
            service.say("What's up Doc?").thenAccept(logger::info),
            service.say("Doc says hello").thenAccept(logger::info));

        logger.info("All tasks completed. Shutting down service connections.");
        Futures.await(services.disconnectAll());
    }
}
