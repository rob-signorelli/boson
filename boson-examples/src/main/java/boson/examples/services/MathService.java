package boson.examples.services;

import java.util.concurrent.CompletableFuture;

/**
 * A simple service that does basic arithmetic.
 */
public interface MathService
{
    /**
     * Adds 2 numbers. What do you think this does?
     * @param a One operand
     * @param b The other operand
     * @return The sum of "a+b"
     */
    CompletableFuture<Integer> add(int a, int b);

    /**
     * Subtracts 2 numbers. What do you think this does?
     * @param a One operand
     * @param b The other operand
     * @return The sum of "a-b"
     */
    CompletableFuture<Integer> sub(int a, int b);
}
