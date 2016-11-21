package boson.transport.serialize;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

/**
 * Describes a component that is capable of taking some Serializable object and turning into the raw bytes that we'll
 * send through the transport.
 */
public interface SerializationEngine
{
    /**
     * Convert the given object into serialized bytes
     * @param obj The POJO to convert into raw bytes
     * @return The resulting bytes
     */
    byte[] toBytes(Serializable obj) throws IOException;

    /**
     * Reverses the serialization process, taking the serialized bytes and turning it back into the original POJO
     * @param bytes The serialized bytes
     * @return The original POJO
     */
    <T> T fromBytes(byte[] bytes) throws IOException, ClassNotFoundException;

    /**
     * Reverses the serialization process, taking the serialized bytes and turning it back into the original POJO.
     * Implementations should close the given input stream upon completion of the operation, success or fail.
     * @param bytes The serialized bytes
     * @return The original POJO
     */
    <T> T fromBytes(InputStream bytes) throws IOException, ClassNotFoundException;
}
