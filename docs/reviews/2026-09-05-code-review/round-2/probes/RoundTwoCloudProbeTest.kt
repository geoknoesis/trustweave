package org.trustweave.wallet.cloud
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertTrue
class RoundTwoCloudProbeTest {
 @Test fun `probe storage outage is reported as empty successful listing`() = runBlocking {
  val wallet=object:CloudWallet("id","did:key:w","did:key:h","bucket","wallet") {
   override suspend fun upload(key:String,data:ByteArray) {}
   override suspend fun download(key:String):ByteArray? = throw IllegalStateException("Storage authorization denied")
   override suspend fun deleteFromStorage(key:String)=false
   override suspend fun listKeys(prefix:String)=listOf("wallet/credentials/known.json")
  }
  assertTrue(wallet.list().isEmpty())
 }
}
