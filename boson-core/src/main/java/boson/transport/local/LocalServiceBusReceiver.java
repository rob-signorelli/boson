package boson.transport.local;

import boson.Futures;
import boson.transport.ServiceBusReceiver;
import boson.transport.ServiceBusReceiverAdapter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The communication transport used by a service implementation to receive work/method requests.
 */
class LocalServiceBusReceiver<T> extends ServiceBusReceiverAdapter<T>
{
    /**
     * No need to make this too complicated. Local services are running in the same VM, so we need some sort of lookup
     * the producer can use to find the other end of the local service bus.
     */
    private static Map<Class<?>, LocalServiceBusReceiver<?>> LOCAL_SERVICES = new HashMap<>();

    /**
     * Registers this end of the service bus with a lookup that can be used by the producer to send requests our way.
     * @param instance The actual service instance we're going to grab work for
     * @return A future that completes when the consumer is ready to take requests
     */
    @Override
    public CompletableFuture<ServiceBusReceiver<T>> connect(T instance)
    {
        if (instance == null)
            throw new IllegalArgumentException("Can't connect LocalServiceBusConsumer to null instance");

        service = instance;
        LOCAL_SERVICES.put(getServiceContract(), this);
        return Futures.of(this);
    }

    /**
     * Removes this service from the local service bus lookup, preventing it from taking requests
     * @return A future that completes when the consumer stops taking requests.
     */
    @Override
    public CompletableFuture<ServiceBusReceiver<T>> disconnect()
    {
        service = null;
        LOCAL_SERVICES.remove(getServiceContract());
        return Futures.of(this);
    }

    /**
     * Used by ServiceBusProducer to "magically" find the other end of the service bus, allowing it to send requests to
     * the VM's consumer instance for that type.
     * @param serviceInterface The primary service interface
     * @return The service that
     */
    @SuppressWarnings("unchecked")
    static <T> LocalServiceBusReceiver<T> lookup(Class<T> serviceInterface)
    {
        return (LocalServiceBusReceiver<T>)LOCAL_SERVICES.get(serviceInterface);
    }
}
