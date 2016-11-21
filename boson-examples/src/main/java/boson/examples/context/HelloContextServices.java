package boson.examples.context;

import boson.Futures;
import boson.Utils;
import boson.examples.services.ContextAwareHelloService;
import boson.examples.services.HelloService;
import boson.services.Services;
import boson.transport.ServiceBusConfig;
import boson.transport.http.HttpTransportBindings;
import org.slf4j.Logger;

/**
 * Uses the HTTP transport to fire up a Boson-activated HelloService that is capable of receiving/logging user context
 * info that's automatically passed along with each request to the services we register here.
 */
public class HelloContextServices
{
    private static Logger logger = Utils.logger(HelloContextServices.class);

    public static void main(String [] args) throws Exception
    {
        logger.info("Starting HelloContextServices");
        Services services = new Services();

        // Other than having a reference to 'services' passed to our service this is the exact same setup as HelloHttpServices.
        // See the comments at the top of ContextAwareHelloService.java for a better way to do this.
        Futures.await(services.implement(
            HelloService.class,
            new ContextAwareHelloService(services),
            new HttpTransportBindings<>(),
            new ServiceBusConfig().uri("http://localhost:5454")));

        logger.info("HelloContextServices up and running. Press ENTER to quit.");
        System.console().readLine();
        Futures.await(services.disconnectAll());
    }
}
