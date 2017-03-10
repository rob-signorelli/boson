package boson.transport.serialize;

/**
 * A generic runtime exception signaling a failure of a serialization (or deserialization) operation.
 */
public class SerializationException extends RuntimeException
{
    public SerializationException(Throwable cause)
    {
        super(cause);
    }

    public SerializationException(String message, Throwable cause)
    {
        super(message, cause);
    }
}
