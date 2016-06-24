package boson.examples.services;

import java.util.concurrent.CompletableFuture;

/**
 * Implementation of the math service that handles basic arithmetic.
 */
public class SimpleMathService implements MathService
{
    /**
     * Adds 2 numbers. What do you think this does?
     * @param a One operand
     * @param b The other operand
     * @return The sum of "a+b"
     */
    @Override
    public CompletableFuture<Integer> add(int a, int b)
    {
        return CompletableFuture.completedFuture(a + b);
    }

    /**
     * Subtracts 2 numbers. What do you think this does?
     * @param a One operand
     * @param b The other operand
     * @return The sum of "a-b"
     */
    @Override
    public CompletableFuture<Integer> sub(int a, int b)
    {
        return CompletableFuture.completedFuture(a - b);
    }
}
