package boson.transport.rabbitmq;

import boson.Futures;
import boson.Utils;
import boson.transport.ServiceBusConfig;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * Represents all of the low-level queue/broker connections required by either the dispatcher or receiver side of the
 * message bus. Both sides have to connect to the broker and consume from one queue while writing to another. In the
 * case of the dispatcher it's writing to the "request" queue and reading from its personal "response" queue. For
 * receivers it's the exact opposite - they write to the response queues for each requester and read from the
 * shared "request" queue. As a result, most of the connection objects can be shared, so here you go. This client
 * manages the RabbitMQ connections and the dispatcher/receiver can simply worry about the logic required to send/receive
 * requests/responses and handle them appropriately.
 */
class RabbitMQClient
{
    private static Logger logger = Utils.logger(RabbitMQClient.class);

    private ConnectionFactory connectionFactory;
    private Channel channel;
    private Connection connection;
    private QueueingConsumer queueConsumer;
    private String queueConsumerName;
    private Class<?> serviceContract;

    /**
     * Sets up all of the common connections, channels, and consumers required to interact with our RPC-style messaging queues.
     * @param serviceContract The interface of the service we're interacting with (used to infer request queue name)
     * @param config Options like the URI of the broker and any auth credentials
     * @return A future that completes w/ this client once everything has been connected
     */
    public CompletableFuture<RabbitMQClient> connect(Class<?> serviceContract, ServiceBusConfig config)
    {
        try
        {
            this.serviceContract = serviceContract;
            connectionFactory = new ConnectionFactory();
            connectionFactory.setHost(config.getUri().getHost());
            connectionFactory.setPort(config.getUri().getPort());
            if (config.isAuthenticationPresent())
            {
                connectionFactory.setUsername(config.getUsername());
                connectionFactory.setPassword(config.getPassword());
            }
            connection = connectionFactory.newConnection();

            // Open a channel w/ the global REQUEST queue for this service (each service gets its own queue). Part of
            // doing this is to ensure that the request queue is created by the remote broker as well.
            channel = connection.createChannel();
            channel.queueDeclare(serviceContract.getName(), false, false, false, null);
            channel.basicQos(1);
            return Futures.of(this);
        }
        catch (Throwable t)
        {
            return Futures.error(t);
        }
    }

    /**
     * Closes all connections and channels associated with the message broker.
     * @return A future that completes when all of the resources have been released.
     */
    public CompletableFuture<RabbitMQClient> close()
    {
        connectionFactory = null;
        queueConsumer = null;
        channel = null;

        // Why you no implement Closeable?!!?!?!
        if (connection != null)
        {
            try { connection.close(); } catch (Exception ignore) {}
            connection = null;
        }

        return Futures.of(this);
    }

    /**
     * Should be called by a service bus receiver to create a consumer for the shared request queue for the
     * underlying service.
     * @return A future that resolves once we're ready to consume the queue
     */
    public CompletableFuture<RabbitMQClient> consumeRequests()
    {
        return consume(serviceContract.getName());
    }

    /**
     * Should be called by a dispatcher to both CREATE and consume a personal temporary queue that it can use to receive
     * responses targeted specifically for it.
     * @return A future that resolves once we're ready to consume the new queue
     */
    public CompletableFuture<RabbitMQClient> consumeResponses()
    {
        return consume(null);
    }

    /**
     * Implements the logic described by both consumeRequest() and consumeResponses(), connecting to the desired queue.
     * @param queueName The name of the queue to listen to.
     * @return A future that completes when we're ready to start consuming messages from the queue
     */
    private CompletableFuture<RabbitMQClient> consume(String queueName)
    {
        try
        {
            // If you leave the queue name empty, we'll create a new temporary queue and use its name. This is
            // what we'll do for the individual response queues. When this has a value, we'll assume that it's the
            // shared response queue so just consume that.
            queueConsumerName = Utils.hasValue(queueName)
                ? queueName
                : channel.queueDeclare().getQueue();

            if (logger.isTraceEnabled())
                logger.trace("Consuming queue '{}'", queueName);

            queueConsumer = new QueueingConsumer(channel);
            channel.basicConsume(queueConsumerName, true, queueConsumer);
            return Futures.of(this);
        }
        catch (Throwable t)
        {
            return Futures.error(t);
        }
    }

    /**
     * @return The communication channel we have open w/ the broker.
     */
    public Channel getChannel() { return channel; }

    /**
     * @return The handler for the message queue we are consuming messages from.
     */
    public QueueingConsumer getQueueConsumer() { return queueConsumer; }

    /**
     * @return The name of the queue that our consumer is pulling messages from.
     */
    public String getQueueConsumerName() { return queueConsumerName; }
}
