package com.willfp.eco.internal.spigot.data.observation
import com.willfp.eco.core.data.keys.PersistentDataKey
import com.willfp.eco.core.data.keys.PersistentDataKeyType
import com.willfp.eco.core.data.observation.ReadControl
import com.willfp.eco.internal.spigot.data.profiles.ProfileHandler
import com.willfp.eco.internal.spigot.data.profiles.impl.EcoPlayerProfile
import io.mockk.*
import org.bukkit.NamespacedKey
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.sql.DataSource
import java.sql.*
class NativeObservationTest {
 @Test fun missingProfileDoesNotCreateOrReadAnything() {
  val handler=mockk<ProfileHandler>();val uuid=UUID.randomUUID()
  every{handler.peekPlayerProfile(uuid)} returns null
  val result=NativeObservationAccess(handler).peek(uuid,emptySet())
  assertFalse(result.loaded());assertTrue(result.values().isEmpty())
  verify(exactly=1){handler.peekPlayerProfile(uuid)};confirmVerified(handler)
 }
 @Test fun realColdProfilePeeksNeverUseStorageWriterOrDefaults() {
  val handler=mockk<ProfileHandler>();val profile=EcoPlayerProfile(UUID.randomUUID(),handler)
  val key=mockk<PersistentDataKey<Int>>()
  assertTrue(profile.peekCachedValues(setOf(key)).isEmpty())
  assertTrue(profile.peekCachedValues(setOf(key)).isEmpty())
  verify(exactly=0){key.defaultValue};confirmVerified(handler)
 }
 @Test fun groupedSelectsRetainZerosAndDoNotWrite() {
  val pool=mockk<DataSource>();val connection=mockk<Connection>(relaxed=true)
  every{pool.connection} returns connection
  every{connection.autoCommit} returns true;every{connection.isReadOnly} returns false;every{connection.transactionIsolation} returns Connection.TRANSACTION_READ_COMMITTED
  val id=UUID.randomUUID();val keys= mutableSetOf<PersistentDataKey<*>>()
  for((type,suffix,value) in listOf(Triple(PersistentDataKeyType.INT,"int",0),Triple(PersistentDataKeyType.DOUBLE,"double",0.02),Triple(PersistentDataKeyType.BOOLEAN,"boolean",false))) {
   val key=mockk<PersistentDataKey<Any>>();val name=NamespacedKey("ecoskills",suffix)
   every{key.isLocal} returns false;every{key.type} returns type as PersistentDataKeyType<Any>;every{key.key} returns name;keys.add(key)
   val statement=mockk<PreparedStatement>(relaxed=true);val rows=mockk<ResultSet>(relaxed=true)
   every{connection.prepareStatement(match{it.contains("`eco_${suffix}`")})} returns statement
   every{statement.executeQuery()} returns rows;every{rows.next()} returnsMany listOf(true,false)
   every{rows.getBytes(1)} returns JdbcObservationReader.uuidBytes(id);every{rows.getString(2)} returns name.toString();every{rows.wasNull()} returns false
   if(value is Int)every{rows.getInt(3)} returns value
   if(value is Double)every{rows.getDouble(3)} returns value
   if(value is Boolean)every{rows.getBoolean(3)} returns value
   verify(exactly=0){statement.executeUpdate()}
  }
  val result=JdbcObservationReader(pool,"eco_").read(setOf(id),keys,ReadControl(System.nanoTime()+2_000_000_000))
  assertEquals(setOf(0,0.02,false),result.getValue(id).values.toSet())
  verify(exactly=3){connection.prepareStatement(match{it.startsWith("SELECT ")&&it.contains(" IN (?)")})}
  verify(exactly=0){connection.createStatement()};verify(exactly=0){connection.commit()}
  assertThrows(UnsupportedOperationException::class.java){(result as MutableMap<*,*>).clear()}
 }
 @Test fun invalidPrefixAndDeadlineFailBeforeConnection() {
  val pool=mockk<DataSource>();val control=ReadControl(System.nanoTime()-1)
  assertThrows(java.util.concurrent.CancellationException::class.java){JdbcObservationReader(pool,"eco_").read(setOf(UUID.randomUUID()),emptySet(),control)}
  assertThrows(UnsupportedOperationException::class.java){JdbcObservationReader(pool,"unsafe;").read(setOf(UUID.randomUUID()),emptySet(),control)}
  confirmVerified(pool)
 }
}
