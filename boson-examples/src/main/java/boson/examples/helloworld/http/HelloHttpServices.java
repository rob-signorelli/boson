package boson.examples.helloworld.http;

import boson.Futures;
import boson.Utils;
import boson.examples.services.HelloService;
import boson.examples.services.SimpleHelloService;
import boson.services.Services;
import boson.transport.ServiceBusConfig;
import boson.transport.http.HttpTransportBindings;
import org.slf4j.Logger;

/**
 * Uses Boson to activate an instance of SimpleHelloService so that it can receive requests over HTTP. You'll need
 * to run HelloHttp in another terminal to fire off requests that will be picked up by this service.
 */
public class HelloHttpServices
{
    private static Logger logger = Utils.logger(HelloHttpServices.class);

    public static void main(String [] args) throws Exception
    {
        logger.info("Starting SimpleHelloService");
        Services services = new Services();
        Futures.await(services.implement(
            HelloService.class,
            new SimpleHelloService(),
            new HttpTransportBindings<>(),
            new ServiceBusConfig().uri("http://localhost:5454")));

        logger.info("HelloService up and running. Press ENTER to quit.");
        System.console().readLine();
        Futures.await(services.disconnectAll());
    }
}
