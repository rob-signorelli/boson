package boson.services;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

/**
 * Encapsulates all of the information required to invoke a remote method on a service. Different communication
 * schemes may not require all of the fields we have available, but this should be good enough for most implemented schemes.
 */
public class ServiceResponse implements Serializable
{
    private static final long serialVersionUID = 1L;

    private UUID id;
    private Object result;
    private Throwable error;
    private String correlation;
    private Instant expires;
    private String serviceInfo;

    public ServiceResponse(ServiceRequest request)
    {
        id = request.getId();
        correlation = request.getCorrelation();
    }

    /**
     * This is the unique identifier of the response. This should match the id of the request that it is paired to.
     * @return The request's/response's id
     */
    public UUID getId() { return id; }

    /**
     * Assuming the service responded successfully to the request invocation, this is the final result that it calculated.
     * @return The return value of the service call
     */
    public Object getResult() { return result; }

    /**
     * When the service successfully finishes its method call, this is its return value. This should also be the
     * resolution value of the original caller's future.
     * @param result The result of the operation
     */
    public void setResult(Object result) { this.result = result; }

    /**
     * If the operation failed, this is the exception that was thrown so that it can be propagated back to the caller.
     * @return The exception thrown by the service while invoking the method
     */
    public Throwable getError() { return error; }

    /**
     * Should the service operation fail due to some uncaught exception, this is the error that should be propagated back
     * to the original caller.
     * @param error The exception to propagate back to the caller
     */
    public void setError(Throwable error) { this.error = error; }

    /**
     * The shared correlation value between the original request and this response.
     * @return The correlation id/address.
     */
    public String getCorrelation() { return correlation; }

    /**
     * For transport types that require it, this is the shared correlation id/address that the caller can use to match
     * up the response to the request.
     * @param correlation The correlation id/address.
     */
    public void setCorrelation(String correlation) { this.correlation = correlation; }

    /**
     * The transport layer should purge this response if it has not been retrieved by this point in time.
     * @return A future UTC timestamp
     */
    public Instant getExpires() { return expires; }

    /**
     * This is only used by certain transport layers. It defines the point in time when we should purge this response
     * if the caller has not consumed this response in a timely fashion. The most common instance of this is if the
     * service took too long to respond so the caller has completely given up on the request/response - then the service
     * finally finishes the task but the caller is no longer listening. This ensures that the response doesn't hang
     * around forever.
     * @param expires A future UTC timestamp
     */
    public void setExpires(Instant expires) { this.expires = expires; }

    /**
     * This is debugging information that encodes whatever the transport thinks is useful.
     * @return A string containing debug info.
     */
    public String getServiceInfo() { return serviceInfo; }

    /**
     * This exists for debugging and logging/tracking purposes only. The service instance can encode information such
     * as the server name, timestamp, etc - whatever might be useful for logging/debugging code to better identify
     * the path that the request took to be serviced. It serves no actual transport purpose. It's simply useful if you
     * see an exception and need to look at the logs on the specific server that handled the request - or some similar use case.
     * @param serviceInfo The encoded server/processing info
     */
    public void setServiceInfo(String serviceInfo) { this.serviceInfo = serviceInfo; }

    /**
     * A quick check to see whether or not the request was successfully completed with a valid return value.
     * @return Did we NOT throw an exception?
     */
    public boolean isSuccess()
    {
        return error == null;
    }

    /**
     * A quick check to see whether or not the request threw an uncaught exception while evaluating.
     * @return Did the service result in an uncaught exception?
     */
    public boolean isFailure()
    {
        return !isSuccess();
    }

    /**
     * Marks the response in a way that it has completed successfully w/ the given return value
     * @param result The return value.
     * @return this
     */
    public ServiceResponse done(Object result)
    {
        setError(null);
        setResult(result);
        return this;
    }

    /**
     * Marks the response in a way that it failed due to the given error.
     * @param t The error to propagate back to the caller
     * @return this
     */
    public ServiceResponse fail(Throwable t)
    {
        setResult(null);
        setError(t);
        return this;
    }

    /**
     * @return The debugging string for this response
     */
    @Override
    public String toString()
    {
        return "ServiceResponse[" + getId() + "]";
    }
}
