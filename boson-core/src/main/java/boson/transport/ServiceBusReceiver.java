package boson.transport;

import boson.services.ServiceRequest;
import boson.services.ServiceResponse;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * When you implement a service, it should be completely independent of the transport layer. It should accomplish the
 * work that the service intends and that's it. This bus consumer is something that can be composed with the service
 * instance to find work to do and transmit the results back to the caller. These consumer instances are transport
 * specific, but are only part of the initial config/setup of the VM/process, so you don't think about it during the
 * normal implementation of your service code.
 *
 * While implementations may vary in what it means to connect or disconnect, how threading works, etc, we enforce a
 * standard <code>apply(ServiceRequest)</code> function so that every implementation can be generic in how we use the
 * ServiceRequest to physically invoke the method on the target service object.
 */
public interface ServiceBusReceiver<T> extends Function<ServiceRequest, CompletableFuture<ServiceResponse>>
{
    /**
     * This is the service instance that the consumer is retrieving work for.
     * @return The actual service instance
     */
    T getService();

    /**
     * The service contract interface that the service implements which defines the operations it will expose.
     * @return The interface for the target service that defines our service methods
     */
    Class<T> getServiceContract();

    /**
     * @return The connection/threading/timeout settings for this service bus
     */
    ServiceBusConfig getConfig();

    /**
     * Fires up the service transport's communication channel so that it can start to receive requests and feed them
     * to the given service.
     * @param service The actual service instance that we're going to intercept work for
     * @return A future that completes with this receiver when the consumer is ready to take requests
     */
    CompletableFuture<ServiceBusReceiver<T>> connect(T service);

    /**
     * Stops the service's transport communication channel so that it no longer takes any new requests. This has no
     * effect on any requests that were started before asking to disconnect.
     * @return A future that completes with this receiver when the consumer stops taking requests.
     */
    CompletableFuture<ServiceBusReceiver<T>> disconnect();


    /**
     * Chaining support. Your service may implement any number of interfaces. This tells us which of those interfaces defines the
     * methods you want to expose to the rest of the application. For instance if your <code>SimpleFooService</code>
     * implemented the <code>FooService</code>, <code>Serializable</code>, <code>Closable</code>, and
     * <code>Observer</code>. You'd pass the <code>FooService</code> class because that's the one that defines the
     * exposed methods that should be available to remote processes to call.
     * @param serviceContract The service contract interface for the target service
     * @return this
     */
    ServiceBusReceiver<T> expose(Class<T> serviceContract);

    /**
     * Applies the configuration settings for this consumer. This should be done BEFORE attempting to connect as
     * these values tell the connect() method how and where to connect to.
     * @param config The configuration to apply
     * @return this
     */
    ServiceBusReceiver<T> config(ServiceBusConfig config);
}
