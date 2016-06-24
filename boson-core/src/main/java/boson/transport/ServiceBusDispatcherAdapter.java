package boson.transport;

/**
 * Manages the common book-keeping tasks required by most producer implementations.
 */
public abstract class ServiceBusDispatcherAdapter<T> implements ServiceBusDispatcher<T>
{
    protected Class<T> serviceContract;
    protected boolean connected;
    protected ServiceBusConfig config;

    /**
     * This is the type of service that this service bus is dispatching requests for
     * @return The interface for the target service that exposes the operations we can dispatch
     */
    @Override
    public Class<T> getServiceContract() { return serviceContract; }

    /**
     * @return The configuration options for the service bus
     */
    @Override
    public ServiceBusConfig getConfig() { return config; }

    /**
     * Chaining support.  A given producer is responsible for dispatching requests for just one type of service. This
     * class is the primary "service contract" interface that defines what operations are exposed by the service and can be
     * dispatched by this service bus.
     * @param serviceContract The primary service interface
     * @return this
     */
    @Override
    public ServiceBusDispatcher<T> expose(Class<T> serviceContract)
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
    public ServiceBusDispatcher<T> config(ServiceBusConfig config)
    {
        this.config = config;
        return this;
    }

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
     * Builds the string used in logging/debugging that encodes the transport type and service name
     * @return A string like "HttpServiceBusProducer[FooService]"
     */
    @Override
    public String toString()
    {
        String serviceName = (getServiceContract() == null) ? "no-service" : getServiceContract().getSimpleName();
        return String.format("%s[%s]", getClass().getSimpleName(), serviceName);
    }

    /**
     * Results in true if the other object is another producer w/ the same transport type and underlying service contract.
     * @param o The other object to test
     * @return Are these two objects equivalent?
     */
    @Override
    public boolean equals(Object o)
    {
        if (o == null) return false;
        if (getClass() != o.getClass()) return false; // can't do instanceof b/c we don't know the exact type at compile time

        ServiceBusDispatcher<?> otherProducer = (ServiceBusDispatcher<?>)o;
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
