package boson.services;

/**
 * The standard exception we throw when too much time elapses for some request and we give up on the request ever
 * actually coming back.
 */
public class ServiceOperationTimeout extends RuntimeException
{
    /**
     * Creates the exception using the standard error formatting
     * @param serviceName The name of the service where the operation failed
     * @param request The request that failed
     */
    public ServiceOperationTimeout(String serviceName, ServiceRequest request)
    {
        super(String.format("Call to [%s] timed out for request %s", serviceName, request));
    }
}
