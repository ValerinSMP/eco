package com.willfp.eco.core.data.handlers;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Immutable origin-captured task; no recapture when flushed or executed. */
public final class PreparedProfileWrite {
    private final PersistentDataHandler owner;
    private final Runnable persistence;
    private final AtomicBoolean claimed=new AtomicBoolean();
    private final CompletableFuture<Void> completion=new CompletableFuture<>();
    PreparedProfileWrite(PersistentDataHandler owner,Runnable persistence) {this.owner=owner;this.persistence=persistence;}
    public CompletionStage<Void> completion(){return completion.minimalCompletionStage();}
    public void supersede() {if(claimed.compareAndSet(false,true))completion.completeExceptionally(new CancellationException("Coalesced by a newer value"));}
    void dispatch(PersistentDataHandler dispatcher,Executor executor) {
        if(dispatcher!=owner)throw new IllegalArgumentException("Wrong profile handler");
        if(!claimed.compareAndSet(false,true))throw new IllegalStateException("Write already dispatched or superseded");
        try {executor.execute(()->{try{persistence.run();completion.complete(null);}catch(Throwable failure){completion.completeExceptionally(failure);if(failure instanceof Error error)throw error;}});}
        catch(RejectedExecutionException closed){completion.completeExceptionally(closed);throw closed;}
    }
}
