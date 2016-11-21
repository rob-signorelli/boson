package boson.transport.serialize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;

/**
 * Describes a component that is capable of taking some Serializable object and turning into the raw bytes that we'll
 * send through the transport.
 */
public interface SerializationEngine
{
    // --- Serialize ---

    /**
     * Convert the given object into serialized bytes
     * @param obj The POJO to convert into raw bytes
     * @return The resulting bytes
     */
    byte[] objectToBytes(Serializable obj) throws IOException;

    /**
     * Converts the serializable object into a stream of bytes representing that object
     * @param obj The POJO to convert into raw bytes
     * @return The original POJO
     */
    default InputStream objectToStream(Serializable obj) throws IOException, ClassNotFoundException
    {
        // Standard Java serialization creates OutputStream instances when serializing object, not InputStreams. Given
        // the pain required to pipe an OutputStream to an InputStream, it's much faster to simply realize the entire
        // object's byte array in memory. Feel free to implement a version that uses threads and pipes should
        // you need to serialize giant blobs.
        byte[] bytes = objectToBytes(obj);
        return (bytes != null && bytes.length > 0)
            ? new ByteArrayInputStream(bytes)
            : null;
    }


    // --- Deserialize ---

    /**
     * Reverses the serialization process, taking the serialized bytes and turning it back into the original POJO
     * @param bytes The serialized bytes
     * @return The original POJO
     */
    <T> T bytesToObject(Class<T> type, byte[] bytes) throws IOException, ClassNotFoundException;

    /**
     * Reverses the serialization process, taking the serialized bytes and turning it back into the original POJO.
     * Implementations should close the given input stream upon completion of the operation, success or fail.
     * @param bytes The serialized bytes
     * @return The original POJO
     */
    <T> T streamToObject(Class<T> type, InputStream bytes) throws IOException, ClassNotFoundException;
}
