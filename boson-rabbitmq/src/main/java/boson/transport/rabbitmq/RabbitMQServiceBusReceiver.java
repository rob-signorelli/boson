package boson.transport.rabbitmq;

import boson.Futures;
import boson.Utils;
import boson.services.ServiceRequest;
import boson.services.ServiceResponse;
import boson.transport.ServiceBusReceiver;
import boson.transport.ServiceBusReceiverAdapter;
import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.ShutdownSignalException;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * A service bus receiver that passes messages across various RabbitMQ message queues to perform RPC-style service
 * calls. This receiver implements a "Polling Consumer" algorithm to grab work from a single request queue that is
 * shared by all service implementations. When the work is done this will respond to the caller through their personal
 * response queues.
 */
class RabbitMQServiceBusReceiver<T> extends ServiceBusReceiverAdapter<T>
{
    private static Logger logger = Utils.logger(RabbitMQServiceBusReceiver.class);

    private boolean connected;
    private RabbitMQClient queueClient;

    /**
     * Connects to the RabbitMQ message broker and starts listening for requests on the shared request queue for
     * the target service.
     * @param service The actual service instance that we're going to intercept work for
     * @return A future that completes when the connection is complete and we're officially listening for requests
     */
    @Override
    public CompletableFuture<ServiceBusReceiver<T>> connect(T service)
    {
        logger.info("Connecting to message broker for service '{}' at '{}'",
            getServiceName(),
            getConfig().getUri());

        this.service = service;
        return Futures.of(queueClient = new RabbitMQClient())
            .thenCompose(client -> client.connect(getServiceContract(), getConfig()))
            .thenCompose(client -> client.consumeRequests())
            .thenApply(client -> {
                connected = true;
                // DAEMON THREAD. Poll the request queue for work requests, dispatching threads to handle them as we find them.
                getConfig().getThreadPool().execute(() -> incomingRequestListener());
                return this;
            });
    }

    /**
     * Stops listening for requests and cleans up every connection resource associated w/ the message broker.
     * @return A future that completes when we're completely disconnected and cleaned up
     */
    @Override
    public CompletableFuture<ServiceBusReceiver<T>> disconnect()
    {
        logger.info("Connecting to message broker for service '{}'", getServiceName());
        connected = false;
        return queueClient.close().thenApply(client -> this);
    }






    // ----- THIS STUFF SHOULD RUN IN A SEPARATE THREAD TO HANDLE/PROCESS INCOMING REQUESTS --------------------

    /**
     * Responsible for polling the requests queue for the target service, dispatching worker threads to invoke the service
     * method, and sending the resulting responses back to the caller via their personal response queues.
     */
    public void incomingRequestListener()
    {
        while (connected)
        {
            ServiceRequest request = readNextRequest();
            if (connected && request != null)
            {
                dispatchRequest(request);
            }
            else
            {
                // If something went wrong, wait a bit so that if we have a bad connection or something like that
                // we don't fail hundreds of times per second, absolutely destroying your logs w/ stack trace
                // after completely repetitive stack trace.
                Utils.sleep(250);
            }
        }
    }

    /**
     * BLOCKING CALL. That's how the RabbitMQ client library works... deal with it. Polls the shared request queue until
     * we are provided a message which contains the ServiceRequest outlining the work that needs to be done. This simply
     * retrieves and deserializes the raw message bytes. The 'dispatchRequest()' method actually handles the method
     * invocation and response handling.
     * @return The next request to process (should we ever receive one). This could be null (meaning no work) if interrupted for any reason.
     */
    private ServiceRequest readNextRequest()
    {
        try
        {
            if (logger.isTraceEnabled())
                logger.trace("[{}] Reading next request", getServiceName());

            return Utils.deserialize(queueClient.getQueueConsumer().nextDelivery().getBody());
        }
        catch (ShutdownSignalException e)
        {
            logger.warn("[{}] Interrupted due to Shutdown signal", getServiceName());
        }
        catch (InterruptedException e)
        {
            logger.warn("[{}] Interrupted unexpectedly", getServiceName());
        }
        catch (ClassNotFoundException | IOException e)
        {
            logger.error(String.format("[%s] Unable to deserialize incoming request bytes", getServiceName()), e);
        }
        return null;
    }

    /**
     * Given a unit of work to complete (i.e. the request), dispatch a thread from the pool to execute the service call.
     * When the work is complete we'll delegate to writeResponse() to send the completed result back to the caller. Keep
     * in mind that this call will exit quickly. This does NOT block and wait for the result to be calculated - it simply
     * fires off the separate thread to do the heavy lifting so we can go back to polling for the next request.
     * @param request The service work to complete
     */
    private void dispatchRequest(ServiceRequest request)
    {
        try
        {
            getConfig().getThreadPool().execute(() ->
                apply(request).thenAccept(this::writeResponse));
        }
        catch (Throwable t)
        {
            logger.error(String.format("[%s] Unable to fork off request thread", getServiceName()), t);
        }
    }

    /**
     * Use the correlation information to determine the proper response queue to write the response to.
     * @param response The result of the operation to send back to the caller
     */
    private void writeResponse(ServiceResponse response)
    {
        try
        {
            if (logger.isTraceEnabled())
                logger.trace("[{}] Writing response ''", getServiceName(), response);

            BasicProperties properties = new BasicProperties.Builder()
                .correlationId(response.getId().toString())
                .expiration(String.valueOf("60000"))    // No need to make configurable. If call hasn't picked up its response in a minute, fuck it.
                .build();

            queueClient.getChannel().basicPublish("", response.getCorrelation(), properties, Utils.serialize(response));
        }
        catch (Throwable t)
        {
            logger.error(String.format("[%s] Unable to write response to queue '%s'", getServiceName(), response), t);
        }
    }
}

