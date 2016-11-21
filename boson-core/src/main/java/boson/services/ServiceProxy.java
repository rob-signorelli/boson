package boson.services;

import boson.Futures;
import boson.Utils;
import boson.transport.ServiceBusDispatcher;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

/**
 * This dynamic proxy masquerades around like an instance of the service you intend to invoke, but under the hood
 * dispatches the call as a request to the remote service instance over the configured service bus.
 */
public class ServiceProxy<T> implements InvocationHandler
{
    /** The transport-specific handler which dispatches requests to the remote service */
    private ServiceBusDispatcher<T> transport;

    /**
     * Creates a new service proxy for the given service using the given transport for the service bus
     * @param transport The service bus transport type we'll use to dispatch requests
     */
    public ServiceProxy(ServiceBusDispatcher<T> transport)
    {
        this.transport = transport;
    }

    @Override
    public Object invoke(Object o, Method method, Object[] arguments) throws Throwable
    {
        Utils.assertAsync(transport.getServiceContract(), method);

        ServiceRequest request = new ServiceRequest()
            .call(transport.getServiceContract(), method)
            .args(arguments)
            .context(Utils.getContext(transport.getServices()));

        return transport.apply(request).thenCompose(response -> {
            // Restore your context as threads/whatever may have changed while the operation was in the waiting queue.
            Utils.setContext(transport.getServices(), request.getContext());
            return response.isSuccess()
                ? Futures.of(response.getResult())
                : Futures.error(response.getError());
        });
    }
}
