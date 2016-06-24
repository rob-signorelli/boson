package boson.services;

import boson.Futures;
import boson.Utils;
import boson.transport.ServiceBusConfig;
import boson.transport.ServiceBusDispatcher;
import boson.transport.ServiceBusReceiver;
import boson.transport.ServiceTransportBindings;
import org.slf4j.Logger;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

/**
 * This is a repository used to wrangle a set of services that a process both implements and consumes from other
 * remote hosts. Most of the interaction with this repository should happen during the startup phase of your application.
 */
public class Services
{
    private static Logger logger = Utils.logger(Services.class);

    /** A lookup for all of our services (proxies) that you can invoke from your client/caller code. */
    private Map<Class<?>, ConsumedService> consumedServices;
    /** A lookup for all service that this container has real implementations for */
    private Map<Class<?>, ServiceBusReceiver<?>> implementedServices;

    public Services()
    {
        consumedServices = new HashMap<>();
        implementedServices = new HashMap<>();
    }

    /**
     * Retrieves the service (proxy) for the given service type. You must have initialized the service proxy/connection
     * prior to this call using <code>use(serviceType, transport)</code> for this to work.
     * @param serviceType The type of service that you want to access
     * @param <T> The service type
     * @return A proxy to the service that will perform the work for you
     */
    @SuppressWarnings("unchecked")
    public <T> T get(Class<T> serviceType)
    {
        ConsumedService consumedService = consumedServices.get(serviceType);
        return (consumedService == null) ? null : (T)consumedService.serviceProxy;
    }

    /**
     * Initializes the given service so that you can start to perform operations on it. The service is likely some
     * remote process, but the given transport will ensure that the gory communication details are hidden from you
     * as you make service calls moving forward.
     *
     * It's up to you to ensure that the 'transport' that you provide for the proxy matches the transport used by
     * the implementing service on the other end. For instance if you use an HTTP transport here but the service
     * starts up w/ a message queue-based transport you're going to have a bad time.
     * @param serviceContract The service that you're going to activate (create a proxy for)
     * @param transport The communication transport to use w/ this service
     * @param config The configuration to pass to the new transport
     * @param <T> The type of service we're connecting to
     * @return A future for the newly "consumed" service instance (which is really just a proxy that uses the given transport)
     */
    @SuppressWarnings("unchecked")
    public <T> CompletableFuture<T> consume(
        Class<T> serviceContract,
        ServiceTransportBindings<T> transport,
        ServiceBusConfig config)
    {
        if (serviceContract == null) throw Utils.illegalArg("Unable to consume null service");
        if (transport == null) throw Utils.illegalArg("Unable to consume service %s over null transport", serviceContract);
        if (consumedServices.containsKey(serviceContract)) throw Utils.illegalArg("Service %s is already being consumed", serviceContract);

        logger.info("Creating proxy service {} -> {}", serviceContract, transport);
        return transport.dispatcher(serviceContract, config)
            .connect()
            .thenApply(dispatcher -> {
                ConsumedService consumedService = new ConsumedService();
                consumedService.dispatcher = dispatcher;
                consumedService.serviceProxy = Proxy.newProxyInstance(
                    serviceContract.getClassLoader(),
                    new Class[]{serviceContract},
                    new ServiceProxy<>(dispatcher));

                consumedServices.put(serviceContract, consumedService);
                return (T)consumedService.serviceProxy;
            });
    }

    /**
     * Connects the given service to the transport/consumer, putting it into a ready state so that it can start
     * receiving work requests.
     * @param service The service you want to make available
     * @param transport The communication transport type to listen on
     * @param <T> The service contract interface of the service
     * @return A future that resolves w/ the consumer when it has fully connected and is ready to receive requests
     */
    public <T> CompletableFuture<ServiceBusReceiver<T>> implement(
        Class<T> serviceContract,
        T service,
        ServiceTransportBindings<T> transport,
        ServiceBusConfig config)
    {
        // Make sure you're not doing something really, really stupid...
        if (service == null) throw Utils.illegalArg("Unable to implement null service");
        if (serviceContract == null) throw Utils.illegalArg("Unable to implement null service contract");
        if (transport == null) throw Utils.illegalArg("Unable to implement %s service over null transport", serviceContract);
        if (implementedServices.containsKey(serviceContract)) throw Utils.illegalState("%s is already implemented", serviceContract);

        logger.info("Implementing service {} -> {}", serviceContract, transport);
        return transport.receiver(serviceContract, config)
            .connect(service)
            .thenApply(receiver -> {
                implementedServices.put(receiver.getServiceContract(), receiver);
                return receiver;
            });
    }

    /**
     * Typically you'll call this during the shutdown phase of your application. This disconnects all hosted/implemented
     * services as well as any client proxies you fired up. This ensures that we cleanly release any resources.
     * @return A future that resolves once everything is shut down.
     */
    public CompletableFuture<Void> disconnectAll()
    {
        CompletableFuture<Void> implemented = Futures.all(implementedServices.values()
            .stream()
            .map(ServiceBusReceiver::disconnect)
            .collect(Collectors.toList()))
            .thenApply(voids -> null);    // don't need to propagate all void values... one suffices.

        CompletableFuture<Void> consumed = Futures.all(consumedServices.values()
            .stream()
            .map(service -> service.dispatcher.disconnect())
            .collect(Collectors.toList()))
            .thenApply(voids -> null);

        // Once we've shut down the transport make sure to 'shutdown()' the thread pools otherwise the process will hang.
        return CompletableFuture.allOf(implemented, consumed)
            .thenAccept(vd -> Utils.runQuietly(() -> getThreadPools()
                .stream()
                .filter(pool -> pool != ForkJoinPool.commonPool())    // lots of other things use this so don't fuck with fork/join
                .forEach(pool -> pool.shutdown())));
    }

    /**
     * Crawls through all of the implemented and consumed services to find all distinct thread pools used across all
     * services represented by this service bundle.
     * @return The unique thread pools across all services
     */
    private Set<ExecutorService> getThreadPools()
    {
        Set<ExecutorService> threadPools = new HashSet<>();

        implementedServices.values().stream()
            .map(svc -> svc.getConfig().getThreadPool())
            .forEach(threadPools::add);

        consumedServices.values().stream()
            .map(svc -> svc.dispatcher.getConfig().getThreadPool())
            .forEach(threadPools::add);

        return threadPools;
    }

    /**
     * A simple data structure that pairs a service proxy with the dispatcher that will send requests on its behalf. This
     * will allow us to look up a dispatcher so that we can disconnect it when we 'disconnectAll()' the Services object.
     */
    private class ConsumedService
    {
        public Object serviceProxy;
        public ServiceBusDispatcher<?> dispatcher;
    }
}
