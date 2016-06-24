package boson.transport.local;

import boson.Futures;
import boson.Utils;
import boson.services.ServiceRequest;
import boson.services.ServiceResponse;
import boson.transport.ServiceBusDispatcher;
import boson.transport.ServiceBusDispatcherAdapter;

import java.util.concurrent.CompletableFuture;

/**
 * An implementation that assumes that your service is running in the same VM as the code making the calls. It simply
 * uses reflection to invoke the proper method on the service.
 */
class LocalServiceBusDispatcher<T> extends ServiceBusDispatcherAdapter<T>
{
    /**
     * Locates the consumer for the service that's floating around in this VM somewhere (technically this class loader)
     * and feeds the request to it so that the desired work can be accomplished.
     * @param request The unit of work to be done
     * @return A future that resolves w/ the result of the operation
     */
    @Override
    public CompletableFuture<ServiceResponse> apply(ServiceRequest request)
    {
        return connected
            ? LocalServiceBusReceiver.lookup(request.getServiceType()).apply(request)
            : Futures.error(Utils.illegalState(("Service transport is not currently connected")));
    }

    /**
     * The consumer is on the hook for all "setup" tasks, so this just marks the producer as connected and moves on.
     * @return A future that resolves once all setup activities have been completed
     */
    @Override
    public CompletableFuture<ServiceBusDispatcher<T>> connect()
    {
        connected = true;
        return Futures.of(this);
    }

    /**
     * The consumer is on the hook for all "teardown" tasks, so this just marks the producer as not connected.
     * @return A future that resolves once all resources have been released
     */
    @Override
    public CompletableFuture<ServiceBusDispatcher<T>> disconnect()
    {
        connected = false;
        return Futures.of(this);
    }
}
