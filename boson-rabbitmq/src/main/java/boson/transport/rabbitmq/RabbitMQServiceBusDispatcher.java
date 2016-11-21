package boson.transport.rabbitmq;

import boson.Futures;
import boson.Utils;
import boson.services.ServiceRequest;
import boson.services.ServiceResponse;
import boson.services.ServiceResponseRouter;
import boson.transport.ServiceBusDispatcher;
import boson.transport.ServiceBusDispatcherAdapter;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.ShutdownSignalException;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * A dispatcher implementation that puts requests in a request queue that resides on a RabbitMQ message broker. Remote
 * service instances can pick up the work and respond using a dedicated queue that only handles responses for *this*
 * dispatcher.
 */
class RabbitMQServiceBusDispatcher<T> extends ServiceBusDispatcherAdapter<T>
{
    private static Logger logger = Utils.logger(RabbitMQServiceBusDispatcher.class);

    private RabbitMQClient queueClient;
    private boolean connected;
    private ServiceResponseRouter responseRouter;
    private Thread reaperThread;

    /**
     * Dispatches a request across the message queue for a remote service to handle, providing a future that completes
     * when the response has been received and routed back to us.
     * @param request The request to put in the message queue
     * @return A future that completes when the work has been completed and sent back by the remote service
     */
    @Override
    public CompletableFuture<ServiceResponse> apply(ServiceRequest request)
    {
        try
        {
            if (logger.isTraceEnabled())
                logger.trace("[{}] Writing request", getServiceName());

            // Make sure the service that picks up the request knows how to get the response back to us. We provide
            // the auto-generated unique name of the response queue only we have access to so whoever picks up the request
            // can write the response here and rest assured that this process and only this process will receive it.
            request.setCorrelation(queueClient.getQueueConsumerName());

            // Create the future BEFORE dispatching the call. This ensures that we don't accidentally hit a very rare
            // race condition where this thread yields right after the dispatch and then the remote service responds so
            // quickly that the future for the request hasn't even been created yet. That would suck, so create the
            // pending future first.
            CompletableFuture<ServiceResponse> response = responseRouter.createRoute(request);

            BasicProperties properties = new BasicProperties.Builder()
                .correlationId(request.getId().toString())
                .replyTo(request.getCorrelation())
                .expiration(String.valueOf(getConfig().getRequestTimeToLive().toMillis()))    // for whatever reason RabbitMQ use a string instead of a long
                .build();

            // Request queue name is the fully qualified service contract interface, not just the simple name
            byte[] requestBytes = config.getSerializationEngine().objectToBytes(request);
            queueClient.getChannel().basicPublish("", getServiceContract().getName(), properties, requestBytes);
            return response;
        }
        catch (IOException e)
        {
            logger.error("[{}] Unable to enqueue message '{}'", getServiceName(), request.getId());
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates connections to the message broker as well as creating the response queue that we'll use to receive
     * responses from remote services.
     * @return A future that completes when we've opened all necessary connection resources
     */
    @Override
    public CompletableFuture<ServiceBusDispatcher<T>> connect()
    {
        logger.info("Connecting to message broker for service '{}' at '{}'", getServiceName(), getConfig().getUri());

        return Futures.of(queueClient = new RabbitMQClient())
            .thenCompose(client -> client.connect(getServiceContract(), getConfig()))
            .thenCompose(client -> client.consumeResponses())
            .thenApply(client -> {
                responseRouter = new ServiceResponseRouter(config.getThreadPool()).named(getServiceName());
                connected = true;

                // We have 2 daemon threads associated w/ this dispatcher: one that receives/routes responses that
                // come in from the response queue and another that cancels expired requests.
                getConfig().getThreadPool().execute(() -> watchResponseQueue());
                getConfig().getThreadPool().execute(() -> expiredRequestReaper());
                return this;
            });
    }

    /**
     * Releases our connection to the message broker, disconnecting all channels and consumers.
     * @return A future that resolves when everything has been disconnected
     */
    @Override
    public CompletableFuture<ServiceBusDispatcher<T>> disconnect()
    {
        logger.info("Disconnecting from message broker for service '{}'", getServiceName());
        connected = false;
        return queueClient.close()
            .thenCompose(client -> Utils.interrupt(reaperThread))
            .thenApply(vd -> this);
    }


    /// ----- BACKGROUND DAEMON WORKER METHODS ------------------------------

    /**
     * DAEMON. All responses to calls made by this caller will be sent to the same temporary queue that we own. This
     * loop's one and only job is to constantly pull a response from the head of the queue and route it to
     * the request that's waiting for it to finish.
     */
    private void watchResponseQueue()
    {
        while (connected)
        {
            try
            {
                byte[] responseBytes = queueClient.getQueueConsumer().nextDelivery().getBody();

                if (logger.isTraceEnabled())
                    logger.trace("[{}] Response received, routing to correct future.", getServiceName());

                responseRouter.completeRoute(config.getSerializationEngine()
                    .bytesToObject(ServiceResponse.class, responseBytes));
            }
            catch (InterruptedException | ShutdownSignalException e)
            {
                // Probably disconnecting so politely let the next iteration and check for 'connected' fail to exit
            }
            catch (Throwable t)
            {
                String msg = String.format("[%s] Unable to dequeue message", getServiceName());
                logger.error(msg, t);
                Utils.sleep(250);   // in case we just lost all connection, don't dump thousands of stack traces to log per second
            }
        }
        logger.debug("[{}] Queue consumer thread shutting down", getServiceName());
    }

    /**
     * DAEMON. Have the request router dispose of all requests that have passed their expiration (i.e. it took too long
     * for a response to arrive). So we should update the caller's CompletableFuture w/ a timeout exception so that
     * code can fail gracefully.
     */
    private void expiredRequestReaper()
    {
        // Capture the current thread so we can interrupt the sleep on disconnect()
        reaperThread = Thread.currentThread();

        while (connected && responseRouter != null)
        {
            Utils.runQuietly(() -> responseRouter.cancelExpired());
            Utils.sleep(5000);
        }
    }
}
