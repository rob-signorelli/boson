package boson.services;

import boson.Utils;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * In some transport implementations, there's not a 1-to-1 line of communication between the code making the request
 * and the code that completes the caller's future. This helps to correlate responses w/ their original requests and
 * the futures required to complete them. Note that not every transport will use this as the mechanism to locate the
 * future associated w/ a pending request.
 */
public class ServiceResponseRouter
{
    private static Logger logger = Utils.logger(ServiceResponseRouter.class);

    /** Tracks the requests that are still waiting for a response (correlation id to the pending request) */
    private Map<UUID, PendingRequest> pendingRequests;
    /** When we receive a response this executor will be used to complete futures off of the main broker thread */
    private Executor threadPool;
    /** For debugging purposes, this is the name of the service that we're routing for */
    private String serviceName;

    /**
     * Creates a new router. As the router completes futures for pending requests w/ incoming responses it will use the
     * given executor to supply threads for the remaining request work to be completed.
     * @param threadPool The executor that provides threads for completing futures.
     */
    public ServiceResponseRouter(Executor threadPool)
    {
        this.threadPool = threadPool;
        this.pendingRequests = new HashMap<>(1024);
    }

    /**
     * Creates a route for the given request such that when the corresponding/correlated response arrives we know to
     * complete the future associated w/ this request.
     * @param request The request we want completed
     * @return A future that will complete/fail when we receive or give up on its response
     */
    public CompletableFuture<ServiceResponse> createRoute(ServiceRequest request)
    {
        PendingRequest pendingRequest = new PendingRequest();
        pendingRequest.request = request;
        pendingRequest.future = new CompletableFuture<>();

        pendingRequests.put(request.getId(), pendingRequest);
        return pendingRequest.future;
    }

    /**
     * Routes the response to the pending request that is waiting on this response. This will complete the future
     * associated with the original request
     * @param response The response we're routing back to the original request/future
     */
    public void completeRoute(ServiceResponse response)
    {
        // The request and response share an 'id' so use that to find the future that completes it. [insert Jerry Maguire reference]
        PendingRequest pending = (response == null) ? null : pendingRequests.remove(response.getId());
        if (pending != null)
        {
            if (logger.isTraceEnabled())
                logger.trace("[{}] Routing response {}", serviceName, response);

            threadPool.execute(() -> pending.future.complete(response));
        }
        else
        {
            logger.warn("[{}] No pending request for response {}", serviceName, response);
        }
    }

    /**
     * Chaining support. Applies the name of the service that we're routing responses for.
     * @param name The name of the target service
     * @return this
     */
    public ServiceResponseRouter named(String name)
    {
        this.serviceName = name;
        return this;
    }

    /**
     * Finds all requests that have missed their window for receiving a response and cancels them. This involves
     * removing them from the waiting list as well as cancelling their pending futures.
     */
    public void cancelExpired()
    {
        // NOTE: We collect all expired items into a separate collection to avoid concurrent modification exceptions
        //       when we purge each item, removing it from the map.
        logger.trace("Canceling expired requests");
        pendingRequests.values()
            .stream()
            .filter(pending -> pending.request.isExpired())
            .collect(Collectors.toList())
            .forEach(pending -> cancel(pending.request));

    }

    /**
     * Cancels the given request if it's still currently awaiting a response. The future associated w/ the request will
     * be completed "exceptionally" with a ServiceOperationTimeout.
     * @param request The request we are cancelling
     */
    public void cancel(ServiceRequest request)
    {
        try
        {
            if (request != null)
            {
                PendingRequest pendingRequest = pendingRequests.remove(request.getId());
                if (pendingRequest != null && !pendingRequest.future.isDone())
                {
                    threadPool.execute(() -> pendingRequest.future.completeExceptionally(new ServiceOperationTimeout(serviceName, request)));
                }
            }
        }
        catch (Throwable t)
        {
            // Unlikely to happen ever but just in case we don't want to kill the thread dedicated to keeping our
            // set of pending requests clean.
            logger.error(String.format("[%s] Can't purge request", serviceName), t);
        }
    }

    /**
     * The data structure we use to link a request to the future that should be completed when the correlating
     * response finally arrives.
     */
    private class PendingRequest
    {
        public ServiceRequest request;
        public CompletableFuture<ServiceResponse> future;
    }
}
