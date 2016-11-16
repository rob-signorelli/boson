package boson;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Helpers that make life easier and cut down on the verbosity and fill in some glaring functionality gaps when
 * working with CompletableFuture instances.
 */
public class Futures
{
    /**
     * Generates and returns a new, already completed future that resolves w/ the given value.
     * @param value The value to resolve w/
     * @param <T> The resolution value type
     * @return The completed future
     */
    public static <T> CompletableFuture<T> of(T value)
    {
        return CompletableFuture.completedFuture(value);
    }

    /**
     * Generates and returns a new, already completed future that will fail "exceptionally" with the given throwable error.
     * @param t The error that the future will complete "exceptionally" with.
     * @param <T> The resolution value type
     * @return The exceptionally completed future
     */
    public static <T> CompletableFuture<T> error(Throwable t)
    {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(t);
        return future;
    }

    /**
     * BLOCKING CALL. Executes a <code>.get()</code> call on the given future. Any of the potential declared exceptions
     * that might be thrown (InterruptedException or ExecutionException) are quietly rethrown as RuntimeExceptions instead.
     * @param future The future you want to wait on for a result.
     * @param <T> The type of result
     * @return The final result of the future when it finally arrives.
     */
    public static <T> T await(CompletableFuture<T> future)
    {
        try
        {
            return future.get();
        }
        catch (InterruptedException | ExecutionException e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * BLOCKING CALL. Waits for all of the given futures to complete, completely ignoring their resulting values.
     * @param futures The futures to wait on a response for.
     */
    public static void awaitAll(CompletableFuture<?>... futures)
    {
        await(CompletableFuture.allOf(futures));
    }

    /**
     * Similar to <code>CompletableFuture.allOf()</code> except that instead of the resulting future being of type Void
     * you actually get the results of each operation back.
     * @param futures The source futures to wait on responses for
     * @param <T> The type of the result value
     * @return A future that completes w/ all of the result values in a nice list
     */
    @SafeVarargs
    public static <T> CompletableFuture<List<T>> all(CompletableFuture<T>... futures)
    {
        return all(Arrays.asList(futures));
    }

    /**
     * Similar to <code>CompletableFuture.allOf()</code> except that instead of the resulting future being of type Void
     * you actually get the results of each operation back.
     * @param futures The source futures to wait on responses for
     * @param <T> The type of the result value
     * @return A future that completes w/ all of the result values in a nice list
     */
    public static <T> CompletableFuture<List<T>> all(List<CompletableFuture<T>> futures)
    {
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()]));
        return all.thenApply(v -> futures
            .stream()
            .map(CompletableFuture::join)
            .collect(Collectors.toList()));
    }

    /**
     * Safely executes your supplier in a try/catch so that any uncaught exceptions result in an 'exceptional' future. Keep
     * in mind that this is SYNCHRONOUS, not async. If you want async then just simply use the standard 'CompletableFuture.supplyAsync()'
     * method.
     * @param supplier The work to produce the value to complete with
     * @return The completed future
     */
    public static <T> CompletableFuture<T> supply(Supplier<T> supplier)
    {
        try
        {
            return of(supplier.get());
        }
        catch (Throwable t)
        {
            return error(t);
        }
    }
}
