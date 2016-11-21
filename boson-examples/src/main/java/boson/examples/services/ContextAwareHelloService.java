package boson.examples.services;

import boson.Utils;
import boson.services.Services;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * An implementation of the HelloService that is aware of user principal/context information sent along by callers. For
 * this example we brute-force access to the context info but in a serious production application you'd probably want
 * to bury access to the 'Services' object in your own abstraction so you can better decouple your Boson code from your
 * service code.
 */
public class ContextAwareHelloService extends SimpleHelloService
{
    private static Logger logger = Utils.logger(ContextAwareHelloService.class);

    /** Provides access to the context provider */
    private Services services;

    /**
     * Creates the service instance, aware of the repository it belongs to so that it can pull context info
     * @param services The service repository this service belongs to and contains the context provider.
     */
    public ContextAwareHelloService(Services services)
    {
        this.services = services;
    }

    /**
     * Given a string like "Hello X", return the text "Goodbye X"
     * @param phrase The incoming hello phrase to parse and respond to
     * @return The "hello" phrase where "Hello" is replaced w/ "Goodbye"
     */
    @Override
    public CompletableFuture<String> say(String phrase)
    {
        // This would get ridiculous in a real application so look at Google Guice AOP for info on how to create a
        // logging aspect that puts your method logging in 1 place: https://github.com/google/guice/wiki/AOP
        Map<String, String> context = Utils.getContext(services);
        if (context != null)
        {
            logger.info(String.format("Invoking HelloService.say() as [id=%s][name=%s][email=%s]",
                context.get("id"),
                context.get("name"),
                context.get("email")));
        }
        else
        {
            logger.info("Invoking HelloService.say() as 'anonymous'");
        }
        return super.say(phrase);
    }
}
