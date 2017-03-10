package boson.transport.http;

import boson.Futures;
import boson.Utils;
import boson.services.ServiceRequest;
import boson.services.ServiceResponse;
import boson.transport.ServiceBusReceiver;
import boson.transport.ServiceBusReceiverAdapter;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerRequest;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/**
 * This service bus implementation uses a simple HTTP transport to receive work requests. It runs a small jetty
 * web server that receives POST requests containing service operation requests, responding to the caller back over
 * the same HTTP connection.
 */
class HttpServiceBusReceiver<T> extends ServiceBusReceiverAdapter<T>
{
    private static Logger logger = Utils.logger(HttpServiceBusReceiver.class);

    /** The "vertical" factory used to create all Vertx-related components. */
    private Vertx vertx;
    /** The embedded web server that will receive incoming service requests as HTTP POSTs */
    private HttpServer httpServer;

    /**
     * Fires up the service transport's communication channel so that it can start to receive requests.
     * @param instance The actual service instance we're going to grab work for
     * @return A future that completes when the consumer is ready to take requests
     */
    @Override
    public CompletableFuture<ServiceBusReceiver<T>> connect(T instance)
    {
        if (instance == null) return Futures.error(Utils.illegalArg("Can't connect HttpServiceBusConsumer to null instance"));
        if (config == null) return Futures.error(Utils.illegalArg("Can't connect HttpServiceBusConsumer with null config"));
        if (serviceContract == null) return Futures.error(Utils.illegalArg("Can't connect HttpServiceBusConsumer with null service contract"));
        if (httpServer != null) return Futures.error(Utils.illegalState("HTTP server is already connected"));

        try
        {

            logger.info("Starting vert.x http server on port {}", config.getUri().getPort());
            service = instance;
            vertx = Vertx.vertx();
            httpServer = vertx
                .createHttpServer()
                .requestHandler(this::handleRequest)
                .listen(config.getUri().getPort());

            return Futures.of(this);
        }
        catch (Throwable t)
        {
            service = null;
            httpServer = null;
            return Futures.error(t);
        }
    }

    /**
     * Wrangles the asynchronous procedures to read the incoming Boson post, dispatches the
     * body's ServiceRequest to the implementing service, and responds back to the caller
     * with the serialized ServiceResponse.
     * @param httpRequest The HTTP request channel to communicate w/
     */
    protected void handleRequest(HttpServerRequest httpRequest)
    {
        // Every incoming boson request is "POST /"
        if (httpRequest.method() == HttpMethod.POST && httpRequest.uri().equals("/"))
        {
            // As data comes in, fill in the buffer w/ the request post data
            Buffer bodyBuffer = Buffer.buffer();
            httpRequest.bodyHandler(bodyBuffer::appendBuffer);
            httpRequest.handler(bodyBuffer::appendBuffer);

            // Once all of the incoming data is there, we can deserialize the boson
            // request and dispatch the call for real.
            httpRequest.endHandler(v -> Futures.of(bodyBuffer)
                .thenApply(this::toServiceRequest)
                .thenCompose(this::apply)
                .thenApply(this::toBuffer)
                .thenAccept(httpRequest.response()::end)
                .exceptionally(t -> {
                    t.printStackTrace();
                    httpRequest.response().setStatusCode(500).end();
                    return null;
                }));
        }
        else
        {
            httpRequest.response().setStatusCode(404).end();
        }
    }

    /**
     * Given the body of a Boson POST, reconstitute the ServiceRequest we're being
     * asked to dispatch and run.
     * @param postBody The raw body bytes
     * @return The reconstructed service request
     */
    protected ServiceRequest toServiceRequest(Buffer postBody)
    {
        return config.getSerializationEngine()
            .bytesToObject(ServiceRequest.class, postBody.getBytes());
    }

    /**
     * Use the serialization engine to convert the resulting service response into
     * a byte buffer to send back over the HTTP wire.
     * @param response The service call result to transmit back to the caller
     * @return The byte buffer
     */
    protected Buffer toBuffer(ServiceResponse response)
    {
        return Buffer.buffer(config.getSerializationEngine()
            .objectToBytes(response));
    }

    /**
     * Stops the service's transport communication channel so that it no longer takes any new requests. This has no
     * effect on any requests that were started before asking to disconnect.
     * @return A future that completes when the consumer stops taking requests.
     */
    @Override
    public CompletableFuture<ServiceBusReceiver<T>> disconnect()
    {
        try
        {
            // Neither implements Closable, so we need to do this to avoid try/catch
            Utils.runQuietly(() -> httpServer.close());
            Utils.runQuietly(() -> vertx.close());

            httpServer = null;
            vertx = null;
            service = null;
            return Futures.of(this);
        }
        catch (Exception e)
        {
            logger.error("Unable to cleanly stop HTTP server", e);
            return Futures.error(e);
        }
    }
}
