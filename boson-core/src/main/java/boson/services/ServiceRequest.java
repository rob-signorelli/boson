package boson.services;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Encapsulates all of the information required to invoke a remote method on a service. Different communication
 * schemes may not require all of the fields we have available, but this should be good enough for most implemented schemes.
 */
public class ServiceRequest implements Serializable
{
    private static final long serialVersionUID = 1L;

    private UUID id;
    private Class<?> serviceType;
    private String methodName;
    private Class<?>[] argumentTypes;
    private Object[] arguments;
    private String correlation;
    private Instant expires;

    public ServiceRequest()
    {
        id = UUID.randomUUID();
    }

    /**
     * The globally unique identifier which distinguishes this request from any other that flows through the service bus.
     * @return A non-null identifier
     */
    public UUID getId() { return id; }

    /**
     * The primary "service contract" interface that the target service is implementing and we're calling on.
     * @return The main interface implemented by the remote service
     */
    public Class<?> getServiceType() { return serviceType; }

    /**
     * Indicates the service that this request is bound for. This way when a remote VM/process looks at this request
     * it has all of the info it needs in order to route it to the correct service since a process can host multiple
     * services.
     * @param serviceType The primary service contract interface for this request
     */
    public void setServiceType(Class<?> serviceType) { this.serviceType = serviceType; }

    /**
     * The service we're calling on is represented by a POJO somewhere, so this indicates the method that should be
     * called on said service implementation POJO when it received the invocation request.
     * @return The name of the service method to invoke
     */
    public String getMethodName() { return methodName; }

    /**
     * Sets the name of the method on the implementing service that should be called when it receives this request.
     * @param methodName The name of the method to call.
     */
    public void setMethodName(String methodName) { this.methodName = methodName; }

    /**
     * The types of the arguments in the signature of the target service method.
     * @return The argument types of the target service method
     */
    public Class<?>[] getArgumentTypes() { return argumentTypes; }

    /**
     * The implementation of the service we're calling on is represented by a POJO somewhere, so these are the argument
     * types which help us to distinguish the proper method call if it's overloaded.
     * @param types The types in the signature of the target method
     */
    public void setArgumentTypes(Class<?>[] types) { this.argumentTypes = (types == null) ? new Class<?>[0] : types; }

    /**
     * These are the actual runtime values to pass to the target service method when it receives this request.
     * @return The runtime arguments to the service method.
     */
    public Object[] getArguments() { return arguments; }

    /**
     * Applies the runtime arguments to pass to the target service method when it's invoked via this request.
     * @param arguments The values to pass to the service method invocation
     */
    public void setArguments(Object[] arguments) { this.arguments = (arguments == null) ? new Object[0] : arguments; }

    /**
     * The additional correlation/linking information required by some communication schemes to properly route responses
     * back to the correct location/caller.
     * @return The correlation value (an id, address, or any other meaningful info for the transport)
     */
    public String getCorrelation() { return correlation; }

    /**
     * Some mechanisms like message queues and other asynchronous transports may not implicitly have enough information
     * to route a response to a service request back to the original caller. For instance if a service pulls a request
     * out of a shared work queue, a "correlation identifier" is used to share some minimal information required to
     * help the service properly respond in a manner that it is routed to the specific caller.
     *
     * If you're dealing with a shared response bucket, this could help all of the callers identify which responses
     * are theirs. If each requester has their own bucket/queue of responses, the correlation value could be the id
     * of the actual queue where its particular responses should be placed.
     *
     * For synchronous, persistent connections like an HTTP call, this isn't really necessary since you already have
     * a direct handle on the response mechanism, so it's not required for all transports.
     * @param correlation The correlation id/address.
     */
    public void setCorrelation(String correlation) { this.correlation = correlation; }

    /**
     * At what point should we consider this request invalid if it has not been handled by this time?
     * @return A UTC timestamp
     */
    public Instant getExpires() { return expires; }

    /**
     * This is a hack to avoid the halting problem. While your code should have a reasonable expectation that when it
     * makes a request that it will be handled and responded to, sometimes shit happens and services get backed up or
     * services crap out and fail to respond. This allows you to set an upper bound on how long you're willing to wait
     * for this request to receive a response before giving up.
     *
     * Should this time be exceeded, the caller will receive a <code>TimeoutException</code> indicating this failure.
     * Note that this does not automatically cancel any potential work in progress. For instance if you expect all of
     * your service calls to complete in 5 seconds but it takes 10, that work will still be done - the caller will just
     * get an error instead of the successful response. As a result, you need to strike a balance between how long your
     * code should be willing to wait and how long work is likely to take.
     * @param expires The UTC timestamp for when we should give up
     */
    public void setExpires(Instant expires) { this.expires = expires; }

    /**
     * Determines if this request has surpassed its window for how long it will wait to be completed.
     * @return Has this request expired?
     */
    public boolean isExpired()
    {
        return (expires != null) && Instant.now().isAfter(expires);
    }

    /**
     * Chaining support. Sets the correlation id/address for this request.
     * @param id The new value to apply
     * @return this
     */
    public ServiceRequest correlate(String id)
    {
        setCorrelation(id);
        return this;
    }

    /**
     * Chaining support. Sets the request expiration to occur after the given amount of time
     * @param duration The amount of time we have to complete the request
     * @return this
     */
    public ServiceRequest ttl(Duration duration)
    {
        setExpires(Instant.ofEpochMilli(duration.toMillis() + System.currentTimeMillis()));
        return this;
    }

    /**
     * Chaining support. Sets the info about the method to call on the target service
     * @param serviceType The service contract interface for the service we're calling on for work
     * @param m The method that we want to call on the remote service
     * @return this
     */
    public ServiceRequest call(Class<?> serviceType, Method m)
    {
        setServiceType(serviceType);
        setMethodName(m.getName());
        setArgumentTypes(m.getParameterTypes());
        return this;
    }

    /**
     * Chaining support. Applies the following runtime arguments
     * @param arguments The values to pass to the service method
     * @return this
     */
    public ServiceRequest args(Object[] arguments)
    {
        setArguments(arguments);
        return this;
    }

    /**
     * @return The debugging string for this request
     */
    @Override
    public String toString()
    {
        return "ServiceRequest[" + getId() + "]";
    }
}
