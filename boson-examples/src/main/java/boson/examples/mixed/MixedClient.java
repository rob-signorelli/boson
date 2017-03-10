package boson.examples.mixed;

import boson.Futures;
import boson.Utils;
import boson.examples.services.HelloService;
import boson.examples.services.MathService;
import boson.examples.services.RandomService;
import boson.services.Services;
import boson.transport.ServiceBusConfig;
import boson.transport.http.HttpTransportBindings;
import boson.transport.rabbitmq.RabbitMQTransportBindings;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * This shows the basic code required to fire up a Boson service and loosely interact with it. In this case, the service
 * is simply another object in the same VM, but the caller doesn't need to know that or care about that. The code will
 * send the phrase "Hello X" to the service and it will respond back to the caller with "Goodbye X" in the most
 * naive, non-error-checking way possible.
 */
public class MixedClient
{
    private static Logger logger = Utils.logger(MixedClient.class);

    public static void main(String [] args) throws Exception
    {
        logger.info("Hello");

        // Connect to all 3 services concurrently - 2 over HTTP and 1 over RabbitMQ. Unlike other examples that retrieve
        // the service proxy instance here, we can look them up in the repository later if we want.
        Services services = new Services();
        Futures.awaitAll(CompletableFuture.allOf(
            services.consume(
                HelloService.class,
                new HttpTransportBindings<>(),
                new ServiceBusConfig().uri("http://localhost:5678")),
            services.consume(
                MathService.class,
                new HttpTransportBindings<>(),
                new ServiceBusConfig().uri("http://localhost:5679")),
            services.consume(
                RandomService.class,
                new RabbitMQTransportBindings<>(),
                new ServiceBusConfig().uri("rabbitmq://localhost:5672"))));

        // Wait for all of the tests to run then shut down all of the services
        Futures.awaitAll(
            helloTests(services),
            mathTests(services),
            randomTests(services));

        logger.info("All tasks completed. Shutting down service connections.");
        services.disconnectAll().join();
    }

    /**
     * Execute some operations to the HelloService in parallel
     * @param serviceRepository The repository where we can look up our consumed service
     * @return A future that completes when all service calls have completed
     */
    private static CompletableFuture<?> helloTests(Services serviceRepository)
    {
        HelloService service = serviceRepository.get(HelloService.class);
        return Futures.all(
            service.say("Hello World").thenAccept(logger::info),
            service.say("hello world").thenAccept(logger::info),
            service.say("What's up Doc?").thenAccept(logger::info),
            service.say("Doc says hello").thenAccept(logger::info));
    }

    /**
     * Execute some operations to the MathService in parallel
     * @param serviceRepository The repository where we can look up our consumed service
     * @return A future that completes when all service calls have completed
     */
    private static CompletableFuture<?> mathTests(Services serviceRepository)
    {
        MathService service = serviceRepository.get(MathService.class);
        return Futures.all(
            service.add(1, 1).thenAccept(sum -> logger.info("1 + 1 = " + sum)),
            service.add(2, 6).thenAccept(sum -> logger.info("2 + 6 = " + sum)),
            service.add(0, 0).thenAccept(sum -> logger.info("0 + 0 = " + sum)),
            service.sub(1, 1).thenAccept(sub -> logger.info("1 - 1 = " + sub)),
            service.sub(8, 3).thenAccept(sub -> logger.info("8 + 3 = " + sub)));
    }

    /**
     * Execute some operations to the RandomService in parallel
     * @param serviceRepository The repository where we can look up our consumed service
     * @return A future that completes when all service calls have completed
     */
    private static CompletableFuture<?> randomTests(Services serviceRepository)
    {
        RandomService service = serviceRepository.get(RandomService.class);
        return Futures.all(
            service.randomLetters(1).thenAccept(text -> logger.info("randomLetters(1) => " + text)),
            service.randomLetters(5).thenAccept(text -> logger.info("randomLetters(5) => " + text)),
            service.randomLetters(8).thenAccept(text -> logger.info("randomLetters(8) => " + text)),
            service.randomInt(1, 3).thenAccept(num -> logger.info("randomInt(1, 3) => " + num)),
            service.randomInt(15, 30).thenAccept(num -> logger.info("randomInt(15, 30) => " + num)));
    }
}
