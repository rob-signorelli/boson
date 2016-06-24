package boson.examples.services;

import java.util.Random;
import java.util.concurrent.CompletableFuture;

/**
 * A service that generates random bits of data.
 */
public class SimpleRandomService implements RandomService
{
    private Random random = new Random();

    /**
     * Generates a random string of letters of the given length.
     * @return A future string value of the given length
     */
    @Override
    public CompletableFuture<String> randomLetters(int length)
    {
        // Being lazy and assuming length is >= 0
        StringBuilder text = new StringBuilder(length);
        for (int i = 0; i < length; i++)
            text.append((char)(random.nextInt(26) + 'a'));

        return CompletableFuture.completedFuture(text.toString());
    }

    /**
     * Generates a random integer between two values
     * @return The future random value
     */
    @Override
    public CompletableFuture<Integer> randomInt(int lower, int upper)
    {
        // Being lazy and assuming that upper >= lower
        return CompletableFuture.completedFuture(random.nextInt((upper - lower) + 1) + lower);
    }
}
