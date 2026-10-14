package com.willfp.eco.core.data.observation;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.concurrent.CancellationException;
/** Cancellation does not release executor capacity until the underlying read exits. */
public final class ReadControl {
 private final long deadline;
 private volatile boolean cancelled;
 private volatile Statement statement;
 public ReadControl(long deadlineNanos){deadline=deadlineNanos;}
 public void check(){if(cancelled||System.nanoTime()>=deadline)throw new CancellationException("Read deadline");}
 public void attach(Statement value)throws SQLException{statement=value;try{check();}catch(CancellationException e){value.cancel();throw e;}}
 public void detach(){statement=null;}
 public void cancel(){cancelled=true;var active=statement;if(active!=null)try{active.cancel();}catch(SQLException ignored){}}
}
