package boson.examples.helloworld.http;

import boson.Futures;
import boson.Utils;
import boson.examples.services.HelloService;
import boson.examples.services.SimpleHelloService;
import boson.services.Services;
import boson.transport.ServiceBusConfig;
import boson.transport.http.HttpTransportBindings;
import org.slf4j.Logger;

import java.util.Scanner;

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

        // If you want to encrypt all transport traffic over HTTPS just use "https://" in your URI instead of "http://"
        // and provide the keystore that we'll use to sign requests. This example uses a self-signed "localhost"
        // certificate with the keystore password "bosonFTW". In your code simply swap out the keystore path and
        // password as needed. Keep in mind that if you use "https" here, you'll need to do so in 'HelloHttp' as well!
        ServiceBusConfig config = (args.length > 0 && "https".equals(args[0]))
            ? new ServiceBusConfig().uri("https://localhost:5454").keystore("./boson-example-keystore", "bosonFTW")
            : new ServiceBusConfig().uri("http://localhost:5454");

        // Regardless of HTTP vs HTTPS the activation of the service is the same
        Futures.await(services.implement(
            HelloService.class,
            new SimpleHelloService(),
            new HttpTransportBindings<>(),
            config));

        logger.info("HelloService up and running. Press ENTER to quit.");
        new Scanner(System.in).nextLine();
        services.disconnectAll().join();
    }
}
