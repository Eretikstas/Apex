package app.aaps.plugins.sync.nsclientV3

import app.aaps.core.interfaces.nsclient.NSClientRepository
import app.aaps.core.interfaces.nsclient.StoreDataForDb
import app.aaps.core.interfaces.source.NSClientSource
import app.aaps.core.interfaces.sync.DataSyncSelector
import app.aaps.core.interfaces.sync.NsClient
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.keys.BooleanKey
import app.aaps.core.nssdk.localmodel.treatment.NSCarbs
import app.aaps.core.nssdk.localmodel.treatment.NSProfileSwitch
import app.aaps.core.nssdk.mapper.toNSTreatment
import app.aaps.plugins.sync.SyncStrings
import app.aaps.shared.tests.TestBaseWithProfile
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Our addition (docs/OUR_CHANGES.md). The family's only remote control is an iOS client that writes to Nightscout
 * (API v3, app "AAPSClient-iOS"): carbs as "Carb Correction" and a profile percentage as "Profile Switch" with the
 * whole profile inside and no insulin data. These are the records it really writes (made-up values), read the way
 * the master reads them. The master must take them when the user accepts carbs and profile switches from Nightscout,
 * and must not take them when he does not.
 */
class IosClientNsRecordsTest : TestBaseWithProfile() {

    @Mock lateinit var nsClientSource: NSClientSource
    @Mock lateinit var storeDataForDb: StoreDataForDb
    @Mock lateinit var nsClientRepository: NSClientRepository
    @Mock lateinit var nsClient: NsClient
    @Mock lateinit var dataSyncSelector: DataSyncSelector

    private lateinit var processor: NsIncomingDataProcessor

    private val carbsJson
        get() = """
        {"_id":"6b0098fec296caed2e3971ee","eventType":"Carb Correction","enteredBy":"AAPSClient-iOS","carbs":4,
         "app":"AAPSClient-iOS","date":${now - 60_000},"utcOffset":180,"identifier":"0ee76864-74c6-5c97-be05-6e5778dd2f3d",
         "srvModified":${now - 59_000},"srvCreated":${now - 59_000},"subject":"AdminToken","insulin":null}
        """.trimIndent()

    private val profileJson =
        "{\"basal\":[{\"timeAsSeconds\":0,\"time\":\"00:00\",\"value\":0.4},{\"timeAsSeconds\":3600,\"time\":\"01:00\",\"value\":0.35}]," +
            "\"target_high\":[{\"timeAsSeconds\":0,\"time\":\"00:00\",\"value\":6}],\"units\":\"mmol\"," +
            "\"carbratio\":[{\"timeAsSeconds\":0,\"time\":\"00:00\",\"value\":20}],\"sens\":[{\"timeAsSeconds\":0,\"time\":\"00:00\",\"value\":8.5}]," +
            "\"target_low\":[{\"timeAsSeconds\":0,\"time\":\"00:00\",\"value\":6}],\"timezone\":\"Europe/Minsk\"}"

    private val profileSwitchJson
        get() = """
        {"_id":"6b014dc9c296caed2e397610","app":"AAPSClient-iOS","date":${now - 60_000},"percentage":85,"timeshift":0,
         "profile":"TestProfile","duration":0,"enteredBy":"AAPSClient-iOS","eventType":"Profile Switch","utcOffset":180,
         "identifier":"514ab607-e9a9-583b-8cfc-245c31b23bd4","srvModified":${now - 59_000},"srvCreated":${now - 59_000},
         "subject":"AdminToken","carbs":null,"insulin":null,"profileJson":${quote(profileJson)}}
        """.trimIndent()

    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    @BeforeEach
    fun setUp() {
        whenever(nsClientSource.isEnabled()).thenReturn(true)
        whenever(nsClient.dataSyncSelector).thenReturn(dataSyncSelector)
        processor = NsIncomingDataProcessor(
            aapsLogger = aapsLogger, nsClientSource = nsClientSource, preferences = preferences, dateUtil = dateUtil,
            nsClient = nsClient, profileFunction = profileFunction, profileRepository = profileRepository,
            storeDataForDb = storeDataForDb, config = config, profileStoreProvider = { profileStoreProvider() },
            notificationManager = notificationManager, nsClientRepository = nsClientRepository
        )
    }

    @Test
    fun `the client's records are read as carbs and a profile switch`() {
        val carbs = carbsJson.toNSTreatment()
        assertThat(carbs).isInstanceOf(NSCarbs::class.java)
        assertThat((carbs as NSCarbs).carbs).isEqualTo(4.0)
        val ps = profileSwitchJson.toNSTreatment()
        assertThat(ps).isInstanceOf(NSProfileSwitch::class.java)
        assertThat((ps as NSProfileSwitch).percentage).isEqualTo(85)
        assertThat(ps.profileJson).isNotNull()
    }

    @Test
    fun `carbs from the client are stored when carbs from Nightscout are accepted`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptCarbs)).thenReturn(true)
        processor.processTreatments(listOf(checkNotNull(carbsJson.toNSTreatment())), doFullSync = false)
        verify(storeDataForDb).addToCarbs(argThat { amount == 4.0 && timestamp == now - 60_000 })
    }

    @Test
    fun `a profile switch from the client is stored when profile switches from Nightscout are accepted`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptProfileSwitch)).thenReturn(true)
        processor.processTreatments(listOf(checkNotNull(profileSwitchJson.toNSTreatment())), doFullSync = false)
        // No insulin data in the record: the running profile's insulin is used, as for any switch from Nightscout.
        verify(storeDataForDb).addToProfileSwitches(argThat { percentage == 85 && profileName == "TestProfile" && basalBlocks.isNotEmpty() })
    }

    @Test
    fun `nothing from the client is stored when Nightscout data is not accepted (or hardened mode is on)`() {
        // Hardened mode answers false for these keys (PreferencesImpl), the same as switched off.
        whenever(preferences.get(BooleanKey.NsClientAcceptCarbs)).thenReturn(false)
        whenever(preferences.get(BooleanKey.NsClientAcceptProfileSwitch)).thenReturn(false)
        processor.processTreatments(listOf(checkNotNull(carbsJson.toNSTreatment()), checkNotNull(profileSwitchJson.toNSTreatment())), doFullSync = false)
        verify(storeDataForDb, never()).addToCarbs(any())
        verify(storeDataForDb, never()).addToProfileSwitches(any())
    }

    @Test
    fun `a skipped record of the client raises one notice, telling which setting stops it`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptCarbs)).thenReturn(false)
        whenever(preferences.hardenedMode).thenReturn(false)
        val carbs = checkNotNull(carbsJson.toNSTreatment())
        assertThat(carbs.app).isEqualTo("AAPSClient-iOS")
        processor.processTreatments(listOf(carbs), doFullSync = false)
        processor.processTreatments(listOf(carbs), doFullSync = false)
        // Twice the same minute: one notice only.
        verify(notificationManager, times(1)).post(
            id = eq(NotificationId.NS_IOS_CLIENT_SKIPPED), textRef = eq(SyncStrings.ios_client_skipped_off), level = any(), validMinutes = eq(120),
            date = any(), validTo = any(), sound = anyOrNull(), actions = any(), validityCheck = anyOrNull()
        )
    }

    @Test
    fun `in hardened mode the notice says so`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptProfileSwitch)).thenReturn(false)
        whenever(preferences.hardenedMode).thenReturn(true)
        processor.processTreatments(listOf(checkNotNull(profileSwitchJson.toNSTreatment())), doFullSync = false)
        verify(notificationManager).post(
            id = eq(NotificationId.NS_IOS_CLIENT_SKIPPED), textRef = eq(SyncStrings.ios_client_skipped_hardened), level = any(), validMinutes = eq(120),
            date = any(), validTo = any(), sound = anyOrNull(), actions = any(), validityCheck = anyOrNull()
        )
    }

    @Test
    fun `a skipped record of another app raises no notice`() {
        whenever(preferences.get(BooleanKey.NsClientAcceptCarbs)).thenReturn(false)
        processor.processTreatments(listOf(checkNotNull(carbsJson.replace("AAPSClient-iOS", "AAPS").toNSTreatment())), doFullSync = false)
        verify(notificationManager, never()).post(
            id = eq(NotificationId.NS_IOS_CLIENT_SKIPPED), textRef = any(), level = any(), validMinutes = any(),
            date = any(), validTo = any(), sound = anyOrNull(), actions = any(), validityCheck = anyOrNull()
        )
    }
}
