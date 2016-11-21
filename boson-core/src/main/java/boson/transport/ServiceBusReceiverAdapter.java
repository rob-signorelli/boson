package boson.transport;

import boson.Futures;
import boson.Utils;
import boson.services.ServiceRequest;
import boson.services.ServiceResponse;
import boson.services.Services;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;

/**
 * Manages the common book-keeping tasks required by most producer implementations.
 */
public abstract class ServiceBusReceiverAdapter<T> implements ServiceBusReceiver<T>
{
    protected T service;
    protected Class<T> serviceContract;
    protected ServiceBusConfig config;
    protected Services services;

    /**
     * This is the service instance that the consumer is retrieving work for.
     * @return The actual service instance
     */
    @Override
    public T getService() { return service; }

    /**
     * This is the type of service that this service bus is dispatching requests for
     * @return The interface for the target service that exposes the operations we can dispatch
     */
    @Override
    public Class<T> getServiceContract() { return serviceContract; }

    /**
     * @return The configuration settings for this service consumer
     */
    @Override
    public ServiceBusConfig getConfig() { return config; }

    /**
     * @return The repository/manager that this service is a member of
     */
    @Override
    public Services getServices() { return services; }

    /**
     * The display name of the service (the unqualified class name of the service contract)
     * @return The service name
     */
    public String getServiceName()
    {
        Class<T> contract = getServiceContract();
        return (contract == null) ? "no-service" : contract.getSimpleName();
    }

    /**
     * Applies the service contract interface for this service consumer.
     * @param serviceContract The service contract interface for the target service
     * @return this
     */
    @Override
    public ServiceBusReceiver<T> expose(Class<T> serviceContract)
    {
        this.serviceContract = serviceContract;
        return this;
    }

    /**
     * Applies the configuration settings for this consumer. This should be done BEFORE attempting to connect as
     * these values tell the connect() method how and where to connect to.
     * @param config The configuration to apply
     * @return this
     */
    @Override
    public ServiceBusReceiver<T> config(ServiceBusConfig config)
    {
        this.config = config;
        return this;
    }

    /**
     * Chaining support. A back-pointer to the service manager that holds the service registration for this receiver.
     * @param services The service manager/repository this service is a member of
     * @return this
     */
    @Override
    public ServiceBusReceiver<T> in(Services services)
    {
        this.services = services;
        return this;
    }


    // --------------------------------------------------------------------------------------------------------
    // Using reflection to invoke a method on an object should be relatively common regardless of the transport
    // mechanism you use. However you manage to receive the request, feed it here to perform the standard
    // invocation and construction of the response.
    // --------------------------------------------------------------------------------------------------------


    /**
     * Called any time this service receives a request to perform some operation. This will dispatch the operation to
     * the actual service instance, returning a future that will complete w/ the calculated value. That value will be
     * packaged up in a response so it can be sent back over the service bus.
     * @param request The request/method we want to invoke
     * @return A future that completes with the data to send back to the caller
     */
    @Override
    public CompletableFuture<ServiceResponse> apply(ServiceRequest request)
    {
        ServiceResponse response = new ServiceResponse(request);
        if (service == null)
            return Futures.of(response.fail(new IllegalStateException("Service is not connected.")));

        try
        {
            // Make sure the principal/authorization information is applied before physically invoking the method
            Utils.setContext(services, request.getContext());
            Method method = service.getClass().getMethod(
                request.getMethodName(),
                request.getArgumentTypes());

            return invoke(request, method).thenApply(response::done);
        }
        catch (Throwable t)
        {
            return Futures.of(response.fail(t));
        }
    }

    /**
     * Physically invokes the target method on the service this consumer is catching for.
     * @param request The service request we're servicing
     * @param method The actual method to invoke on the service
     * @return The future result that we want to package up and send back to the caller
     * @throws Throwable Any kind of error will be caught later and propagated back to the caller.
     */
    protected CompletableFuture<?> invoke(ServiceRequest request, Method method) throws Throwable
    {
        // The client-side proxy should verify this too, so we won't likely ever fail on this assert, but let's just be sure.
        Utils.assertAsync(service.getClass(), method);
        return (CompletableFuture<?>)method.invoke(service, request.getArguments());
    }

    /**
     * Builds the string used in logging/debugging that encodes the transport type and service name
     * @return A string like "HttpServiceBusConsumer[FooService]"
     */
    @Override
    public String toString()
    {
        String serviceName = (getServiceContract() == null) ? "no-service" : getServiceContract().getSimpleName();
        return String.format("%s[%s]", getClass().getSimpleName(), serviceName);
    }

    /**
     * Results in true if the other object is another consumer w/ the same transport type and underlying service contract.
     * @param o The other object to test
     * @return Are these two objects equivalent?
     */
    @Override
    public boolean equals(Object o)
    {
        if (o == null) return false;
        if (getClass() != o.getClass()) return false; // can't do instanceof b/c we don't know the exact type at compile time

        ServiceBusReceiver<?> otherProducer = (ServiceBusReceiver<?>)o;
        return getServiceContract() == otherProducer.getServiceContract();
    }

    /**
     * @return The hash code
     */
    @Override
    public int hashCode()
    {
        String serviceName = (getServiceContract() == null) ? "no-service" : getServiceContract().getName();
        return (getClass().getName() + ":" + serviceName).hashCode();
    }
}
