package boson.transport.local;

import boson.transport.ServiceBusConfig;
import boson.transport.ServiceBusReceiver;
import boson.transport.ServiceBusDispatcher;
import boson.transport.ServiceTransportBindings;

/**
 * Transport bindings that let two objects in the same VM/Classloader interact as boson services.
 */
public class LocalTransportBindings<T> implements ServiceTransportBindings<T>
{
    /**
     * Defines the communication bindings required to make a request to the remote service. You can consider this the
     * "client" that consumes the service work.
     * @return The dispatcher/caller transport bindings
     */
    @Override
    public ServiceBusDispatcher<T> dispatcher(Class<T> serviceContract, ServiceBusConfig config)
    {
        return new LocalServiceBusDispatcher<T>()
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
        return new LocalServiceBusReceiver<T>()
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