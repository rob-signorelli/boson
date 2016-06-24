package boson.examples.services;

import java.util.concurrent.CompletableFuture;

/**
 * A service that generates random bits of data.
 */
public interface RandomService
{
    /**
     * Generates a random string of letters of the given length.
     * @return A future string value of the given length
     */
    CompletableFuture<String> randomLetters(int length);

    /**
     * Generates a random integer between two values
     * @return The future random value
     */
    CompletableFuture<Integer> randomInt(int lower, int upper);
}
