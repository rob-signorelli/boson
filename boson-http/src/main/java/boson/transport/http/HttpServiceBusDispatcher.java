package boson.transport.http;

import boson.Futures;
import boson.Utils;
import boson.services.ServiceOperationTimeout;
import boson.services.ServiceRequest;
import boson.services.ServiceResponse;
import boson.transport.ServiceBusDispatcher;
import boson.transport.ServiceBusDispatcherAdapter;
import org.slf4j.Logger;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.concurrent.CompletableFuture;

/**
 * An implementation that assumes that your service is running in the same VM as the code making the calls. It simply
 * uses reflection to invoke the proper method on the service.
 */
class HttpServiceBusDispatcher<T> extends ServiceBusDispatcherAdapter<T> implements ServiceBusDispatcher<T>
{
    private static Logger logger = Utils.logger(HttpServiceBusDispatcher.class);

    private SSLSocketFactory sslSocketFactory;
    private boolean https;

    /**
     * Locates the consumer for the service that's floating around in this VM somewhere (technically this class loader)
     * and feeds the request to it so that the desired work can be accomplished.
     * @param request The unit of work to be done
     * @return A future that resolves w/ the result of the operation
     */
    @Override
    public CompletableFuture<ServiceResponse> apply(ServiceRequest request)
    {
        if (!connected)
            return Futures.error(new IllegalStateException("Service transport is not currently connected"));

        CompletableFuture<ServiceResponse> futureResult = new CompletableFuture<>();
        try
        {
            // HTTP I/O is blocking, so use a separate thread to handle the work
            getConfig().getThreadPool().execute(() -> dispatch(request, futureResult));
        }
        catch (Throwable t)
        {
            futureResult.completeExceptionally(new RuntimeException("Unable to spawn thread for request"));
        }
        return futureResult;
    }

    /**
     * BLOCKING CALL. Makes the HTTP post to the configured endpoint, feeding the given request to the remote service
     * to be processed and responded to.
     * @param request The request to send to the remote service implementation
     * @param future The future to mark as completed when the response has been received
     */
    private void dispatch(ServiceRequest request, CompletableFuture<ServiceResponse> future)
    {
        try
        {
            HttpURLConnection connection = createConnection(config.getUri().toURL());
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", "Boson-Service-Transport");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.5");

            // The body of the POST is just the serialized bytes of the ServiceRequest instance
            connection.setDoOutput(true);
            OutputStream postData = connection.getOutputStream();
            postData.write(Utils.serialize(request));
            postData.flush();
            postData.close();

            // Wait for a response and complete/fail accordingly
            int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode <= 299)
            {
                future.complete(Utils.deserialize(connection.getInputStream()));
            }
            else
            {
                future.completeExceptionally(new RuntimeException("HTTP transport error w/ status code " + responseCode));
            }
        }
        catch (SocketTimeoutException timeout)
        {
            logger.error("[{}] Request connection timeout - giving up on response for '{}'", getServiceName(), request);
            future.completeExceptionally(new ServiceOperationTimeout(getServiceName(), request));
        }
        catch (Throwable t)
        {
            logger.error("Totally unexpected HTTP transport exception", t);
            future.completeExceptionally(t);
        }
    }

    /**
     * Open a socket connection to the given URL
     * @param url The endpoint to connect to
     * @return The newly opened connection
     */
    protected HttpURLConnection createConnection(URL url) throws IOException
    {
        HttpURLConnection connection = (HttpURLConnection)url.openConnection();
        if (https)
        {
            ((HttpsURLConnection)connection).setSSLSocketFactory(sslSocketFactory);
        }
        return connection;
    }

    /**
     * Http connections are set up at invocation time so there's not much to do here other than mark ourselves as connected.
     * @return A future that resolves once all setup activities have been completed
     */
    @Override
    public CompletableFuture<ServiceBusDispatcher<T>> connect()
    {
        return Futures.supply(() -> {
            https = config.getUri().getScheme().equals("https");

            // This socket factory will be used for every connection we create
            sslSocketFactory = https && config.isSelfSignedCertificate()
                ? createTrustAllSocketFactory()
                : HttpsURLConnection.getDefaultSSLSocketFactory();

            connected = true;
            return this;
        });
    }

    /**
     * Since there are no persistent connections/resources to clean up, this just marks the producer as not-connected.
     * @return A future that resolves once this can no longer dispatch requests
     */
    @Override
    public CompletableFuture<ServiceBusDispatcher<T>> disconnect()
    {
        return Futures.supply(() -> {
            connected = false;
            sslSocketFactory = null;
            return this;
        });
    }

    // --------------- HTTPS Support ---------------------------------

    /**
     * This creates a socket factory which will accept certificates signed by anyone, even yourself. It enables you
     * to use self-signed certificates for the HTTPS transport.
     * @return A newly created socket factory
     */
    protected SSLSocketFactory createTrustAllSocketFactory()
    {
        try
        {
            TrustManager trustManager = new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                public void checkServerTrusted(X509Certificate[] certs, String authType) { }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, new TrustManager[]{ trustManager }, new SecureRandom());
            return sc.getSocketFactory();
        }
        catch (KeyManagementException | NoSuchAlgorithmException e)
        {
            throw new RuntimeException(e);
        }
    }
}
