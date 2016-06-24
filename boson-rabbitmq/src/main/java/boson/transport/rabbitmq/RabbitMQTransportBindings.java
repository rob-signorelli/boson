package boson.transport.rabbitmq;

import boson.transport.ServiceBusConfig;
import boson.transport.ServiceBusDispatcher;
import boson.transport.ServiceBusReceiver;
import boson.transport.ServiceTransportBindings;

/**
 * A service bus transport that utilizes a series of RabbitMQ message queues to facilitate communication. This binding
 * utilizes a <a href="http://www.enterpriseintegrationpatterns.com/patterns/messaging/PollingConsumer.html">Polling Consumer</a>
 * pattern to handle the distribution of work to implementing services. The "secret sauce" to enable RPC-style communication
 * is that every caller must have their own response queue so that whoever picks up and completes the work, they have
 * a direct line back to the original caller.
 *
 * The caller's request should provide a "correlation" value which is the address of its personal response queue so
 * that it can respond directly without having to have any "real" knowledge of the original caller.
 */
public class RabbitMQTransportBindings<T> implements ServiceTransportBindings<T>
{
    /**
     * Defines the communication bindings required to make a request to the remote service. You can consider this the
     * "client" that consumes the service work.
     * @return The dispatcher/caller transport bindings
     */
    @Override
    public ServiceBusDispatcher<T> dispatcher(Class<T> serviceContract, ServiceBusConfig config)
    {
        return new RabbitMQServiceBusDispatcher<T>()
            .expose(serviceContract)
            .config(config);
    }

    /**
     * Defines the communication bindings required for a process to accept incoming service requests and respond back
     * to them. You can consider this the "server" and produces results for requestsed work.
     * @return The receiver/callee transport bindings
     */
    @Override
    public ServiceBusReceiver<T> receiver(Class<T> serviceContract, ServiceBusConfig config)
    {
        return new RabbitMQServiceBusReceiver<T>()
            .expose(serviceContract)
            .config(config);
    }

    /**
     * @return The debugging string for this object (the class name)
     */
    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }
}
