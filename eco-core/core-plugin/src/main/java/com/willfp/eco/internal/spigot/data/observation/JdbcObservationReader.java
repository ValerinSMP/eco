package com.willfp.eco.internal.spigot.data.observation;
import com.willfp.eco.core.data.observation.*;
import com.willfp.eco.core.data.keys.*;
import javax.sql.DataSource;
import java.util.*;
import java.sql.*;
import java.nio.ByteBuffer;
/** MySQL native scalar tables; existing pool only. No SQL supplied by the caller. */
public final class JdbcObservationReader implements StorageReader {
 private final DataSource pool;
 private final String prefix;
 public JdbcObservationReader(DataSource pool,String prefix){this.pool=pool;this.prefix=prefix;}
 public static byte[] uuidBytes(UUID id){return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();}
 private static String slots(int size){return String.join(",",Collections.nCopies(size,"?"));}
 public Map<UUID,Map<PersistentDataKey<?>,Object>> read(Set<UUID> ids,Set<PersistentDataKey<?>> keys,ReadControl control)throws Exception{
  if(ids.isEmpty()||ids.size()>16||keys.size()>1024)throw new IllegalArgumentException("Read limit");
  if(!prefix.matches("[A-Za-z0-9_]*"))throw new UnsupportedOperationException("Unsupported table prefix");
  var output=new LinkedHashMap<UUID,Map<PersistentDataKey<?>,Object>>();for(var id:ids)output.put(id,new LinkedHashMap<>());
  var groups=new LinkedHashMap<String,Map<String,PersistentDataKey<?>>>();
  for(var key:keys){
   if(key.isLocal())throw new UnsupportedOperationException("Local backend");
   String type=key.getType()==PersistentDataKeyType.INT?"int":key.getType()==PersistentDataKeyType.DOUBLE?"double":key.getType()==PersistentDataKeyType.BOOLEAN?"boolean":null;
   if(type==null)throw new UnsupportedOperationException("Scalar type");
   String name=key.getKey().toString();if(name.length()>128)throw new IllegalArgumentException("Key length");
   groups.computeIfAbsent(type,x->new LinkedHashMap<>()).put(name,key);
  }
  control.check();
  try(var connection=pool.getConnection()){
   boolean auto=connection.getAutoCommit(),readOnly=connection.isReadOnly();int isolation=connection.getTransactionIsolation();
   try{
    connection.setReadOnly(true);connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);connection.setAutoCommit(false);
    for(var group:groups.entrySet()){
     control.check();
     String sql="SELECT profileUUID,dataKey,dataValue FROM `"+prefix+group.getKey()+"` WHERE profileUUID IN ("+slots(ids.size())+") AND dataKey IN ("+slots(group.getValue().size())+")";
     try(var statement=connection.prepareStatement(sql)){
      statement.setQueryTimeout(2);int index=1;for(var id:ids)statement.setBytes(index++,uuidBytes(id));for(var name:group.getValue().keySet())statement.setString(index++,name);
      control.attach(statement);
      try(var rows=statement.executeQuery()){
       int count=0;while(rows.next()){
        control.check();if(++count>ids.size()*group.getValue().size())throw new SQLException("Unexpected row count");
        byte[] raw=rows.getBytes(1);if(raw==null||raw.length!=16)throw new SQLException("Invalid UUID");
        var buffer=ByteBuffer.wrap(raw);var id=new UUID(buffer.getLong(),buffer.getLong());var key=group.getValue().get(rows.getString(2));
        if(!output.containsKey(id)||key==null)throw new SQLException("Unexpected row");
        Object value=switch(group.getKey()){case "int"->rows.getInt(3);case "double"->rows.getDouble(3);default->rows.getBoolean(3);};
        if(!rows.wasNull())output.get(id).put(key,value);
       }
      }finally{control.detach();}
     }
    }
    connection.rollback();
   }finally{
    try{connection.rollback();}finally{connection.setAutoCommit(auto);connection.setTransactionIsolation(isolation);connection.setReadOnly(readOnly);}
   }
  }
  var frozen=new LinkedHashMap<UUID,Map<PersistentDataKey<?>,Object>>();output.forEach((id,value)->frozen.put(id,Collections.unmodifiableMap(new LinkedHashMap<>(value))));return Collections.unmodifiableMap(frozen);
 }
}
