package boson.transport.http;

import boson.Futures;
import boson.Utils;
import boson.services.ServiceRequest;
import boson.services.ServiceResponse;
import boson.transport.ServiceBusReceiver;
import boson.transport.ServiceBusReceiverAdapter;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.slf4j.Logger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;

/**
 * This service bus implementation uses a simple HTTP transport to receive work requests. It runs a small jetty
 * web server that receives POST requests containing service operation requests, responding to the caller back over
 * the same HTTP connection.
 */
class HttpServiceBusReceiver<T> extends ServiceBusReceiverAdapter<T>
{
    private static Logger logger = Utils.logger(HttpServiceBusReceiver.class);

    /** The embedded web server that will receive incoming service requests as HTTP POSTs */
    private Server httpServer;

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
            logger.info("Starting http server on port {}", config.getUri().getPort());
            service = instance;
            httpServer = new Server(new ExecutorThreadPool(config.getThreadPool()));

            /*
             * This is not a public-facing web server so we're not going to bother supporting HTTP & HTTPS at the same
             * time. You either want to communicate securely through this bus's transport or you don't, so only attach
             * the one correct connector for your configuration.
             */
            httpServer.addConnector(Utils.hasValue(config.getKeystorePath())
                ? createHttpsConnector()
                : createHttpConnector());

            httpServer.setHandler(new IncomingHttpRequestHandler());
            httpServer.start();
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
     * Factory to create the connector that accepts incoming HTTP connections (non-secure).
     * @return The constructed connector based on your configuration
     */
    protected ServerConnector createHttpConnector()
    {
        ServerConnector http = new ServerConnector(httpServer);
        http.setPort(config.getUri().getPort());
        http.setIdleTimeout(config.getRequestTimeToLive().toMillis());
        return http;
    }

    /**
     * Utilizes the keystore specified in your configuration to create a connector that accepts secure
     * incoming HTTPS connections.
     * @return The constructed connector based on your configuration
     */
    protected ServerConnector createHttpsConnector()
    {
        File keystoreFile = new File(config.getKeystorePath());
        if (!keystoreFile.exists())
            throw new IllegalStateException("Keystore not found found: " + config.getKeystorePath());

        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.addCustomizer(new SecureRequestCustomizer());

        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath(keystoreFile.getAbsolutePath());
        sslContextFactory.setKeyStorePassword(config.getKeystorePassword());
        ServerConnector sslConnector = new ServerConnector(httpServer,
            new SslConnectionFactory(sslContextFactory, "http/1.1"),
            new HttpConnectionFactory(httpConfig));

        sslConnector.setPort(config.getUri().getPort());
        return sslConnector;
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
            httpServer.stop();
            httpServer = null;
            service = null;
            return Futures.of(this);
        }
        catch (Exception e)
        {
            logger.error("Unable to cleanly stop HTTP server", e);
            return Futures.error(e);
        }
    }

    /**
     * This is a bridge that takes raw HTTP POST data, de-serializes it into a ServiceRequest, dispatches the service
     * operation, and packs the ServiceResponse as a standard HTTP response to the caller.
     */
    private class IncomingHttpRequestHandler extends AbstractHandler
    {
        /**
         * Asynchronous callback for our embedded web server that captures incoming HTTP POST requests and parses the
         * body information to determine the service request that it represents. It's then dispatched to the
         * consumer to invoke the service method as desired.
         * @param target The target URI that was requested
         * @param request The Jetty request data
         * @param httpRequest The abstracted servlet request data
         * @param httpResponse The abstracted servlet response we'll use to give the caller their result
         */
        @Override
        public void handle(String target,
                           Request request,
                           HttpServletRequest httpRequest,
                           HttpServletResponse httpResponse) throws IOException, ServletException
        {
            if ("POST".equalsIgnoreCase(httpRequest.getMethod()) && "/".equals(target))
            {
                try
                {
                    ServiceRequest serviceRequest = config.getSerializationEngine()
                        .streamToObject(ServiceRequest.class, httpRequest.getInputStream());

                    if (logger.isTraceEnabled())
                    {
                        logger.trace("HTTP service request received: {}.{}",
                            getServiceContract().getSimpleName(),
                            serviceRequest.getMethodName());
                    }

                    ServiceResponse serviceResponse = Futures.await(apply(serviceRequest));
                    respond(httpResponse, serviceResponse);
                }
                catch (Throwable t)
                {
                    logger.error("Uncaught HTTP transport exception", t);
                }
            }

            /*
             * This can be used by load balancer health checks. For instance you can set up a health check that lets
             * this instance stay in the server pool as long as "GET /ping" returns a 200. This doesn't return any content
             * to keep the response as lightweight as possible.
             */
            if ("GET".equalsIgnoreCase(httpRequest.getMethod()) && "/ping".equals(target))
            {
                httpResponse.setStatus(200);
                httpResponse.setContentLengthLong(0);
                request.setHandled(true);
            }
        }

        /**
         * Writes the response to the transport bus back to the caller
         * @param httpResponse Contains the output stream where we'll write the response bytes
         * @param serviceResponse The response encapsulation
         * @throws IOException If there are issues w/ the HTTP connection
         */
        protected void respond(HttpServletResponse httpResponse, ServiceResponse serviceResponse) throws IOException
        {
            // We're going to respond w/ 200 no matter what. Even failures should respond successfully over the
            // transport back to the caller. Analyzing the ServiceResponse should trigger failure on the client.
            OutputStream responseData = httpResponse.getOutputStream();
            httpResponse.setStatus(200);
            responseData.write(config.getSerializationEngine().objectToBytes(serviceResponse));
            responseData.flush();
            responseData.close();
        }
    }
}
