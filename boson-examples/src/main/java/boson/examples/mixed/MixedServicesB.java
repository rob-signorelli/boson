package boson.examples.mixed;

import boson.Futures;
import boson.Utils;
import boson.examples.services.RandomService;
import boson.examples.services.SimpleRandomService;
import boson.services.Services;
import boson.transport.ServiceBusConfig;
import boson.transport.rabbitmq.RabbitMQTransportBindings;
import org.slf4j.Logger;

/**
 * Uses Boson to activate a single RandomService over a RabbitMQ transport.
 */
public class MixedServicesB
{
    private static Logger logger = Utils.logger(MixedServicesB.class);

    public static void main(String [] args) throws Exception
    {
        logger.info("Starting MixedServicesB");
        Services services = new Services();

        // Activate our one service using RabbitMQ bindings as opposed to HTTP
        Futures.await(services.implement(
            RandomService.class,
            new SimpleRandomService(),
            new RabbitMQTransportBindings<>(),
            new ServiceBusConfig().uri("rabbitmq://localhost:5672")));

        logger.info("RandomService up and running over RabbitMQ. Press ENTER to quit.");
        System.console().readLine();
        Futures.await(services.disconnectAll());
    }
}
