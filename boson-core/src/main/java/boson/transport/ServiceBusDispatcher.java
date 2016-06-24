package boson.transport;

import boson.services.ServiceRequest;
import boson.services.ServiceResponse;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Defines the standard interface used by all transports to dispatch the invocation of a service method across the
 * service bus to be picked up by some ServiceBusConsumer and processed.
 * @param <T> The type of service that we are dispatching operation requests for
 */
public interface ServiceBusDispatcher<T> extends Function<ServiceRequest, CompletableFuture<ServiceResponse>>
{
    /**
     * This is the type of service that this service bus is dispatching requests for
     * @return The interface for the target service that exposes the operations we can dispatch
     */
    Class<T> getServiceContract();

    /**
     * @return The connection/timing configuration settings for this service bus
     */
    ServiceBusConfig getConfig();

    /**
     * Performs any connection setup and resource allocation required to open a line of communication using this
     * transport mechanism. For instance in a message queue-based transport this may involve setting up a response
     * queue and discovering the request queue.
     * @return A future that completes w/ this dispatcher once all setup activities have been completed
     */
    CompletableFuture<ServiceBusDispatcher<T>> connect();

    /**
     * Releases all connections and resources associated with this transport. Doing this will prevent you from being
     * able to utilize this service any longer.
     * @return A future that completes w/ this dispatcher once all resources have been released
     */
    CompletableFuture<ServiceBusDispatcher<T>> disconnect();

    /**
     * Chaining support.  A given producer is responsible for dispatching requests for just one type of service. This
     * class is the primary "service contract" interface that defines what operations are exposed by the service and can be
     * dispatched by this service bus.
     * @param serviceContract The primary service interface
     * @return this
     */
    ServiceBusDispatcher<T> expose(Class<T> serviceContract);

    /**
     * Applies the configuration settings for this service bus. This should be done BEFORE attempting to connect as
     * these values tell the connect() method how and where to connect to.
     * @param config The configuration to apply
     * @return this
     */
    ServiceBusDispatcher<T> config(ServiceBusConfig config);
}
