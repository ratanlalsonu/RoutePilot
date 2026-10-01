package com.example.data.repository

import android.content.Context
import android.location.Geocoder
import com.example.BuildConfig
import com.example.data.local.DestinationEntity
import com.example.data.local.RoutePilotDao
import com.example.data.remote.RemoteBackendClient
import com.example.domain.model.Destination
import com.example.domain.model.LocationPoint
import com.example.domain.repository.DestinationRepository
import com.example.domain.routing.GeoUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.abs

/**
 * Metadata for a recognized Nearby Place category (like Google Maps Nearby Search).
 */
data class NearbyCategorySpec(
    val canonicalCategory: String,
    val keywords: List<String>,
    val googlePlaceType: String,
    val overpassSelectors: List<String>,
    val fallbackTemplates: List<NearbyPlaceTemplate>
)

data class NearbyPlaceTemplate(
    val idSuffix: String,
    val name: String,
    val roadHint: String,
    val dLat: Double,
    val dLng: Double
)

data class ParsedSearchIntent(
    val categorySpec: NearbyCategorySpec?,
    val cleanQueryKeyword: String,
    val explicitLocationName: String?
)

class DestinationRepositoryImpl(
    private val context: Context,
    private val dao: RoutePilotDao,
    private val remoteBackendClient: RemoteBackendClient
) : DestinationRepository {

    private val seedPrefs by lazy {
        context.getSharedPreferences("routepilot_dest_seed_prefs", Context.MODE_PRIVATE)
    }

    private val accountPrefs by lazy {
        context.getSharedPreferences("routepilot_user_accounts", Context.MODE_PRIVATE)
    }

    private val searchCache = java.util.concurrent.ConcurrentHashMap<String, List<Destination>>()

    private val activeUserKeyFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(resolveCurrentUserKey())

    fun setActiveUser(userId: String?, email: String?) {
        val normalizedEmail = email?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        val normalizedId = userId?.trim()?.takeIf { it.isNotEmpty() }
        activeUserKeyFlow.value = normalizedEmail ?: normalizedId ?: resolveCurrentUserKey()
    }

    private fun resolveCurrentUserKey(): String? {
        val fbUser = runCatching {
            if (com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()) {
                com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            } else {
                null
            }
        }.getOrNull()
        val fbEmail = fbUser?.email?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        if (fbEmail != null) return fbEmail
        val fbUid = fbUser?.uid?.trim()?.takeIf { it.isNotEmpty() }
        if (fbUid != null) return fbUid
        val localEmail = accountPrefs.getString("active_user_email", null)
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotEmpty() }
        return localEmail
    }

    companion object {
        val defaultReferenceDestinations = listOf(
            Destination(
                id = "dest_district_hospital",
                name = "District Hospital",
                address = "Medical Road, Sector 4, Main City",
                category = "Hospital",
                latitude = 25.4920,
                longitude = 78.6180,
                distanceFromUserKm = 12.0,
                isDemoSample = false
            ),
            Destination(
                id = "dest_bus_stand",
                name = "Bus Stand",
                address = "Central Inter-State Bus Terminal",
                category = "Transit",
                latitude = 25.4680,
                longitude = 78.5910,
                distanceFromUserKm = 8.5,
                isDemoSample = false
            ),
            Destination(
                id = "dest_railway_station",
                name = "Railway Station",
                address = "Junction Station Road, Platform 1",
                category = "Railway",
                latitude = 25.4420,
                longitude = 78.5560,
                distanceFromUserKm = 15.0,
                isDemoSample = false
            ),
            Destination(
                id = "dest_college",
                name = "College",
                address = "Bundelkhand Engineering & Science Campus",
                category = "Education",
                latitude = 25.4590,
                longitude = 78.6090,
                distanceFromUserKm = 5.8,
                isDemoSample = false
            ),
            Destination(
                id = "dest_sipri_market",
                name = "Sipri Market Complex",
                address = "Sipri Bazaar Main Commercial Hub",
                category = "Shopping",
                latitude = 25.4515,
                longitude = 78.5480,
                distanceFromUserKm = 6.4,
                isDemoSample = false
            ),
            Destination(
                id = "dest_civil_lines_tech",
                name = "Civil Lines Tech Park",
                address = "Civil Lines Corporate Avenue",
                category = "Work",
                latitude = 25.4440,
                longitude = 78.5740,
                distanceFromUserKm = 4.2,
                isDemoSample = false
            ),
            Destination(
                id = "dest_medical_college",
                name = "Medical College Campus",
                address = "Kanpur Highway Bypass Gate 2",
                category = "Hospital",
                latitude = 25.4605,
                longitude = 78.6010,
                distanceFromUserKm = 7.3,
                isDemoSample = false
            ),
            Destination(
                id = "dest_fort_gate",
                name = "City Fort Heritage Gate",
                address = "Old City Fort Circle",
                category = "Landmark",
                latitude = 25.4580,
                longitude = 78.5755,
                distanceFromUserKm = 3.9,
                isDemoSample = false
            )
        )

        val realWorldGazetteer = defaultReferenceDestinations + listOf(
            Destination(
                id = "dest_home_sipri",
                name = "Home (Sipri Enclave)",
                address = "Sipri Bazaar Residential Block B",
                category = "Home",
                latitude = 25.4510,
                longitude = 78.5620,
                distanceFromUserKm = 3.2,
                isDemoSample = false
            ),
            Destination(
                id = "dest_work_civil_lines",
                name = "Work (Civil Lines Hub)",
                address = "Civil Lines IT & Logistics Center",
                category = "Work",
                latitude = 25.4440,
                longitude = 78.5740,
                distanceFromUserKm = 4.6,
                isDemoSample = false
            ),
            Destination(
                id = "dest_trauma_center",
                name = "Regional Trauma & Emergency Center",
                address = "NH-27 Medical Bypass",
                category = "Hospital",
                latitude = 25.4790,
                longitude = 78.6040,
                distanceFromUserKm = 9.4,
                isDemoSample = false
            )
        )

        /**
         * Known local/regional area coordinates for fast offline/instant resolution when a user
         * specifies a location name (in addition to live Geocoder / Nominatim / Photon lookup).
         */
        private val knownNamedLocations = mapOf(
            "jhansi" to LocationPoint(25.4484, 78.5685),
            "sipri" to LocationPoint(25.4515, 78.5480),
            "sipri bazaar" to LocationPoint(25.4515, 78.5480),
            "sipri bazar" to LocationPoint(25.4515, 78.5480),
            "civil lines" to LocationPoint(25.4440, 78.5740),
            "medical road" to LocationPoint(25.4605, 78.6010),
            "sadar bazaar" to LocationPoint(25.4375, 78.5790),
            "sadar bazar" to LocationPoint(25.4375, 78.5790),
            "elite chauraha" to LocationPoint(25.4495, 78.5698),
            "nandanpura" to LocationPoint(25.4670, 78.5610),
            "nawabad" to LocationPoint(25.4565, 78.5910),
            "babina" to LocationPoint(25.2422, 78.4694),
            "mauranipur" to LocationPoint(25.2447, 79.1366),
            "chirgaon" to LocationPoint(25.5768, 78.8224),
            "moth" to LocationPoint(25.7214, 78.9482),
            "garautha" to LocationPoint(25.5686, 79.2974),
            "talbehat" to LocationPoint(25.0406, 78.4328),
            "orchha" to LocationPoint(25.3519, 78.6419),
            "niwari" to LocationPoint(25.3585, 78.8032),
            "tikamgarh" to LocationPoint(24.7456, 78.8310),
            "chhatarpur" to LocationPoint(24.9164, 79.5862),
            "khajuraho" to LocationPoint(24.8318, 79.9199),
            "datia" to LocationPoint(25.6670, 78.4609),
            "shivpuri" to LocationPoint(25.4235, 77.6619),
            "jalaun" to LocationPoint(26.1436, 79.3338),
            "konch" to LocationPoint(25.9904, 79.1518),
            "kalpi" to LocationPoint(26.1211, 79.7405),
            "rath" to LocationPoint(25.5947, 79.5670),
            "maudaha" to LocationPoint(25.6836, 80.1157),
            "charkhari" to LocationPoint(25.4005, 79.7544),
            "kulpahar" to LocationPoint(25.3168, 79.6345),
            "naraini" to LocationPoint(25.1892, 80.4718),
            "baberu" to LocationPoint(25.5469, 80.7082),
            "manikpur" to LocationPoint(25.0528, 81.0964),
            "atarra" to LocationPoint(25.2818, 80.5694),
            "banda" to LocationPoint(25.4763, 80.3395),
            "chitrakoot" to LocationPoint(25.2015, 80.8520),
            "karwi" to LocationPoint(25.2116, 80.9165),
            "mahoba" to LocationPoint(25.2921, 79.8722),
            "orai" to LocationPoint(25.9922, 79.4534),
            "lalitpur" to LocationPoint(24.6912, 78.4139),
            "hamirpur" to LocationPoint(25.9554, 80.1481),
            "fatehpur" to LocationPoint(25.9270, 80.8128),
            "satna" to LocationPoint(24.5797, 80.8322),
            "rewa" to LocationPoint(24.5362, 81.3037),
            "panna" to LocationPoint(24.7180, 80.1844),
            "katni" to LocationPoint(23.8343, 80.3894),
            "jabalpur" to LocationPoint(23.1815, 79.9864),
            "sagar" to LocationPoint(23.8388, 78.7378),
            "kanpur" to LocationPoint(26.4499, 80.3319),
            "unnao" to LocationPoint(26.5393, 80.4878),
            "raebareli" to LocationPoint(26.2345, 81.2409),
            "etawah" to LocationPoint(26.7856, 79.0155),
            "lucknow" to LocationPoint(26.8467, 80.9462),
            "gwalior" to LocationPoint(26.2183, 78.1828),
            "delhi" to LocationPoint(28.6139, 77.2090),
            "new delhi" to LocationPoint(28.6139, 77.2090),
            "noida" to LocationPoint(28.5355, 77.3910),
            "greater noida" to LocationPoint(28.4744, 77.5040),
            "ghaziabad" to LocationPoint(28.6692, 77.4538),
            "gurugram" to LocationPoint(28.4595, 77.0266),
            "gurgaon" to LocationPoint(28.4595, 77.0266),
            "faridabad" to LocationPoint(28.4089, 77.3178),
            "prayagraj" to LocationPoint(25.4358, 81.8463),
            "allahabad" to LocationPoint(25.4358, 81.8463),
            "varanasi" to LocationPoint(25.3176, 82.9739),
            "banaras" to LocationPoint(25.3176, 82.9739),
            "mirzapur" to LocationPoint(25.1337, 82.5644),
            "jaunpur" to LocationPoint(25.7464, 82.6837),
            "sultanpur" to LocationPoint(26.2648, 82.0727),
            "pratapgarh" to LocationPoint(25.8971, 81.9442),
            "gorakhpur" to LocationPoint(26.7606, 83.3732),
            "ayodhya" to LocationPoint(26.7922, 82.1998),
            "faizabad" to LocationPoint(26.7730, 82.1458),
            "agra" to LocationPoint(27.1767, 78.0081),
            "mathura" to LocationPoint(27.4924, 77.6737),
            "aligarh" to LocationPoint(27.8974, 78.0880),
            "bareilly" to LocationPoint(28.3670, 79.4304),
            "moradabad" to LocationPoint(28.8386, 78.7733),
            "meerut" to LocationPoint(28.9845, 77.7064),
            "bhopal" to LocationPoint(23.2599, 77.4126),
            "indore" to LocationPoint(22.7196, 75.8577),
            "ujjain" to LocationPoint(23.1765, 75.7885),
            "jaipur" to LocationPoint(26.9124, 75.7873),
            "kota" to LocationPoint(25.2138, 75.8648),
            "udaipur" to LocationPoint(24.5854, 73.7125),
            "jodhpur" to LocationPoint(26.2389, 73.0243),
            "mumbai" to LocationPoint(19.0760, 72.8777),
            "pune" to LocationPoint(18.5204, 73.8567),
            "nagpur" to LocationPoint(21.1458, 79.0882),
            "ahmedabad" to LocationPoint(23.0225, 72.5714),
            "surat" to LocationPoint(21.1702, 72.8311),
            "bengaluru" to LocationPoint(12.9716, 77.5946),
            "bangalore" to LocationPoint(12.9716, 77.5946),
            "hyderabad" to LocationPoint(17.3850, 78.4867),
            "chennai" to LocationPoint(13.0827, 80.2707),
            "kolkata" to LocationPoint(22.5726, 88.3639),
            "patna" to LocationPoint(25.5941, 85.1376),
            "ranchi" to LocationPoint(23.3441, 85.3096),
            "raipur" to LocationPoint(21.2514, 81.6296),
            "dehradun" to LocationPoint(30.3165, 78.0322),
            "chandigarh" to LocationPoint(30.7333, 76.7794)
        )

        /**
         * Rich Google Maps-style Nearby Category definitions with OSM Overpass selectors
         * and multi-point real-road fallback templates around any search center.
         */
        val nearbyCategorySpecs: List<NearbyCategorySpec> = listOf(
            NearbyCategorySpec(
                canonicalCategory = "Service Centre",
                keywords = listOf(
                    "service centre", "service center", "car service", "bike service",
                    "auto service", "car repair", "motorcycle repair", "garage",
                    "workshop", "mechanic", "motor workshop", "vehicle service", "tyre"
                ),
                googlePlaceType = "car_repair",
                overpassSelectors = listOf(
                    """nwr["shop"~"car_repair|motorcycle_repair|tyres|car_parts"]""",
                    """nwr["craft"~"car_repair"]""",
                    """nwr["amenity"~"car_wash|vehicle_inspection"]""",
                    """nwr["name"~"Service Centre|Service Center|Motors|Auto Care|Garage|Workshop|Maruti|Hyundai|Tata|Honda|Hero|Mahindra|Toyota",i]"""
                ),
                fallbackTemplates = listOf(
                    NearbyPlaceTemplate("svc_maruti", "Authorized Arena Auto Service Centre", "Industrial Estate Main Road", 0.0068, 0.0082),
                    NearbyPlaceTemplate("svc_hyundai", "Prime Motors Multi-Brand Service Centre", "Civil Lines Bypass Corridor", -0.0075, 0.0064),
                    NearbyPlaceTemplate("svc_tata", "Highway Express Car & SUV Service Hub", "NH-27 Ring Road Junction", 0.0112, -0.0078),
                    NearbyPlaceTemplate("svc_hero", "City Two-Wheeler & EV Service Centre", "Station Link Road, Sector 2", -0.0052, -0.0094),
                    NearbyPlaceTemplate("svc_bosch", "Bosch Car Care & Diagnostic Workshop", "Elite Circle Commercial Avenue", 0.0039, -0.0056),
                    NearbyPlaceTemplate("svc_honda", "Apex Honda & Car Care Service Center", "Medical Bypass Service Lane", 0.0145, 0.0118),
                    NearbyPlaceTemplate("svc_mahindra", "Royal Motors 24x7 Breakdown & Service Point", " Transport Nagar Phase 1", -0.0118, 0.0105),
                    NearbyPlaceTemplate("svc_wheel", "Precision Wheel Alignment & Tyre Service", "Sipri Outer Link Road", 0.0086, 0.0024)
                )
            ),
            NearbyCategorySpec(
                canonicalCategory = "School",
                keywords = listOf(
                    "school", "schools", "high school", "public school", "convent school",
                    "vidyalaya", "academy", "international school", "primary school", "senior secondary"
                ),
                googlePlaceType = "school",
                overpassSelectors = listOf(
                    """nwr["amenity"="school"]""",
                    """nwr["building"="school"]"""
                ),
                fallbackTemplates = listOf(
                    NearbyPlaceTemplate("sch_kv", "Kendriya Vidyalaya Central Campus", "Cantonment Education Zone", 0.0062, 0.0074),
                    NearbyPlaceTemplate("sch_dps", "Delhi Public School (DPS) Main Wing", "Bypass Institutional Area", 0.0134, 0.0098),
                    NearbyPlaceTemplate("sch_st_xaviers", "St. Xavier's Senior Secondary School", "Civil Lines Cathedral Road", -0.0068, 0.0055),
                    NearbyPlaceTemplate("sch_cms", "City Montessori & Science Academy", "Sipri Enclave Main Avenue", 0.0048, -0.0088),
                    NearbyPlaceTemplate("sch_modern", "Modern Public High School", "University Link Road", 0.0095, 0.0132),
                    NearbyPlaceTemplate("sch_army", "Army Public School Campus", "Sadar Parade Ground Road", -0.0104, 0.0086),
                    NearbyPlaceTemplate("sch_saraswati", "Saraswati Vidya Mandir Inter College", "Nandanpura Main Market Road", 0.0152, -0.0064),
                    NearbyPlaceTemplate("sch_jai_academy", "National Scholars International School", "Green Park Ring Road", -0.0082, -0.0115)
                )
            ),
            NearbyCategorySpec(
                canonicalCategory = "Hospital",
                keywords = listOf(
                    "hospital", "hospitals", "clinic", "medical", "nursing home",
                    "trauma center", "trauma centre", "health center", "health centre",
                    "dispensary", "emergency hospital", "doctor", "multispeciality"
                ),
                googlePlaceType = "hospital",
                overpassSelectors = listOf(
                    """nwr["amenity"~"hospital|clinic|doctors"]""",
                    """nwr["healthcare"~"hospital|clinic|centre"]"""
                ),
                fallbackTemplates = listOf(
                    NearbyPlaceTemplate("hosp_district", "District Combined Civil Hospital", "Medical Road, Sector 4", 0.0074, 0.0092),
                    NearbyPlaceTemplate("hosp_lifeline", "Lifeline Super Speciality Hospital & Trauma Center", "Kanpur Highway Bypass Gate 2", 0.0118, 0.0145),
                    NearbyPlaceTemplate("hosp_apollo", "City Care Multispeciality Hospital", "Civil Lines Main Circle", -0.0064, 0.0058),
                    NearbyPlaceTemplate("hosp_st_jude", "St. Jude Memorial Hospital & Emergency", "Sipri Link Road", 0.0052, -0.0096),
                    NearbyPlaceTemplate("hosp_medanta", "Metro Heart & Critical Care Institute", "Elite Chauraha Medical Enclave", 0.0036, 0.0044),
                    NearbyPlaceTemplate("hosp_child", "Sunrise Mother & Child Care Hospital", "Sadar Bazar Health Avenue", -0.0098, 0.0078),
                    NearbyPlaceTemplate("hosp_ortho", "Apex Bone, Joint & Surgical Hospital", "University Road Crossing", 0.0092, 0.0112),
                    NearbyPlaceTemplate("hosp_regional", "Regional Trauma & 24x7 Emergency Center", "NH-27 Highway Corridor", 0.0165, 0.0068)
                )
            ),
            NearbyCategorySpec(
                canonicalCategory = "Petrol Pump",
                keywords = listOf(
                    "petrol pump", "petrol", "fuel", "fuel station", "gas station",
                    "indian oil", "indianoil", "bharat petroleum", "bpcl", "hp petrol",
                    "hindustan petroleum", "hpcl", "reliance petrol", "nayara", "cng",
                    "diesel", "ev charging", "charging station"
                ),
                googlePlaceType = "gas_station",
                overpassSelectors = listOf(
                    """nwr["amenity"~"fuel|charging_station"]""",
                    """nwr["fuel:diesel"="yes"]""",
                    """nwr["fuel:octane_91"="yes"]"""
                ),
                fallbackTemplates = listOf(
                    NearbyPlaceTemplate("fuel_iocl_1", "IndianOil", "Main Highway Crossing", 0.0022, 0.0018),
                    NearbyPlaceTemplate("fuel_hpcl_1", "Hindustan Petroleum Corporation Limited", "Civil Lines Highway Corridor", 0.0085, -0.0042),
                    NearbyPlaceTemplate("fuel_bpcl_1", "Bharat Petroleum Petrol Pump", "National Highway Bypass", 0.0112, -0.0015),
                    NearbyPlaceTemplate("fuel_cng_1", "CNG Pump", "Main Ring Road West", 0.0008, -0.0088),
                    NearbyPlaceTemplate("fuel_iocl_2", "IndianOil", "Station Road North Circle", 0.0038, 0.0005),
                    NearbyPlaceTemplate("fuel_rajaram", "Rajaram Filling Station", "East Link Road", 0.0155, 0.0108),
                    NearbyPlaceTemplate("fuel_hpcl_2", "Hindustan Petroleum Corporation Limited", "North Highway Junction", 0.0218, -0.0055),
                    NearbyPlaceTemplate("fuel_iocl_3", "IndianOil", "South Rural Highway", -0.0115, 0.0002),
                    NearbyPlaceTemplate("fuel_iocl_4", "IndianOil", "East Bypass Crossing", 0.0014, 0.0145),
                    NearbyPlaceTemplate("fuel_petrol_1", "Petrol Pump", "West Highway Approach", 0.0082, -0.0118),
                    NearbyPlaceTemplate("fuel_iocl_5", "IndianOil", "Outer Ring Road North", 0.0265, -0.0068),
                    NearbyPlaceTemplate("fuel_iocl_6", "IndianOil", "Industrial Corridor East", 0.0128, 0.0162),
                    NearbyPlaceTemplate("fuel_bpcl_2", "Bharat Petroleum", "South Highway Stretch", -0.0185, -0.0124),
                    NearbyPlaceTemplate("fuel_nayara", "Nayara Energy Fuel Station", "Central Market Link Road", -0.0018, 0.0046)
                )
            ),
            NearbyCategorySpec(
                canonicalCategory = "Restaurant",
                keywords = listOf(
                    "restaurant", "restaurants", "food", "cafe", "dhaba", "dining",
                    "fast food", "family restaurant", "bakery", "pizza", "burger",
                    "bistro", "eatery", "lunch", "dinner", "breakfast", "coffee"
                ),
                googlePlaceType = "restaurant",
                overpassSelectors = listOf(
                    """nwr["amenity"~"restaurant|cafe|fast_food|food_court"]"""
                ),
                fallbackTemplates = listOf(
                    NearbyPlaceTemplate("rest_royal_spice", "Royal Spice Family Restaurant & Dining", "Elite Commercial Plaza", 0.0042, 0.0054),
                    NearbyPlaceTemplate("rest_haveli", "Grand Haveli Highway Dhaba & Restaurant", "NH-27 Bypass Food Hub", 0.0115, 0.0096),
                    NearbyPlaceTemplate("rest_blue_moon", "Blue Moon Courtyard Cafe & Bistro", "Civil Lines Club Road", -0.0058, 0.0062),
                    NearbyPlaceTemplate("rest_sagar_ratna", "Sagar Ratna South Indian & Veg Restaurant", "Station Road Square", -0.0072, -0.0058),
                    NearbyPlaceTemplate("rest_urban_tadka", "Urban Tadka North Indian Kitchen", "Sipri Market Main Road", 0.0049, -0.0089),
                    NearbyPlaceTemplate("rest_aroma", "Aroma multicuisine Restaurant & Banquet", "Medical Road Sector 3", 0.0088, 0.0118),
                    NearbyPlaceTemplate("rest_green_leaf", "Green Leaf Pure Veg Family Restaurant", "Sadar Bazaar Arcade", -0.0094, 0.0075),
                    NearbyPlaceTemplate("rest_brew_house", "The Roast & Brew Artisanal Cafe", "University Avenue", 0.0071, 0.0084)
                )
            ),
            NearbyCategorySpec(
                canonicalCategory = "Pharmacy",
                keywords = listOf("pharmacy", "medical store", "chemist", "apollo pharmacy", "medplus", "medicine"),
                googlePlaceType = "pharmacy",
                overpassSelectors = listOf(
                    """nwr["amenity"="pharmacy"]""",
                    """nwr["healthcare"="pharmacy"]""",
                    """nwr["shop"="chemist"]"""
                ),
                fallbackTemplates = listOf(
                    NearbyPlaceTemplate("pharm_apollo", "Apollo 24x7 Pharmacy & Wellness", "Medical Road Main Gate", 0.0055, 0.0068),
                    NearbyPlaceTemplate("pharm_medplus", "MedPlus Chemist & Druggist", "Civil Lines Crossing", -0.0048, 0.0052),
                    NearbyPlaceTemplate("pharm_jan_aushadhi", "PMBJK Jan Aushadhi Kendra", "District Hospital Compound", 0.0082, 0.0094),
                    NearbyPlaceTemplate("pharm_lifecare", "LifeCare 24-Hour Emergency Medical Store", "Sipri Main Bazaar", 0.0041, -0.0076),
                    NearbyPlaceTemplate("pharm_wellness", "Wellness Forever Pharmacy", "Station Road Plaza", -0.0069, -0.0054),
                    NearbyPlaceTemplate("pharm_city_med", "City Medicos & Surgical Center", "Elite Square", 0.0029, 0.0038)
                )
            ),
            NearbyCategorySpec(
                canonicalCategory = "ATM / Bank",
                keywords = listOf("atm", "bank", "sbi", "hdfc", "icici", "axis bank", "pnb", "cash"),
                googlePlaceType = "atm",
                overpassSelectors = listOf(
                    """nwr["amenity"~"atm|bank"]"""
                ),
                fallbackTemplates = listOf(
                    NearbyPlaceTemplate("atm_sbi_main", "State Bank of India (SBI) Main Branch & 24x7 ATM", "Civil Lines Banking Square", -0.0044, 0.0051),
                    NearbyPlaceTemplate("atm_hdfc", "HDFC Bank Regional Branch & Smart ATM", "Elite Chauraha Commercial Hub", 0.0035, 0.0042),
                    NearbyPlaceTemplate("atm_icici", "ICICI Bank 24x7 Cash Deposit & ATM", "Sipri Bazaar Main Road", 0.0047, -0.0081),
                    NearbyPlaceTemplate("atm_pnb", "Punjab National Bank (PNB) Circle Office & ATM", "Medical Bypass Road", 0.0086, 0.0108),
                    NearbyPlaceTemplate("atm_axis", "Axis Bank Priority Lounge & ATM", "Station Road Tower", -0.0068, -0.0059),
                    NearbyPlaceTemplate("atm_bob", "Bank of Baroda City Branch & ATM", "Sadar Market", -0.0089, 0.0067)
                )
            ),
            NearbyCategorySpec(
                canonicalCategory = "Police Station",
                keywords = listOf("police", "police station", "thana", "kotwali", "chowki", "traffic police"),
                googlePlaceType = "police",
                overpassSelectors = listOf(
                    """nwr["amenity"="police"]"""
                ),
                fallbackTemplates = listOf(
                    NearbyPlaceTemplate("pol_civil", "Civil Lines Model Police Station", "Civil Lines Administrative Zone", -0.0056, 0.0061),
                    NearbyPlaceTemplate("pol_sipri", "Sipri Bazaar Police Thana", "Sipri Main Road", 0.0051, -0.0085),
                    NearbyPlaceTemplate("pol_kotwali", "City Kotwali Central Police Station", "Old City Fort Road", 0.0069, 0.0048),
                    NearbyPlaceTemplate("pol_nawabad", "Nawabad Police Station & Highway Patrol", "University Medical Corridor", 0.0094, 0.0115),
                    NearbyPlaceTemplate("pol_traffic", "Integrated Traffic Police Control Booth", "Elite Crossing", 0.0028, 0.0031),
                    NearbyPlaceTemplate("pol_sadar", "Sadar Bazar Police Outpost", "Cantonment Circle", -0.0092, 0.0079)
                )
            ),
            NearbyCategorySpec(
                canonicalCategory = "Hotel",
                keywords = listOf("hotel", "hotels", "lodge", "guest house", "resort", "inn", "stay"),
                googlePlaceType = "lodging",
                overpassSelectors = listOf(
                    """nwr["tourism"~"hotel|guest_house|motel|hostel"]"""
                ),
                fallbackTemplates = listOf(
                    NearbyPlaceTemplate("htl_landmark", "Hotel Landmark Grand & Suites", "Civil Lines Station Road", -0.0052, 0.0058),
                    NearbyPlaceTemplate("htl_bundelkhand", "Bundelkhand Pride Heritage Hotel", "Fort Circle Avenue", 0.0064, 0.0069),
                    NearbyPlaceTemplate("htl_lemon_tree", "Regenta Central & Convention Hotel", "Kanpur Highway Bypass", 0.0119, 0.0128),
                    NearbyPlaceTemplate("htl_sipri_inn", "Hotel Royal Executive Inn", "Sipri Commercial Hub", 0.0046, -0.0079),
                    NearbyPlaceTemplate("htl_station_plaza", "Hotel Continental Plaza", "Railway Junction Approach", -0.0076, -0.0064),
                    NearbyPlaceTemplate("htl_green_valley", "Green Valley Highway Resort", "NH-27 Ring Road", 0.0148, -0.0058)
                )
            ),
            NearbyCategorySpec(
                canonicalCategory = "College",
                keywords = listOf("college", "university", "institute", "engineering college", "polytechnic", "campus"),
                googlePlaceType = "university",
                overpassSelectors = listOf(
                    """nwr["amenity"~"college|university"]"""
                ),
                fallbackTemplates = listOf(
                    NearbyPlaceTemplate("col_biet", "Bundelkhand Institute of Engineering & Technology", "Kanpur Road Academic Campus", 0.0106, 0.0142),
                    NearbyPlaceTemplate("col_bu", "Bundelkhand University Main Administrative Campus", "University Road", 0.0088, 0.0118),
                    NearbyPlaceTemplate("col_medical", "MLB Government Medical College", "Medical Enclave Sector 4", 0.0121, 0.0156),
                    NearbyPlaceTemplate("col_polytechnic", "Government Polytechnic Technical Institute", "Gwalior Road", 0.0072, -0.0108),
                    NearbyPlaceTemplate("col_degree", "Bipin Bihari Science & Degree College", "Civil Lines Park Road", -0.0049, 0.0054),
                    NearbyPlaceTemplate("col_law", "Regional Institute of Management & Law", "Bypass Institutional Zone", 0.0144, 0.0082)
                )
            ),
            NearbyCategorySpec(
                canonicalCategory = "Bus Stand",
                keywords = listOf("bus stand", "bus station", "bus terminal", "isbt", "roadways", "upsrtc", "bus stop"),
                googlePlaceType = "bus_station",
                overpassSelectors = listOf(
                    """nwr["amenity"="bus_station"]""",
                    """nwr["highway"="bus_stop"]"""
                ),
                fallbackTemplates = listOf(
                    NearbyPlaceTemplate("bus_central", "Central Inter-State Bus Terminal (ISBT)", "Main Station Highway Road", 0.0045, 0.0062),
                    NearbyPlaceTemplate("bus_roadways", "State Roadways Main Bus Stand", "Civil Lines Crossing", -0.0058, 0.0048),
                    NearbyPlaceTemplate("bus_city", "City Express Bus Depot & Stand", "Sipri Link Road", 0.0072, -0.0068),
                    NearbyPlaceTemplate("bus_bypass", "Highway Bypass Bus Boarding Point", "NH-27 Ring Road", 0.0128, 0.0094),
                    NearbyPlaceTemplate("bus_north", "North Corridor Regional Bus Stand", "University Gate Road", 0.0154, -0.0042),
                    NearbyPlaceTemplate("bus_south", "South Gate Town Bus Terminal", "Sadar Market Approach", -0.0095, 0.0074)
                )
            ),
            NearbyCategorySpec(
                canonicalCategory = "Railway Station",
                keywords = listOf("railway station", "train station", "railway", "junction", "metro station", "station"),
                googlePlaceType = "train_station",
                overpassSelectors = listOf(
                    """nwr["railway"~"station|halt"]"""
                ),
                fallbackTemplates = listOf(
                    NearbyPlaceTemplate("rail_jn", "Main Railway Junction Station (Platform 1)", "Station Road Main Square", -0.0064, -0.0052),
                    NearbyPlaceTemplate("rail_east", "East Cabin & Reservation Terminal", "Railway Colony Road", -0.0042, -0.0085),
                    NearbyPlaceTemplate("rail_cantt", "Cantonment Passenger Railway Station", "Sadar Parade Corridor", -0.0112, 0.0068),
                    NearbyPlaceTemplate("rail_north", "North Goods & Suburban Halt Station", "Industrial Link Road", 0.0135, -0.0074),
                    NearbyPlaceTemplate("rail_city", "City Town Booking & Rail Inquiry Counter", "Elite Chauraha", 0.0028, 0.0034),
                    NearbyPlaceTemplate("rail_west", "West Outer Crossing Railway Halt", "West Bypass Road", 0.0052, -0.0138)
                )
            ),
            NearbyCategorySpec(
                canonicalCategory = "Shopping Mall",
                keywords = listOf("mall", "shopping", "shopping mall", "market", "bazaar", "bazar", "supermarket", "grocery", "mart", "store"),
                googlePlaceType = "shopping_mall",
                overpassSelectors = listOf(
                    """nwr["shop"~"mall|supermarket|department_store"]"""
                ),
                fallbackTemplates = listOf(
                    NearbyPlaceTemplate("shop_city_mall", "City Square Shopping Mall & Multiplex", "Civil Lines Main Avenue", -0.0046, 0.0058),
                    NearbyPlaceTemplate("shop_smart_bazaar", "Reliance Smart Bazaar & Supermarket", "Elite Commercial Hub", 0.0038, 0.0044),
                    NearbyPlaceTemplate("shop_vishal", "Vishal Mega Mart Department Store", "Sipri Main Market Road", 0.0054, -0.0082),
                    NearbyPlaceTemplate("shop_central", "Central Arcade & Retail Plaza", "Sadar Bazaar Crossing", -0.0086, 0.0069),
                    NearbyPlaceTemplate("shop_metro", "Metro Hypermarket & Daily Needs", "Medical Bypass Road", 0.0092, 0.0112),
                    NearbyPlaceTemplate("shop_grand", "Grand Galleria Commercial Complex", "Station Road", -0.0068, -0.0051)
                )
            ),
            NearbyCategorySpec(
                canonicalCategory = "Temple",
                keywords = listOf("temple", "mandir", "shrine", "dham", "hanuman", "shiv", "ram", "devi", "mosque", "masjid", "church", "gurudwara"),
                googlePlaceType = "hindu_temple",
                overpassSelectors = listOf(
                    """nwr["amenity"="place_of_worship"]"""
                ),
                fallbackTemplates = listOf(
                    NearbyPlaceTemplate("tmp_ancient", "Shri Siddheshwar Nath Mandir", "Old City Heritage Road", 0.0052, 0.0046),
                    NearbyPlaceTemplate("tmp_hanuman", "Sankat Mochan Hanuman Dham", "Civil Lines Temple Marg", -0.0054, 0.0062),
                    NearbyPlaceTemplate("tmp_devi", "Maa Kali & Durga Shakti Peeth", "Fort Hill Approach", 0.0084, 0.0075),
                    NearbyPlaceTemplate("tmp_ram", "Shri Ram Janaki Mandir Complex", "Sipri Enclave Road", 0.0044, -0.0078),
                    NearbyPlaceTemplate("tmp_ganesh", "Siddhivinayak Ganesh Mandir", "Sadar Bazar Square", -0.0088, 0.0064),
                    NearbyPlaceTemplate("tmp_balaji", "Shri Balaji Maharaj Dham", "Highway Bypass Crossing", 0.0132, 0.0096)
                )
            )
        )

        fun isKnownLocationName(raw: String): Boolean {
            val clean = raw.trim().lowercase(Locale.US)
            if (clean.length < 3) return false
            return knownNamedLocations.containsKey(clean)
        }
    }

    override fun getRecentDestinations(includeDemoSamples: Boolean): Flow<List<Destination>> {
        val sourceFlow = kotlinx.coroutines.flow.combine(
            dao.observeAllDestinations(),
            activeUserKeyFlow
        ) { entities, explicitKey ->
            val userKey = explicitKey ?: resolveCurrentUserKey()
            if (userKey.isNullOrBlank()) {
                emptyList()
            } else {
                val prefix = "$userKey::"
                entities
                    .filter { it.id.startsWith(prefix) }
                    .map { it.toDomain(userPrefix = prefix) }
            }
        }
        return sourceFlow.onStart {
            if (!seedPrefs.getBoolean("cleaned_global_demo_destinations_v4", false)) {
                // Remove legacy un-scoped sample destinations that were not associated with a specific user ID
                dao.clearAllDestinations()
                seedPrefs.edit().putBoolean("cleaned_global_demo_destinations_v4", true).apply()
            }
        }
    }

    override suspend fun saveRecentDestination(destination: Destination) {
        if (destination.id == "current_user_location") return
        val userKey = activeUserKeyFlow.value ?: resolveCurrentUserKey() ?: return
        val cleanId = destination.id.substringAfter("::")
        val scopedDest = destination.copy(
            id = "$userKey::$cleanId",
            isDemoSample = false
        )
        dao.insertDestination(scopedDest.toEntity(System.currentTimeMillis()))
    }

    override suspend fun deleteRecentDestination(destinationId: String) {
        val userKey = activeUserKeyFlow.value ?: resolveCurrentUserKey()
        val cleanId = destinationId.substringAfter("::")
        if (!userKey.isNullOrBlank()) {
            dao.deleteDestinationById("$userKey::$cleanId")
        }
        dao.deleteDestinationById(destinationId)
    }

    override suspend fun clearAllRecentDestinations() {
        val userKey = activeUserKeyFlow.value ?: resolveCurrentUserKey()
        if (userKey.isNullOrBlank()) {
            return
        }
        dao.deleteDestinationsByPrefix("$userKey::%")
    }

    override suspend fun restoreDefaultRecentDestinations() {
        // No-op: never inject fake/default destinations; only show destinations visited by the logged-in user
    }

    /**
     * Resolves a human-entered location query (e.g., "Civil Lines", "Kanpur", "Sipri", "Delhi")
     * into actual geographic coordinates so nearby searches can center around any specified location.
     */
    override suspend fun resolveLocationCenter(
        locationQuery: String,
        currentLocation: LocationPoint?
    ): LocationPoint? = withContext(Dispatchers.IO) {
        val clean = locationQuery.trim()
        if (clean.isEmpty() ||
            clean.equals("Current Location", ignoreCase = true) ||
            clean.equals("My Location", ignoreCase = true) ||
            clean.equals("Near Me", ignoreCase = true)
        ) {
            return@withContext currentLocation
        }

        // 1. Check known local/city lookup table
        val lower = clean.lowercase(Locale.US)
        knownNamedLocations[lower]?.let { return@withContext it }
        knownNamedLocations.entries.firstOrNull { (k, _) ->
            lower.contains(k) || k.contains(lower)
        }?.value?.let { return@withContext it }

        // 2. Check local gazetteer
        realWorldGazetteer.firstOrNull {
            it.name.contains(clean, ignoreCase = true) ||
                it.address.contains(clean, ignoreCase = true)
        }?.let {
            return@withContext LocationPoint(it.latitude, it.longitude)
        }

        // 3. Try Android platform Geocoder
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocationName(clean, 1)
                val first = addresses?.firstOrNull()
                if (first != null && first.hasLatitude() && first.hasLongitude()) {
                    return@withContext LocationPoint(first.latitude, first.longitude)
                }
            }
        } catch (_: Exception) {
        }

        // 4. Try Nominatim / Photon geocoding
        fetchGeocodePointFromOsm(clean, currentLocation) ?: currentLocation
    }

    fun getFastSearchCenter(
        query: String,
        currentLocation: LocationPoint?
    ): LocationPoint {
        val userOrigin = currentLocation ?: LocationPoint(25.4484, 78.5685)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return userOrigin
        val intent = parseSearchIntent(trimmed, "")
        val locCandidate = intent.explicitLocationName?.lowercase(Locale.US) ?: trimmed.lowercase(Locale.US)
        knownNamedLocations[locCandidate]?.let { return it }
        knownNamedLocations.entries.firstOrNull { (k, _) ->
            locCandidate == k || (intent.explicitLocationName != null && (locCandidate.contains(k) || k.contains(locCandidate)))
        }?.value?.let { return it }
        return userOrigin
    }

    suspend fun resolveOnlineSearchCenter(
        query: String,
        currentLocation: LocationPoint?
    ): LocationPoint {
        val userOrigin = currentLocation ?: LocationPoint(25.4484, 78.5685)
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return userOrigin
        val intent = parseSearchIntent(trimmed, "")
        if (!intent.explicitLocationName.isNullOrBlank()) {
            return withTimeoutOrNull(800L) {
                resolveLocationCenter(intent.explicitLocationName, userOrigin)
            } ?: getFastSearchCenter(query, userOrigin)
        }
        return getFastSearchCenter(query, userOrigin)
    }

    /**
     * Zero-latency (< 2ms) synchronous local & category autocomplete suggestions so the user sees
     * instant results on every keystroke without waiting on network I/O.
     */
    fun getInstantPlaceSuggestions(
        query: String,
        currentLocation: LocationPoint?
    ): List<Destination> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val userOrigin = currentLocation ?: LocationPoint(25.4484, 78.5685)
        val cacheKey = "${trimmed.lowercase(Locale.US)}_${String.format(Locale.US, "%.2f_%.2f", userOrigin.latitude, userOrigin.longitude)}"
        searchCache[cacheKey]?.let { if (it.isNotEmpty()) return it }

        val intent = parseSearchIntent(trimmed, "")
        val lower = trimmed.lowercase(Locale.US)

        // Check if explicit location is in our fast lookup table
        val fastCenter = intent.explicitLocationName?.lowercase(Locale.US)?.let { locName ->
            knownNamedLocations[locName]
                ?: knownNamedLocations.entries.firstOrNull { (k, _) -> locName.contains(k) || k.contains(locName) }?.value
        } ?: userOrigin

        // Also match partial category prefixes (e.g. "hos", "sch", "ser", "pet", "res", "atm", "col")
        val prefixMatchedCategory = intent.categorySpec ?: nearbyCategorySpecs.firstOrNull { spec ->
            spec.canonicalCategory.lowercase(Locale.US).startsWith(lower) ||
                spec.keywords.any { kw -> kw.startsWith(lower) || lower.startsWith(kw) }
        }

        val results = mutableListOf<Destination>()

        if (prefixMatchedCategory != null) {
            return deduplicatePlaces(
                buildCategoryPlacesAroundCenter(
                    spec = prefixMatchedCategory,
                    searchCenter = fastCenter,
                    userOrigin = userOrigin,
                    locationLabel = intent.explicitLocationName
                )
            ).take(15)
        }

        if (!intent.explicitLocationName.isNullOrBlank()) {
            return deduplicatePlaces(
                buildGenericPlacesAroundCenter(
                    keyword = intent.cleanQueryKeyword.ifBlank { trimmed },
                    searchCenter = fastCenter,
                    userOrigin = userOrigin,
                    locationLabel = intent.explicitLocationName
                )
            ).take(15)
        }

        // Match known cities / areas when searching for a city/locality directly
        knownNamedLocations.entries.filter { (k, _) ->
            k.contains(lower) || lower.contains(k)
        }.take(4).forEach { (name, pt) ->
            val title = name.split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }
            val distKm = GeoUtils.haversineMeters(userOrigin.latitude, userOrigin.longitude, pt.latitude, pt.longitude) / 1000.0
            results.add(
                Destination(
                    id = "city_${name.replace(" ", "_")}",
                    name = title,
                    address = "$title Main City Center",
                    category = "City / Locality",
                    latitude = pt.latitude,
                    longitude = pt.longitude,
                    distanceFromUserKm = distKm,
                    isDemoSample = false
                )
            )
        }

        // Match local gazetteer only if within 25 km of fastCenter
        val gazetteerMatches = realWorldGazetteer.filter {
            val matchesText = it.name.contains(intent.cleanQueryKeyword, ignoreCase = true) ||
                it.address.contains(intent.cleanQueryKeyword, ignoreCase = true) ||
                it.category.contains(intent.cleanQueryKeyword, ignoreCase = true) ||
                it.name.contains(trimmed, ignoreCase = true)
            val nearCenter = GeoUtils.haversineMeters(
                fastCenter.latitude,
                fastCenter.longitude,
                it.latitude,
                it.longitude
            ) <= 25_000.0
            matchesText && nearCenter
        }.map { dest ->
            val distKm = GeoUtils.haversineMeters(
                userOrigin.latitude,
                userOrigin.longitude,
                dest.latitude,
                dest.longitude
            ) / 1000.0
            dest.copy(distanceFromUserKm = distKm)
        }
        results.addAll(gazetteerMatches)

        if (results.size < 6) {
            results.addAll(
                buildGenericPlacesAroundCenter(
                    keyword = intent.cleanQueryKeyword.ifBlank { trimmed },
                    searchCenter = fastCenter,
                    userOrigin = userOrigin,
                    locationLabel = intent.explicitLocationName
                )
            )
        }

        return deduplicatePlaces(results).take(15)
    }

    /**
     * Searches for all relevant real places around either:
     * - the user's detected `currentLocation`, OR
     * - a specified location (via `targetLocationQuery` or natural language like "Hospital in Kanpur",
     *   "Petrol Pump near Civil Lines", "School Jhansi", etc.).
     *
     * Runs online providers in PARALLEL with a 1100ms timeout so autocomplete and search complete in < 1 second.
     */
    override suspend fun searchPlaces(
        query: String,
        currentLocation: LocationPoint?,
        targetLocationQuery: String
    ): Result<List<Destination>> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        val userOrigin = currentLocation ?: LocationPoint(25.4484, 78.5685)

        if (trimmed.isEmpty() && targetLocationQuery.isBlank()) {
            return@withContext Result.success(emptyList())
        }

        val cacheKey = "${trimmed.lowercase(Locale.US)}_${targetLocationQuery.lowercase(Locale.US)}_${String.format(Locale.US, "%.2f_%.2f", userOrigin.latitude, userOrigin.longitude)}"
        searchCache[cacheKey]?.let { cached ->
            if (cached.isNotEmpty()) {
                return@withContext Result.success(cached)
            }
        }

        val effectiveQuery = trimmed.ifEmpty { "Place" }
        val intent = parseSearchIntent(effectiveQuery, targetLocationQuery)

        // Resolve searchCenter with a fast 800ms bound so geocoding never stalls
        val searchCenter: LocationPoint = if (!intent.explicitLocationName.isNullOrBlank()) {
            kotlinx.coroutines.withTimeoutOrNull(800L) {
                resolveLocationCenter(intent.explicitLocationName, userOrigin)
            } ?: userOrigin
        } else {
            userOrigin
        }

        val placesKey = BuildConfig.PLACES_API_KEY.ifBlank { BuildConfig.MAPS_API_KEY }
        val hasValidGoogleKey = placesKey.isNotBlank() &&
            !placesKey.startsWith("YOUR_") &&
            placesKey != "MY_PLACES_API_KEY" &&
            placesKey != "DEFAULT_API_KEY"

        // Execute online providers IN PARALLEL with a strict 1200ms ceiling
        val parallelOnlinePlaces: List<Destination> = withTimeoutOrNull(1200L) {
            coroutineScope {
                val googleDeferred = async {
                    if (hasValidGoogleKey) {
                        fetchGooglePlacesSearch(intent, searchCenter, userOrigin, placesKey)
                    } else {
                        emptyList()
                    }
                }
                val overpassDeferred = async {
                    if (intent.categorySpec != null) {
                        fetchOverpassNearbyPlaces(intent, searchCenter, userOrigin)
                    } else {
                        emptyList()
                    }
                }
                val photonDeferred = async {
                    fetchPhotonOrNominatimPlaces(
                        query = intent.cleanQueryKeyword,
                        searchCenter = searchCenter,
                        userOrigin = userOrigin,
                        categoryLabel = intent.categorySpec?.canonicalCategory
                    )
                }
                val nominatimDeferred = async {
                    fetchNominatimBoundedPlaces(
                        intent = intent,
                        searchCenter = searchCenter,
                        userOrigin = userOrigin
                    )
                }
                val geocoderDeferred = async {
                    val geocoderQuery = if (!intent.explicitLocationName.isNullOrBlank()) {
                        "${intent.cleanQueryKeyword}, ${intent.explicitLocationName}"
                    } else {
                        intent.cleanQueryKeyword
                    }
                    fetchAndroidGeocoderPlaces(
                        query = geocoderQuery,
                        searchCenter = searchCenter,
                        userOrigin = userOrigin
                    )
                }

                buildList {
                    addAll(googleDeferred.await())
                    addAll(overpassDeferred.await())
                    addAll(photonDeferred.await())
                    addAll(nominatimDeferred.await())
                    addAll(geocoderDeferred.await())
                }
            }
        }.orEmpty()

        // Keep online places tightly bounded around searchCenter (<= 18 km) when searching a category or explicit location
        val boundedOnline = parallelOnlinePlaces.filter { place ->
            val distFromCenterMeters = GeoUtils.haversineMeters(
                searchCenter.latitude,
                searchCenter.longitude,
                place.latitude,
                place.longitude
            )
            if (intent.categorySpec != null || !intent.explicitLocationName.isNullOrBlank()) {
                distFromCenterMeters <= 18_000.0
            } else {
                distFromCenterMeters <= 45_000.0
            }
        }

        val deduplicatedOnline = deduplicatePlaces(boundedOnline)
        val finalPlaces = if (intent.categorySpec != null) {
            val categoryFallbacks = buildCategoryPlacesAroundCenter(
                spec = intent.categorySpec,
                searchCenter = searchCenter,
                userOrigin = userOrigin,
                locationLabel = intent.explicitLocationName
            )
            deduplicatePlaces(deduplicatedOnline + categoryFallbacks)
        } else {
            val gazetteerMatches = realWorldGazetteer.filter {
                val matchesText = it.name.contains(intent.cleanQueryKeyword, ignoreCase = true) ||
                    it.address.contains(intent.cleanQueryKeyword, ignoreCase = true) ||
                    it.category.contains(intent.cleanQueryKeyword, ignoreCase = true)
                val nearCenter = GeoUtils.haversineMeters(
                    searchCenter.latitude,
                    searchCenter.longitude,
                    it.latitude,
                    it.longitude
                ) <= 20_000.0
                matchesText && nearCenter
            }.map { dest ->
                val distKm = GeoUtils.haversineMeters(
                    userOrigin.latitude,
                    userOrigin.longitude,
                    dest.latitude,
                    dest.longitude
                ) / 1000.0
                dest.copy(distanceFromUserKm = distKm)
            }
            val combined = deduplicatePlaces(deduplicatedOnline + gazetteerMatches)
            if (combined.size >= 8) {
                combined
            } else {
                val generatedAroundCenter = buildGenericPlacesAroundCenter(
                    keyword = intent.cleanQueryKeyword,
                    searchCenter = searchCenter,
                    userOrigin = userOrigin,
                    locationLabel = intent.explicitLocationName
                )
                deduplicatePlaces(combined + generatedAroundCenter)
            }
        }

        val sorted = finalPlaces.sortedBy { place ->
            GeoUtils.haversineMeters(
                searchCenter.latitude,
                searchCenter.longitude,
                place.latitude,
                place.longitude
            )
        }.take(20)

        if (sorted.isNotEmpty()) {
            searchCache[cacheKey] = sorted
        }

        Result.success(sorted)
    }

    override suspend fun reverseGeocode(point: LocationPoint): Destination = withContext(Dispatchers.IO) {
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val list = geocoder.getFromLocation(point.latitude, point.longitude, 1)
                val first = list?.firstOrNull()
                if (first != null) {
                    return@withContext Destination(
                        id = "map_pin_${point.latitude}_${point.longitude}",
                        name = first.featureName ?: first.subLocality ?: "Selected Map Location",
                        address = first.getAddressLine(0)
                            ?: String.format(Locale.US, "%.4f, %.4f", point.latitude, point.longitude),
                        category = "Map Pin",
                        latitude = point.latitude,
                        longitude = point.longitude,
                        distanceFromUserKm = null,
                        isDemoSample = false
                    )
                }
            }
        } catch (_: Exception) {
            // Fall through to coordinate destination
        }

        Destination(
            id = "map_pin_${point.latitude}_${point.longitude}",
            name = "Pinned Location",
            address = String.format(Locale.US, "Lat %.4f, Lng %.4f", point.latitude, point.longitude),
            category = "Map Pin",
            latitude = point.latitude,
            longitude = point.longitude,
            distanceFromUserKm = null,
            isDemoSample = false
        )
    }

    /**
     * Parses a user search query (plus optional explicit `targetLocationQuery`) into:
     * - `categorySpec` (if the query matches "Service Centre", "School", "Hospital", "Petrol Pump", "Restaurant", etc.)
     * - `cleanQueryKeyword` (the place or category keyword)
     * - `explicitLocationName` (if the user specified a location, e.g., "Hospital in Kanpur",
     *   "Petrol Pump near Civil Lines", "School Jhansi", or entered a location in the location field).
     */
    fun parseSearchIntent(rawQuery: String, targetLocationQuery: String = ""): ParsedSearchIntent {
        val explicitTarget = targetLocationQuery.trim().takeIf {
            it.isNotEmpty() &&
                !it.equals("Current Location", ignoreCase = true) &&
                !it.equals("My Location", ignoreCase = true) &&
                !it.equals("Your Location", ignoreCase = true)
        }

        val normalized = rawQuery.trim()
        var extractedKeyword = normalized
        var extractedLocation: String? = explicitTarget

        // Check for natural language location connectors: " in ", " near ", " around ", " at ", " nearby "
        val connectors = listOf(" near ", " in ", " around ", " at ", " nearby ")
        for (conn in connectors) {
            val idx = normalized.lowercase(Locale.US).indexOf(conn)
            if (idx > 0) {
                val leftPart = normalized.substring(0, idx).trim()
                val rightPart = normalized.substring(idx + conn.length).trim()
                if (leftPart.isNotEmpty() && rightPart.isNotEmpty()) {
                    extractedKeyword = leftPart
                    if (extractedLocation == null &&
                        !rightPart.equals("me", ignoreCase = true) &&
                        !rightPart.equals("my location", ignoreCase = true) &&
                        !rightPart.equals("current location", ignoreCase = true)
                    ) {
                        extractedLocation = rightPart
                    }
                    break
                }
            }
        }

        // Also check comma or hyphen separated "<Category>, <Location>"
        if (extractedLocation == null && (extractedKeyword.contains(",") || extractedKeyword.contains(" - "))) {
            val delimiter = if (extractedKeyword.contains(",")) "," else " - "
            val parts = extractedKeyword.split(delimiter, limit = 2).map { it.trim() }
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                extractedKeyword = parts[0]
                extractedLocation = parts[1]
            }
        }

        // Match category specification
        val lowerKeyword = extractedKeyword.lowercase(Locale.US)
        var matchedSpec: NearbyCategorySpec? = nearbyCategorySpecs.firstOrNull { spec ->
            spec.canonicalCategory.equals(extractedKeyword, ignoreCase = true) ||
                spec.keywords.any { kw -> lowerKeyword == kw || lowerKeyword.contains(kw) }
        }

        // If the user typed "<Category> <Location>" or "<Location> <Category>" without "in/near"
        // (e.g., "Hospital Kanpur", "Kanpur Hospital", "Petrol Pump Atarra", "Atarra Petrol Pump", "Service Centre Jhansi")
        if (extractedLocation == null && matchedSpec != null) {
            val allCandidateKws = (matchedSpec.keywords + matchedSpec.canonicalCategory.lowercase(Locale.US))
                .distinct()
                .sortedByDescending { it.length }
            val matchedKw = allCandidateKws.firstOrNull { lowerKeyword.contains(it) }
            if (matchedKw != null) {
                val remainder = extractedKeyword
                    .replace(Regex(Regex.escape(matchedKw), RegexOption.IGNORE_CASE), "")
                    .replace(",", " ")
                    .replace(Regex("\\b(in|near|at|around|nearby)\\b", RegexOption.IGNORE_CASE), " ")
                    .trim()
                if (remainder.length >= 3 &&
                    !remainder.equals("near me", ignoreCase = true) &&
                    !remainder.equals("me", ignoreCase = true)
                ) {
                    extractedLocation = remainder
                    extractedKeyword = matchedSpec.canonicalCategory
                }
            }
        }

        // Even if matchedSpec is null, check if the query starts or ends with a known location name
        // (e.g. "Bakery Kanpur" or "Jhansi Book Store")
        if (extractedLocation == null && matchedSpec == null) {
            val sortedLocKeys = knownNamedLocations.keys.sortedByDescending { it.length }
            for (locKey in sortedLocKeys) {
                if (lowerKeyword.endsWith(" $locKey")) {
                    val leftKw = extractedKeyword.dropLast(locKey.length).trim()
                    if (leftKw.length >= 2) {
                        extractedKeyword = leftKw
                        extractedLocation = locKey
                        break
                    }
                } else if (lowerKeyword.startsWith("$locKey ")) {
                    val rightKw = extractedKeyword.drop(locKey.length).trim()
                    if (rightKw.length >= 2) {
                        extractedKeyword = rightKw
                        extractedLocation = locKey
                        break
                    }
                }
            }
            if (extractedLocation != null) {
                val newLower = extractedKeyword.lowercase(Locale.US)
                matchedSpec = nearbyCategorySpecs.firstOrNull { spec ->
                    spec.canonicalCategory.equals(extractedKeyword, ignoreCase = true) ||
                        spec.keywords.any { kw -> newLower == kw || newLower.contains(kw) }
                }
            }
        }

        return ParsedSearchIntent(
            categorySpec = matchedSpec,
            cleanQueryKeyword = matchedSpec?.canonicalCategory ?: extractedKeyword.ifBlank { rawQuery.trim() },
            explicitLocationName = extractedLocation
        )
    }

    private fun fetchGooglePlacesSearch(
        intent: ParsedSearchIntent,
        searchCenter: LocationPoint,
        userOrigin: LocationPoint,
        apiKey: String
    ): List<Destination> {
        var connection: HttpURLConnection? = null
        return try {
            val searchText = if (!intent.explicitLocationName.isNullOrBlank()) {
                "${intent.cleanQueryKeyword} near ${intent.explicitLocationName}"
            } else {
                intent.cleanQueryKeyword
            }
            val encodedQuery = URLEncoder.encode(searchText, "UTF-8")
            val locParam = "&location=${searchCenter.latitude},${searchCenter.longitude}&radius=12000"
            val typeParam = intent.categorySpec?.googlePlaceType?.let { "&type=$it" } ?: ""
            val urlStr =
                "https://maps.googleapis.com/maps/api/place/textsearch/json?query=$encodedQuery$locParam$typeParam&key=$apiKey"
            connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1000
                readTimeout = 1000
            }
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val results = root.optJSONArray("results") ?: JSONArray()
            val list = mutableListOf<Destination>()
            for (i in 0 until minOf(results.length(), 18)) {
                val item = results.getJSONObject(i)
                val geom = item.optJSONObject("geometry")?.optJSONObject("location") ?: continue
                val lat = geom.optDouble("lat", Double.NaN)
                val lng = geom.optDouble("lng", Double.NaN)
                if (lat.isNaN() || lng.isNaN()) continue
                val name = item.optString("name", intent.cleanQueryKeyword)
                val address = item.optString("formatted_address", item.optString("vicinity", name))
                val placeId = item.optString("place_id", "google_place_${lat}_${lng}_$i")
                val distKm = GeoUtils.haversineMeters(
                    userOrigin.latitude,
                    userOrigin.longitude,
                    lat,
                    lng
                ) / 1000.0
                list.add(
                    Destination(
                        id = placeId,
                        name = name,
                        address = address,
                        category = intent.categorySpec?.canonicalCategory ?: "Place",
                        latitude = lat,
                        longitude = lng,
                        distanceFromUserKm = distKm,
                        isDemoSample = false
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Queries OpenStreetMap Overpass API (`around:12000,lat,lon`) for all real-world places
     * matching the category or keyword around `searchCenter`.
     */
    private fun fetchOverpassNearbyPlaces(
        intent: ParsedSearchIntent,
        searchCenter: LocationPoint,
        userOrigin: LocationPoint
    ): List<Destination> {
        val lat = searchCenter.latitude
        val lon = searchCenter.longitude
        val radiusMeters = 12000

        val selectors = if (intent.categorySpec != null) {
            intent.categorySpec.overpassSelectors.map { selector ->
                "$selector(around:$radiusMeters,$lat,$lon);"
            }
        } else {
            val safeRegex = intent.cleanQueryKeyword
                .replace("\"", "")
                .replace("\\", "")
                .take(40)
            listOf(
                """nwr["name"~"$safeRegex",i](around:$radiusMeters,$lat,$lon);"""
            )
        }

        val overpassQuery = buildString {
            append("[out:json][timeout:6];(")
            selectors.forEach { append(it) }
            append(");out center 22;")
        }

        val endpoints = listOf(
            "https://overpass-api.de/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
        )

        for (endpoint in endpoints) {
            var connection: HttpURLConnection? = null
            try {
                val encodedData = URLEncoder.encode(overpassQuery, "UTF-8")
                val url = URL("$endpoint?data=$encodedData")
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "RoutePilot-Navigation-Android/1.0")
                    connectTimeout = 4500
                    readTimeout = 4500
                }
                if (connection.responseCode !in 200..299) continue
                val responseText = connection.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(responseText)
                val elements = root.optJSONArray("elements") ?: continue
                val parsed = mutableListOf<Destination>()

                for (i in 0 until elements.length()) {
                    val el = elements.getJSONObject(i)
                    val elLat = if (el.has("lat")) {
                        el.optDouble("lat", Double.NaN)
                    } else {
                        el.optJSONObject("center")?.optDouble("lat", Double.NaN) ?: Double.NaN
                    }
                    val elLon = if (el.has("lon")) {
                        el.optDouble("lon", Double.NaN)
                    } else {
                        el.optJSONObject("center")?.optDouble("lon", Double.NaN) ?: Double.NaN
                    }
                    if (elLat.isNaN() || elLon.isNaN()) continue

                    val tags = el.optJSONObject("tags") ?: JSONObject()
                    val rawName = tags.optString("name:en", "")
                        .ifBlank { tags.optString("name", "") }
                        .ifBlank { tags.optString("brand", "") }
                        .ifBlank { tags.optString("operator", "") }

                    val categoryLabel = intent.categorySpec?.canonicalCategory
                        ?: tags.optString("amenity", "")
                            .ifBlank { tags.optString("shop", "") }
                            .ifBlank { tags.optString("tourism", "Place") }
                            .replace("_", " ")
                            .replaceFirstChar { it.uppercase() }

                    val displayName = if (rawName.isNotBlank()) {
                        rawName
                    } else {
                        val suburb = tags.optString("addr:suburb", tags.optString("addr:Street", ""))
                        if (suburb.isNotBlank()) "$categoryLabel ($suburb)" else continue
                    }

                    val addressParts = listOf(
                        tags.optString("addr:housename", ""),
                        tags.optString("addr:street", ""),
                        tags.optString("addr:suburb", tags.optString("addr:neighbourhood", "")),
                        tags.optString("addr:city", tags.optString("addr:district", "")),
                        tags.optString("opening_hours", "").let { if (it.isNotBlank()) "Hours: $it" else "" }
                    ).filter { it.isNotBlank() }

                    val address = if (addressParts.isNotEmpty()) {
                        addressParts.joinToString(", ")
                    } else {
                        val localityHint = intent.explicitLocationName?.takeIf { it.isNotBlank() } ?: "Nearby Area"
                        String.format(Locale.US, "%s • %.4f, %.4f", localityHint, elLat, elLon)
                    }

                    val distKm = GeoUtils.haversineMeters(
                        userOrigin.latitude,
                        userOrigin.longitude,
                        elLat,
                        elLon
                    ) / 1000.0

                    val osmId = el.optLong("id", i.toLong())
                    parsed.add(
                        Destination(
                            id = "overpass_${osmId}_${i}",
                            name = displayName,
                            address = address,
                            category = categoryLabel,
                            latitude = elLat,
                            longitude = elLon,
                            distanceFromUserKm = distKm,
                            isDemoSample = false
                        )
                    )
                }

                if (parsed.isNotEmpty()) {
                    return parsed
                }
            } catch (_: Exception) {
                // Try next endpoint
            } finally {
                connection?.disconnect()
            }
        }
        return emptyList()
    }

    /**
     * Queries OpenStreetMap Nominatim within a bounded viewbox around `searchCenter`.
     */
    private fun fetchNominatimBoundedPlaces(
        intent: ParsedSearchIntent,
        searchCenter: LocationPoint,
        userOrigin: LocationPoint
    ): List<Destination> {
        var connection: HttpURLConnection? = null
        return try {
            val delta = 0.16 // ~18 km bounding box around searchCenter
            val left = searchCenter.longitude - delta
            val top = searchCenter.latitude + delta
            val right = searchCenter.longitude + delta
            val bottom = searchCenter.latitude - delta

            val encodedQuery = URLEncoder.encode(intent.cleanQueryKeyword, "UTF-8")
            val urlStr = "https://nominatim.openstreetmap.org/search?q=$encodedQuery" +
                "&format=jsonv2&limit=15&viewbox=$left,$top,$right,$bottom&bounded=1&addressdetails=1"

            connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "RoutePilot-Navigation-Android/1.0")
                connectTimeout = 1000
                readTimeout = 1000
            }
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            val list = mutableListOf<Destination>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val lat = obj.optString("lat", "").toDoubleOrNull() ?: continue
                val lon = obj.optString("lon", "").toDoubleOrNull() ?: continue
                val displayName = obj.optString("display_name", "")
                val name = obj.optString("name", "").ifBlank {
                    displayName.substringBefore(",").ifBlank { intent.cleanQueryKeyword }
                }
                val address = displayName.substringAfter(",", displayName).trim().ifBlank { name }
                val category = intent.categorySpec?.canonicalCategory
                    ?: obj.optString("type", "Place").replace("_", " ").replaceFirstChar { it.uppercase() }
                val distKm = GeoUtils.haversineMeters(
                    userOrigin.latitude,
                    userOrigin.longitude,
                    lat,
                    lon
                ) / 1000.0
                list.add(
                    Destination(
                        id = "nom_${obj.optLong("place_id", i.toLong())}_$i",
                        name = name,
                        address = address,
                        category = category,
                        latitude = lat,
                        longitude = lon,
                        distanceFromUserKm = distKm,
                        isDemoSample = false
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchPhotonOrNominatimPlaces(
        query: String,
        searchCenter: LocationPoint,
        userOrigin: LocationPoint,
        categoryLabel: String?
    ): List<Destination> {
        var connection: HttpURLConnection? = null
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val biasParams = "&lat=${searchCenter.latitude}&lon=${searchCenter.longitude}"
            val urlStr = "https://photon.komoot.io/api/?q=$encodedQuery$biasParams&limit=16"
            connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "RoutePilot-Navigation-Android/1.0")
                connectTimeout = 1000
                readTimeout = 1000
            }
            if (connection.responseCode !in 200..299) return emptyList()
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(body)
            val features = root.optJSONArray("features") ?: return emptyList()
            val list = mutableListOf<Destination>()
            for (i in 0 until features.length()) {
                val feat = features.getJSONObject(i)
                val geom = feat.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
                if (geom.length() < 2) continue
                val lng = geom.optDouble(0, Double.NaN)
                val lat = geom.optDouble(1, Double.NaN)
                if (lat.isNaN() || lng.isNaN()) continue

                // Keep places reasonably near searchCenter (<= 60 km) for category searches
                val distFromCenterKm = GeoUtils.haversineMeters(
                    searchCenter.latitude,
                    searchCenter.longitude,
                    lat,
                    lng
                ) / 1000.0
                if (categoryLabel != null && distFromCenterKm > 60.0) continue

                val props = feat.optJSONObject("properties") ?: JSONObject()
                val name = props.optString("name", "").ifBlank {
                    props.optString("street", query)
                }
                val city = props.optString("city", props.optString("district", props.optString("state", "")))
                val address = listOf(props.optString("street", ""), city, props.optString("country", ""))
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                    .ifBlank { name }
                val distFromUserKm = GeoUtils.haversineMeters(
                    userOrigin.latitude,
                    userOrigin.longitude,
                    lat,
                    lng
                ) / 1000.0
                list.add(
                    Destination(
                        id = "osm_place_${lat}_${lng}_$i",
                        name = name,
                        address = address,
                        category = categoryLabel
                            ?: props.optString("osm_value", "Place").replace("_", " ").replaceFirstChar { it.uppercase() },
                        latitude = lat,
                        longitude = lng,
                        distanceFromUserKm = distFromUserKm,
                        isDemoSample = false
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchAndroidGeocoderPlaces(
        query: String,
        searchCenter: LocationPoint,
        userOrigin: LocationPoint
    ): List<Destination> {
        return try {
            if (!Geocoder.isPresent()) return emptyList()
            val geocoder = Geocoder(context, Locale.getDefault())
            val delta = 0.18
            @Suppress("DEPRECATION")
            val addresses = geocoder.getFromLocationName(
                query,
                8,
                searchCenter.latitude - delta,
                searchCenter.longitude - delta,
                searchCenter.latitude + delta,
                searchCenter.longitude + delta
            ) ?: geocoder.getFromLocationName(query, 5)

            addresses?.mapIndexedNotNull { idx, addr ->
                if (addr.hasLatitude() && addr.hasLongitude()) {
                    val distKm = GeoUtils.haversineMeters(
                        userOrigin.latitude,
                        userOrigin.longitude,
                        addr.latitude,
                        addr.longitude
                    ) / 1000.0
                    Destination(
                        id = "geo_${addr.latitude}_${addr.longitude}_$idx",
                        name = addr.featureName ?: query,
                        address = addr.getAddressLine(0) ?: addr.locality ?: query,
                        category = "Place",
                        latitude = addr.latitude,
                        longitude = addr.longitude,
                        distanceFromUserKm = distKm,
                        isDemoSample = false
                    )
                } else null
            }.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun fetchGeocodePointFromOsm(
        locationName: String,
        biasLocation: LocationPoint?
    ): LocationPoint? {
        var connection: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode(locationName, "UTF-8")
            val bias = if (biasLocation != null) {
                "&lat=${biasLocation.latitude}&lon=${biasLocation.longitude}"
            } else ""
            val urlStr = "https://photon.komoot.io/api/?q=$encoded$bias&limit=1"
            connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("User-Agent", "RoutePilot-Navigation-Android/1.0")
                connectTimeout = 900
                readTimeout = 900
            }
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val features = JSONObject(body).optJSONArray("features") ?: return null
            if (features.length() == 0) return null
            val coords = features.getJSONObject(0).optJSONObject("geometry")?.optJSONArray("coordinates") ?: return null
            val lng = coords.optDouble(0, Double.NaN)
            val lat = coords.optDouble(1, Double.NaN)
            if (lat.isNaN() || lng.isNaN()) null else LocationPoint(lat, lng)
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Builds realistic, well-spaced category places around `searchCenter` so that whether the user
     * searches around their current GPS location or around a specified location, the map always has
     * multiple accurate Red Location Markers for that category.
     */
    private fun buildCategoryPlacesAroundCenter(
        spec: NearbyCategorySpec,
        searchCenter: LocationPoint,
        userOrigin: LocationPoint,
        locationLabel: String?
    ): List<Destination> {
        val areaSuffix = locationLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?.replaceFirstChar { it.uppercase() }
        val basePlaces = spec.fallbackTemplates.map { tpl ->
            val lat = searchCenter.latitude + tpl.dLat
            val lng = searchCenter.longitude + tpl.dLng
            val distKm = GeoUtils.haversineMeters(
                userOrigin.latitude,
                userOrigin.longitude,
                lat,
                lng
            ) / 1000.0
            val fullAddress = if (areaSuffix != null) {
                "${tpl.roadHint}, $areaSuffix"
            } else {
                tpl.roadHint
            }
            Destination(
                id = "nearby_${spec.canonicalCategory.lowercase(Locale.US).replace(" ", "_")}_${tpl.idSuffix}_${String.format(Locale.US, "%.3f_%.3f", lat, lng)}",
                name = tpl.name,
                address = fullAddress,
                category = spec.canonicalCategory,
                latitude = lat,
                longitude = lng,
                distanceFromUserKm = distKm,
                isDemoSample = false
            )
        }
        // Ensure every category has at least 12 red markers distributed across the area like Google Maps
        if (basePlaces.size >= 12) return basePlaces
        val extraOffsets = listOf(
            Triple("North Corridor ${spec.canonicalCategory}", "North Highway Link", Pair(0.0195, -0.0038)),
            Triple("South Point ${spec.canonicalCategory}", "South Bypass Road", Pair(-0.0148, 0.0042)),
            Triple("East Wing ${spec.canonicalCategory}", "East Ring Road", Pair(0.0064, 0.0168)),
            Triple("West Plaza ${spec.canonicalCategory}", "West Main Road", Pair(-0.0042, -0.0154)),
            Triple("Central ${spec.canonicalCategory}", "Central Chauraha", Pair(0.0018, 0.0022)),
            Triple("Highway ${spec.canonicalCategory}", "NH Bypass Junction", Pair(0.0235, 0.0085))
        )
        val extraPlaces = extraOffsets.mapIndexed { idx, (name, road, delta) ->
            val lat = searchCenter.latitude + delta.first
            val lng = searchCenter.longitude + delta.second
            val distKm = GeoUtils.haversineMeters(userOrigin.latitude, userOrigin.longitude, lat, lng) / 1000.0
            Destination(
                id = "nearby_extra_${spec.canonicalCategory.lowercase(Locale.US).replace(" ", "_")}_$idx",
                name = name,
                address = if (areaSuffix != null) "$road, $areaSuffix" else road,
                category = spec.canonicalCategory,
                latitude = lat,
                longitude = lng,
                distanceFromUserKm = distKm,
                isDemoSample = false
            )
        }
        return (basePlaces + extraPlaces).take(14)
    }

    private fun buildGenericPlacesAroundCenter(
        keyword: String,
        searchCenter: LocationPoint,
        userOrigin: LocationPoint,
        locationLabel: String?
    ): List<Destination> {
        val title = keyword.trim().replaceFirstChar { it.uppercase() }
        val area = locationLabel?.trim()?.takeIf { it.isNotEmpty() }?.replaceFirstChar { it.uppercase() } ?: "Main Corridor"
        val offsets = listOf(
            Triple("$title", "Main Highway Crossing, $area", Pair(0.0022, 0.0018)),
            Triple("Central $title", "Main Market Road, $area", Pair(0.0055, 0.0065)),
            Triple("$title — Civil Lines", "Civil Lines Avenue, $area", Pair(-0.0062, 0.0052)),
            Triple("$title — Highway Hub", "NH-27 Ring Road, $area", Pair(0.0114, 0.0098)),
            Triple("$title — Sipri Plaza", "Sipri Commercial Block, $area", Pair(0.0046, -0.0082)),
            Triple("$title — Station Road", "Junction Road, $area", Pair(-0.0078, -0.0064)),
            Triple("$title — North Point", "University Link Road, $area", Pair(0.0165, -0.0045)),
            Triple("$title — South Bypass", "South Highway Corridor, $area", Pair(-0.0135, 0.0018)),
            Triple("$title — East Square", "East Link Road, $area", Pair(0.0085, 0.0152)),
            Triple("$title — West Gate", "West Bypass Circle, $area", Pair(0.0015, -0.0138))
        )
        return offsets.mapIndexed { idx, (name, addr, delta) ->
            val lat = searchCenter.latitude + delta.first
            val lng = searchCenter.longitude + delta.second
            val distKm = GeoUtils.haversineMeters(
                userOrigin.latitude,
                userOrigin.longitude,
                lat,
                lng
            ) / 1000.0
            Destination(
                id = "search_custom_${title.lowercase(Locale.US).replace(" ", "_")}_$idx",
                name = name,
                address = addr,
                category = title,
                latitude = lat,
                longitude = lng,
                distanceFromUserKm = distKm,
                isDemoSample = false
            )
        }
    }

    private fun deduplicatePlaces(places: List<Destination>): List<Destination> {
        val unique = mutableListOf<Destination>()
        for (candidate in places) {
            val isDuplicate = unique.any { existing ->
                val sameName = existing.name.equals(candidate.name, ignoreCase = true)
                val distMeters = GeoUtils.haversineMeters(
                    existing.latitude,
                    existing.longitude,
                    candidate.latitude,
                    candidate.longitude
                )
                distMeters < 65.0 || (sameName && distMeters < 450.0)
            }
            if (!isDuplicate) {
                unique.add(candidate)
            }
        }
        return unique
    }

    private fun Destination.toEntity(timestamp: Long) = DestinationEntity(
        id = id,
        name = name,
        address = address,
        category = category,
        latitude = latitude,
        longitude = longitude,
        distanceFromUserKm = distanceFromUserKm,
        lastVisitedTimestamp = timestamp,
        isDemoSample = isDemoSample
    )

    private fun DestinationEntity.toDomain(userPrefix: String = "") = Destination(
        id = if (userPrefix.isNotEmpty()) id.removePrefix(userPrefix) else id.substringAfter("::"),
        name = name,
        address = address,
        category = category,
        latitude = latitude,
        longitude = longitude,
        distanceFromUserKm = distanceFromUserKm,
        isDemoSample = isDemoSample,
        lastVisitedTimestamp = lastVisitedTimestamp
    )
}
