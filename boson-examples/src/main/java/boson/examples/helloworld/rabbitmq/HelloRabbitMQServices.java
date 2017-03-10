package boson.examples.helloworld.rabbitmq;

import boson.Futures;
import boson.Utils;
import boson.examples.services.HelloService;
import boson.examples.services.SimpleHelloService;
import boson.services.Services;
import boson.transport.ServiceBusConfig;
import boson.transport.rabbitmq.RabbitMQTransportBindings;
import org.slf4j.Logger;

import java.util.Scanner;

/**
 * Uses Boson to activate an instance of SimpleHelloService so that it can receive requests by polling a shared
 * requests queue from a RabbitMQ broker. After performing the "real" work, it will respond using one of the many
 * caller-specific response queues.
 */
public class HelloRabbitMQServices
{
    private static Logger logger = Utils.logger(HelloRabbitMQServices.class);

    public static void main(String [] args) throws Exception
    {
        logger.info("Starting services.");
        Services services = new Services();
        Futures.await(services.implement(
            HelloService.class,
            new SimpleHelloService(),
            new RabbitMQTransportBindings<>(),
            new ServiceBusConfig().uri("rabbitmq://localhost:5672")));

        logger.info("Services up and running. Press ENTER to quit.");
        new Scanner(System.in).nextLine();
        services.disconnectAll().join();
    }
}
